# benchmarks

포트폴리오에 적은 OCR·검색·TTS 수치를 다시 잴 수 있는 스크립트다. 결과 해석은 `project/Docs/elseDocs/`의 실측 문서에 있다.

| 폴더 | 재는 것 | 결과 문서 |
|---|---|---|
| `ocr_cer/` | Marker, MinerU, 품질 게이트, Kanana 교정의 글자 오류율(CER) | [OCR_CER_실측.md](../Docs/elseDocs/OCR_CER_실측.md) |
| `qdrant_retrieval/` | 적재 전 정규화와 Dense+Sparse RRF 검색의 Top-k 적중률, MRR | [Qdrant_검색_정규화_실측.md](../Docs/elseDocs/Qdrant_검색_정규화_실측.md) |
| `tts_similarity/` | 데모 사이트 합성 음성과 원본 음성의 화자 유사도(ECAPA-TDNN 코사인) | [TTS_화자유사도_실측.md](../Docs/elseDocs/TTS_화자유사도_실측.md) |
| `modal_apps_local/` | Whisper V3 Turbo, BGE-M3·reranker Modal 앱의 추론 코드를 로컬 MPS에서 실행 | [Modal_앱_로컬_MPS_검증.md](../Docs/05_실측/Modal_앱_로컬_MPS_검증.md) |

## 실행

```bash
cd project/benchmarks/ocr_cer && ./run.sh
cd project/benchmarks/qdrant_retrieval && ./run.sh
cd project/benchmarks/modal_apps_local && ./run.sh
cd project/benchmarks/tts_similarity && ./run.sh
```

- 필요한 것: uv, Google Chrome(테스트 PDF 렌더링), Pretendard 글꼴. Kanana 교정 단계는 Apple Silicon(MLX)에서 돈다.
- 가상환경, 모델 캐시, 테스트 PDF는 각 폴더의 `work/`에 만들어지고 git에는 올라가지 않는다. 모델과 패키지를 합쳐 약 10GB를 내려받는다.
- 품질 게이트, 후처리, 청커는 `backend-fastapi/app/modules/OCR_v1`의 서비스 코드를 그대로 불러 쓴다(`ocr_v1_loader.py`).
- 테스트 교재의 본문은 인자로 준 md 폴더에서 뽑는다. 기본은 `project/Docs`다. 2026-11-02 측정에는 공개하지 않는 내부 문서를 썼기 때문에, 기본값으로 돌리면 수치가 조금 다를 수 있다.
