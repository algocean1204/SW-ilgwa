# OCR v1 파이프라인 모듈

교재 PDF를 LangGraph StateGraph 8단계(분류 → 추출 → 교정 → 품질 게이트 → 폴백 → 후처리 → 청킹 → 임베딩·저장)로 처리하고,
RAG 하이브리드 검색(Dense+Sparse+Reranker)을 제공한다.

---

## 모듈 개요

| 항목 | 내용 |
|---|---|
| 주요 기능 | PDF OCR 인제스트 + Qdrant RAG 하이브리드 검색 |
| 파이프라인 | LangGraph `StateGraph` 8단계 (노드 8개, `pipeline/graph.py`) |
| 기본 OCR 엔진 | Marker. 품질 게이트에 걸린 페이지만 MinerU 재추출 후 Kanana-2 교정 (실패 시 Claude) |
| 임베딩 모델 | `BAAI/bge-m3` (Dense+Sparse 동시 생성) |
| 리랭커 | `BAAI/bge-reranker-v2-m3` |
| 임베딩·리랭커 실행 위치 | 기본 Modal GPU(L4) 앱 `ocr-bge-m3`. `OCR_V1_EMBED_BACKEND=local`이면 FastAPI 프로세스 안에서 돌린다 |
| 벡터 저장소 | Qdrant (로컬 또는 원격 서버) |
| 서버 포트 | 8005 (환경변수 `OCR_V1_PORT` 로 변경) |

---

## 아키텍처

```
PDF 입력
  │
  ▼
Stage 1: 페이지 타입 분류 (born-digital / scanned / mixed)
  │
  ▼
Stage 2: OCR 추출 (Marker 엔진)
  │
  ▼
Stage 3: llm_correct (기본 비활성, 그대로 통과. OCR_V1_MARKER_USE_LLM_MODEL 설정 시에만 동작)
  │
  ▼
Stage 4: 품질 게이트 ─── 실패 페이지 있음 ──▶ Stage 5: 실패 페이지만 MinerU 재추출 → Kanana-2 교정 (실패 시 Claude)
  │ (전 페이지 통과)                                        │
  └────────────────────────────────────────────────────────┘
  │
  ▼
Stage 6: 후처리 (헤더/푸터 제거, 하이픈 복원, 단락 병합, 표/수식 정규화)
  │
  ▼
Stage 7: 섹션 기반 청킹 (tiktoken cl100k_base, 600~1200 토큰)
  │
  ▼
Stage 8: BGE-M3 임베딩 (Dense + Sparse, 기본 Modal GPU) + Qdrant 업서트 (embed_store 노드 하나)
```

품질 게이트 분기(`route_after_quality`):

| 결과 | 조건 | 다음 노드 |
|---|---|---|
| `pass` | 전 페이지 통과 | postprocess |
| `partial` | 일부 페이지 실패 | fallback → postprocess |
| `fail_all` | 전 페이지 실패(스캔본 등) | fallback → postprocess |

fallback 노드는 게이트에 걸린 페이지만 MinerU로 다시 뽑고, 그 페이지만 ChapterStudio의 Kanana-2 교정 커넥터(`get_polish_connector().polish`)로 고친다.
게이트를 통과한 페이지는 교정하지 않는다. Kanana-2를 전 페이지에 적용하면 원문에 없는 문장이 붙어 CER이 0.437까지 올랐다.
Kanana-2 교정이 실패하면(예외, 타임아웃, 빈 응답, 원문 그대로 반환) 같은 지시문으로 ChapterStudio의 Claude Sonnet 커넥터(`get_connector("claude_sonnet")`)가 그 페이지를 교정한다.
Kanana-2 커넥터는 실패해도 원문을 돌려주므로 원문과 같은 결과도 실패로 본다. Claude도 실패하거나 키가 없으면 MinerU 결과를 그대로 쓴다.

임베딩과 리랭커는 `registry.get_embedder()`, `get_reranker()`가 `OCR_V1_EMBED_BACKEND`를 보고 고른다.
`modal`(기본)은 `deploy/modal_embed_app.py`의 `BGEM3Server`를 `modal.Cls.from_name`으로 부르고, `local`은 FlagEmbedding을 이 프로세스에 올린다.
두 쪽 모두 반환 모양이 같다. Modal 앱 배포:

```bash
modal run app/modules/OCR_v1/deploy/modal_embed_app.py::download_model   # 가중치를 Volume에 한 번 받아 둔다
modal deploy app/modules/OCR_v1/deploy/modal_embed_app.py
```

---

## IN/OUT 인터페이스

### `OCRv1Pipeline.ingest()`

```python
async def ingest(
    self,
    pdf_bytes: bytes,         # 원본 PDF 이진 데이터
    filename: str,            # 원본 파일명 (컬렉션명 자동 생성에 사용)
    collection_name: str = "" # Qdrant 컬렉션명 (빈 값이면 파일명 기반 자동 생성)
) -> dict:                    # OCRPipelineState 와 동일한 구조의 딕셔너리
```

반환 딕셔너리 주요 키:

| 키 | 타입 | 설명 |
|---|---|---|
| `pipeline_status` | `str` | `"done"` \| `"error"` |
| `total_pages` | `int` | PDF 전체 페이지 수 |
| `chunks` | `list[ChunkRecord]` | 생성된 청크 목록 |
| `embedded_count` | `int` | 실제 임베딩·저장된 청크 수 |
| `collection_name` | `str` | 저장된 Qdrant 컬렉션명 |
| `quality_scores` | `list[QualityScore]` | 페이지별 품질 점수 |
| `timings` | `dict[str, float]` | Stage별 소요 시간(초) |
| `error_message` | `str \| None` | 오류 발생 시 메시지 |

### `OCRv1Pipeline.search()`

```python
async def search(
    self,
    query: str,              # 검색 쿼리 문자열
    collection_name: str,    # 검색 대상 Qdrant 컬렉션명
    top_k: int = 5           # 반환할 최상위 결과 수 (1~50)
) -> list[dict]:             # SearchHit 딕셔너리 목록
```

반환 딕셔너리 항목 키:

| 키 | 타입 | 설명 |
|---|---|---|
| `chunk_id` | `str` | 청크 고유 식별자 |
| `text` | `str` | 청크 본문 텍스트 |
| `score` | `float` | 리랭킹 후 관련도 점수 |
| `section_title` | `str` | 청크가 속한 섹션 제목 |
| `page_nums` | `list[int]` | 원본 페이지 번호 목록 |

---

## API 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/ocr/v1/ingest` | PDF 인제스트 (multipart/form-data) |
| `POST` | `/api/ocr/v1/search` | RAG 하이브리드 검색 (JSON) |
| `GET` | `/health` | 헬스체크 |
| `GET` | `/` | 테스트 HTML 페이지로 리다이렉트 |
| `GET` | `/static/ocr_v1.html` | 한국어 테스트 페이지 |

---

## 환경변수

| 변수명 | 기본값 | 설명 |
|---|---|---|
| `OCR_V1_PORT` | `8005` | FastAPI 서버 바인딩 포트 |
| `OCR_V1_PRIMARY_ENGINE` | `marker` | 기본 OCR 엔진 |
| `OCR_V1_FALLBACK_ENGINE` | `mineru` | 품질 실패 시 폴백 엔진 |
| `OCR_V1_MARKER_TIMEOUT_SEC` | `300` | Marker 엔진 호출 타임아웃(초) |
| `OCR_V1_MARKER_BATCH_MULTIPLIER` | `2` | Marker 배치 크기 배율 |
| `OCR_V1_MARKER_USE_LLM_MODEL` | `""` | Stage 3 Marker 재추출 교정 모델명 (기본 비활성) |
| `OCR_V1_FALLBACK_KANANA_POLISH` | `true` | MinerU로 다시 뽑은 실패 페이지에 Kanana-2 교정 적용. 끄면 Claude 교정도 하지 않는다 |
| `OCR_V1_FALLBACK_CLAUDE_POLISH` | `true` | Kanana-2 교정이 실패한 페이지를 Claude로 교정 |
| `OCR_V1_QUALITY_PASS_THRESHOLD` | `0.7` | 품질 통과 최소 점수 |
| `OCR_V1_CHAR_CORRUPTION_MAX` | `0.05` | 최대 허용 문자 오염률 |
| `OCR_V1_TABLE_INTEGRITY_MIN` | `0.8` | 표 무결성 최소 점수 |
| `OCR_V1_FORMULA_PRESERVATION_MIN` | `0.9` | 수식 보존 최소 점수 |
| `OCR_V1_READING_ORDER_MIN` | `0.85` | 읽기 순서 최소 점수 |
| `OCR_V1_CHUNK_MIN_TOKENS` | `600` | 섹션 청크 최소 토큰 수 |
| `OCR_V1_CHUNK_MAX_TOKENS` | `1200` | 섹션 청크 최대 토큰 수 |
| `OCR_V1_EMBED_BACKEND` | `modal` | 임베딩·리랭커 실행 위치. `modal` 또는 `local` |
| `OCR_V1_MODAL_EMBED_APP_NAME` | `ocr-bge-m3` | 임베딩·리랭커 Modal 앱 이름 |
| `OCR_V1_MODAL_EMBED_GPU` | `L4` | Modal 앱 GPU (배포할 때만 읽는다) |
| `OCR_V1_MODAL_EMBED_SCALEDOWN_SEC` | `30` | Modal 컨테이너 유휴 회수 시간 (배포할 때만 읽는다) |
| `OCR_V1_EMBEDDING_MODEL` | `BAAI/bge-m3` | Dense+Sparse 임베딩 모델 |
| `OCR_V1_EMBEDDING_BATCH_SIZE` | `32` | 임베딩 배치 크기 |
| `OCR_V1_QDRANT_URL` | `http://localhost:6333` | Qdrant 서버 URL |
| `OCR_V1_QDRANT_COLLECTION` | `ocr_v1_chunks` | 기본 Qdrant 컬렉션명 |
| `OCR_V1_RERANKER_MODEL` | `BAAI/bge-reranker-v2-m3` | 크로스인코더 리랭킹 모델 |
| `OCR_V1_RERANK_TOP_K` | `10` | 리랭킹 전 초기 검색 결과 수 |
| `OCR_V1_MAX_QUALITY_RETRIES` | `1` | 폴백 재시도 횟수 설정값. 현재 그래프는 읽지 않고 폴백을 1회만 한다 |

---

## 의존성

```
marker-pdf          # Marker OCR 엔진 (born-digital 최적화)
magic-pdf           # MinerU OCR 엔진 (scanned 폴백)
FlagEmbedding       # BGE-M3 Dense+Sparse 임베딩 (local 백엔드에서만 필요. Modal 이미지에는 따로 설치)
modal               # Modal 임베딩·리랭커 호출 (modal 백엔드)
qdrant-client       # Qdrant 벡터 저장소 클라이언트
tiktoken            # cl100k_base 토크나이저 (청킹용)
langgraph           # StateGraph 파이프라인
fastapi             # REST API 프레임워크
uvicorn             # ASGI 서버
python-multipart    # multipart/form-data 파일 업로드 지원
```

---

## 사용 예시

### Python 직접 호출

```python
import asyncio
from app.modules.OCR_v1.connector import OCRv1Pipeline

async def main():
    pipeline = OCRv1Pipeline()

    # PDF 인제스트
    with open("document.pdf", "rb") as f:
        pdf_bytes = f.read()

    state = await pipeline.ingest(pdf_bytes, "document.pdf", "my_collection")
    print(f"상태: {state['pipeline_status']}, 청크 수: {len(state['chunks'])}")

    # RAG 검색
    hits = await pipeline.search("검색 쿼리", "my_collection", top_k=5)
    for hit in hits:
        print(f"[{hit['score']:.4f}] {hit['section_title']}: {hit['text'][:100]}")

asyncio.run(main())
```

### curl 예시

```bash
# PDF 인제스트
curl -X POST http://localhost:8005/api/ocr/v1/ingest \
  -F "file=@document.pdf" \
  -F "collection_name=my_collection"

# RAG 검색
curl -X POST http://localhost:8005/api/ocr/v1/search \
  -H "Content-Type: application/json" \
  -d '{"query": "검색 쿼리", "collection_name": "my_collection", "top_k": 5}'

# 헬스체크
curl http://localhost:8005/health
```

### 서버 실행 (샌드박스)

```bash
# uv venv 환경에서 실행
uv venv
uv pip install -r requirements.txt
uv run uvicorn OCR_v1.app.main:app --port 8005
```

---

## 폴백·오류 정책

| 상황 | 처리 방식 |
|---|---|
| 품질 게이트 실패 | 실패 페이지만 MinerU로 재추출 후 Kanana-2 교정 (1회) |
| Kanana-2 교정 실패 | 같은 지시문으로 Claude 교정. Claude도 실패하면 MinerU 결과 유지 |
| Modal 임베딩 호출 실패 | embed_store 노드가 `pipeline_status="error"`로 기록. 로컬로 바꾸려면 `OCR_V1_EMBED_BACKEND=local` |
| 폴백 후에도 품질 미달 | 데이터를 잃지 않도록 낮은 점수 그대로 청킹·저장 |
| 파이프라인 전체 예외 | `pipeline_status="error"`, `error_message` 에 원인 기록, HTTP 200 반환 |
| PDF 형식 오류 | HTTP 400 + 상세 메시지 반환 |

---

## backend-fastapi 연결

1. 라우터는 `backend-fastapi/main.py`가 `include_router`로 `/api/ocr/v1` 접두에 등록한다.
2. `.env`에 위 환경변수 목록을 넣는다.
3. 다른 모듈은 `OCR_v1/connector.py`의 `OCRv1Pipeline`을 import해 쓴다(예: `Agent_orchestrator/adapters.py`).
4. 운영에서는 Qdrant 서버 URL(`OCR_V1_QDRANT_URL`)을 실제 서버로 맞춘다.
