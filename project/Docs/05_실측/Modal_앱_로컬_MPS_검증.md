# Modal 앱 로컬 MPS 검증

ASR_V1의 Whisper V3 Turbo 앱(`asr-whisper-v3-turbo`)과 OCR_v1의 BGE-M3·reranker 앱(`ocr-bge-m3`)이 쓰는 추론 코드를 Apple Silicon MPS에서 돌려 확인한 기록이다. Modal 배포 자체는 이 기록에 들어 있지 않다.

## 측정 환경

| 항목 | 값 |
|---|---|
| 날짜 | 2026-11-03 |
| 기기 | Apple M4 Pro, macOS, PyTorch MPS |
| 패키지 | torch 2.7.1, transformers 4.51.3, FlagEmbedding 1.3.5 |
| 모델 | openai/whisper-large-v3-turbo, BAAI/bge-m3, BAAI/bge-reranker-v2-m3 |
| 스크립트 | `project/benchmarks/modal_apps_local/run.sh` |

## 방법

1. Whisper: Modal 앱과 같은 설정을 쓴다. fp16 가중치, transformers ASR 파이프라인, 16kHz 디코딩, 30초 청크, batch 8, `language="ko"`.
2. 오디오: macOS `say`(Yuna)로 만든 짧은 문장(3.7초)과 강의 문단(37.3초). 강의 문단은 30초를 넘어 청크가 두 개로 나뉜다.
3. BGE-M3·reranker: Modal 앱처럼 fp16으로 MPS에서 돌린 결과를 CPU fp32 결과와 비교한다. 한국어 문장 3개를 임베딩하고, 질의 1개로 3개를 재정렬한다.

## 결과

| 항목 | 결과 |
|---|---|
| Whisper 모델 적재 | 67.0초 (처음 한 번, 가중치 내려받기 포함) |
| 짧은 문장 3.7초 | 6.1초. "안녕하세요 오늘은 분수의 더샘을 배워 보겠습니다." |
| 강의 문단 37.3초 | 5.2초. 문장 전체를 전사했다. 숫자는 아라비아 숫자로 바뀌었다(오분의 일 → 5분의 1). |
| 전사 오류 | "덧셈"을 "더샘/더셈", "공배수"를 "공폐수", "둘을"을 "2를"로 적었다. 합성 음성 기준이다. |
| BGE-M3 dense | 1024차원, MPS fp16과 CPU fp32의 cosine이 세 문장 모두 1.0 |
| BGE-M3 sparse | 세 문장 모두 토큰 집합이 같다 |
| reranker | MPS [0.6364, 0.0, 0.0], CPU [0.6376, 0.0, 0.0]. 1위 문장이 같다 |

짧은 문장의 6.1초는 첫 호출의 준비 시간이 섞인 값이다. 두 번째 호출인 강의 문단은 37.3초 음성을 5.2초에 처리했다.

## 해석

- Whisper 앱의 설정(fp16, 30초 청크)으로 30초가 넘는 강의 음성도 끊기지 않고 전사된다.
- 임베딩과 재정렬은 fp16으로 돌려도 fp32와 결과가 사실상 같다. Modal GPU에서 fp16으로 돌리는 설정을 그대로 둔다.
- Modal 위에서의 콜드 스타트와 지연 시간은 이 기록으로 알 수 없다.

원본 결과: `project/benchmarks/modal_apps_local/results/result_mps.json`
