# 교재 RAG 파이프라인

전공 PDF 교재를 검색 가능한 문단으로 바꿔 Qdrant에 넣고, 강의 생성 때 필요한 문단만 찾아 프롬프트에 넣는 흐름이다. 코드는 `backend-fastapi/app/modules/OCR_v1/`에 있다. 단계별 수치는 아래 두 실측 문서에 있다.

- [OCR_CER_실측.md](OCR_CER_실측.md): 추출 단계의 글자 오류율
- [Qdrant_검색_정규화_실측.md](Qdrant_검색_정규화_실측.md): 정규화와 하이브리드 검색의 적중률

## 1. 왜 RAG인가

슬라이드 하나를 만들 때 교재를 통째로 넣으면 입력이 길어지고 비용이 커진다. 그래서 교재를 600~1,200 토큰 단위로 나눠 저장하고, 슬라이드 주제에 맞는 문단만 검색해 넣는다. 의미 검색(Dense)만 쓰면 전공 용어를 놓쳐 키워드 검색(Sparse)을 함께 쓴다.

## 2. 적재 흐름 (LangGraph)

```
classify_pdf → extract_base(Marker) → llm_correct → quality_gate ─ 통과 ─────────────┐
                                                               └ 실패 → fallback(MinerU + Kanana-2) ┤
                                                                                     postprocess → chunk_node → embed_store
```

| 노드 | 하는 일 | 주요 설정 (OCR_v1/config.py) |
|---|---|---|
| classify_pdf | 페이지별로 텍스트 레이어가 있는지, 스캔인지 나눈다 | |
| extract_base | Marker로 마크다운을 뽑는다 | `OCR_V1_PRIMARY_ENGINE` |
| llm_correct | 표·수식이 있는 페이지를 Marker LLM 옵션으로 다시 뽑는다. 기본은 꺼져 있다 | `OCR_V1_MARKER_USE_LLM_MODEL` |
| quality_gate | 문자 오염 30%, 읽기 순서 25%, 표 무결성 25%, 수식 보존 20%를 가중 평균한다 | 통과 기준 `OCR_V1_QUALITY_PASS_THRESHOLD`=0.7 |
| fallback | 게이트에 걸린 페이지만 MinerU로 다시 뽑고, 그 결과에만 Kanana-2(Modal) 교정을 건다. Kanana-2가 실패하면 Claude로 교정하고, 그것도 실패하면 MinerU 텍스트를 그대로 둔다. 모든 페이지가 실패한 문서도 여기로 온다 | `OCR_V1_FALLBACK_ENGINE` |
| postprocess | 쪽 번호와 반복 머리글 제거 → 하이픈 복원 → 끊긴 문단 병합 → 표 열 맞춤 → 수식 표기 통일 | 페이지 단위 |
| chunk_node | 페이지를 합친 뒤 제목 단위로 자르고, 600 토큰 미만은 다음 절과 합치고 1,200 토큰을 넘으면 문단에서 나눈다 | `OCR_V1_CHUNK_MIN_TOKENS`, `OCR_V1_CHUNK_MAX_TOKENS` |
| embed_store | BGE-M3로 Dense(1024차원)와 Sparse 벡터를 만들어 Qdrant에 저장한다. BGE-M3는 Modal GPU 앱 `ocr-bge-m3`에서 돈다 | Dense COSINE, Sparse 기본. `OCR_V1_EMBED_BACKEND`=modal(기본) / local |

통과한 페이지에 교정을 걸지 않는 이유는 실측에 있다. 교정 모델을 모든 페이지에 걸면 원문에 없는 설명 문장이 붙어 CER이 0.003에서 0.437로 올랐다.

## 3. 검색 흐름

1. 질의를 BGE-M3로 Dense, Sparse 벡터로 바꾼다.
2. Qdrant에서 Dense와 Sparse를 각각 검색한다. 리랭크 후보 수(`OCR_V1_RERANK_TOP_K`, 기본 10)의 2배씩 가져온다.
3. 두 결과를 RRF(k=60)로 합친다. 문서마다 `1/(60+순위)`를 더해 정렬한다.
4. bge-reranker-v2-m3로 다시 정렬해 상위 top_k를 돌려준다. 리랭커도 같은 Modal 앱에서 돈다.

`OCR_V1_EMBED_BACKEND=local`이면 BGE-M3와 리랭커를 FastAPI 프로세스 안에서 돌린다. project/benchmarks의 재현 측정은 이 모드를 쓴다.

강의 생성(ChapterStudio_V1)에서는 `pdf_context_injector`가 교재 컬렉션에서 문단을 찾아 프롬프트 문맥으로 넣는다. 요청은 top_k=20이지만 리랭크 후보가 10개라 실제로는 최대 10개가 들어간다.

## 4. 알려진 한계

- 품질 게이트는 구조와 문자 오염만 본다. 글자 오류율이 높은 저화질 스캔 페이지(CER 0.14)도 통과했다.
- 머리글 제거가 페이지 단위라 쪽마다 한 번 나오는 교재 머리글은 남는다. Marker 경로에서는 Marker가 머리글을 먼저 빼서 영향이 없다.
- 쪽을 넘어 이어지는 문단은 이어 붙이지 않는다.
