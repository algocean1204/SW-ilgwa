# ASR V1: 실시간 대화형 ASR 파이프라인

## 목적

기존 배치 ASR(`/api/asr` on port 8001)을 확장해 WebSocket 기반 실시간 스트리밍 전사를 지원한다.
Silero VAD로 음성 구간을 감지하고, 침묵 감지 시 기존 `ASRConnector.generate()`를 배치 호출한다(pseudo-streaming).
전사는 기본으로 Modal GPU(L4)에 올린 Whisper large-v3-turbo(`whisper-modal`)가 맡는다.
LLM→TTS 파이프라인에서 완결 문장을 실시간으로 공급하는 것이 최종 목표다.

## 포트

8011 (TTS_V2: 8010, 허브 서브앱: 8001–8004)

## 실행 방법

```bash
# backend-fastapi/ 디렉토리에서 실행
cd /path/to/backend-fastapi
uv run uvicorn app.modules.ASR_V1.app.main:app --port 8011
```

브라우저 테스트 UI: `http://localhost:8011/static/asr_v1_test.html`

## 의존성

기존 venv에 모두 들어 있어 추가 설치는 필요 없다.

| 패키지 | 용도 |
|---|---|
| `torch` | Silero VAD (clearvoice 의존성으로 설치됨) |
| `fastapi[websockets]` | WebSocket 서버 |
| `numpy` | PCM 배열 처리 |
| `librosa`, `scipy` | 오디오 포맷 변환 (common.audio_io) |

Silero VAD 모델(~2MB)은 `torch.hub.load` 최초 실행 시 `~/.cache/torch/hub/`에 자동 캐시된다.

## Modal Whisper 배포

전사 서버는 `deploy/modal_whisper_app.py`다. 가중치는 Modal Volume(`<앱이름>-hf-cache`)에 미리 받아 두고,
CPU 메모리 스냅샷으로 콜드 스타트를 줄인다. 요청이 끝나고 30초 뒤 컨테이너를 내린다.

```bash
# backend-fastapi/ 에서 실행
modal run app/modules/ASR_V1/deploy/modal_whisper_app.py::download_model   # 처음 한 번
modal deploy app/modules/ASR_V1/deploy/modal_whisper_app.py
modal app stop asr-whisper-v3-turbo                                         # 내릴 때
```

원격 메서드는 `WhisperServer.transcribe(audio_bytes, language="ko")`이고 `{"text", "duration_sec", ...}`를 돌려준다.
커넥터는 `ai_connectors/asr/whisper_modal_connector.py`다.

`ASR_GEMINI_FALLBACK=true`면 Whisper 호출이 실패할 때 Gemini ASR로 한 번 더 전사한다(기본 false).

## WebSocket 프로토콜

엔드포인트: `ws://localhost:8011/ws/asr-v1`

### 업스트림 (브라우저 → 서버)

| 타입 | 내용 |
|---|---|
| 바이너리 | PCM Int16LE 16kHz mono 샘플 청크 |
| 텍스트 JSON | `{ "action": "stop" }`: 세션 종료 |

### 다운스트림 (서버 → 브라우저)

| type | 설명 | 예시 |
|---|---|---|
| `session_ack` | 세션 시작/종료 응답 | `{"type":"session_ack","session_id":"...","action":"started"}` |
| `vad_state` | VAD 상태 전환 | `{"type":"vad_state","speaking":true}` |
| `partial` | 부분 전사 (UI 전용) | `{"type":"partial","text":"안녕..."}` |
| `final` | 확정 전사 | `{"type":"final","text":"안녕하세요.","turn_id":"...","duration_sec":1.2,"latency_ms":350}` |
| `flush` | LLM→TTS 전달 세그먼트 | `{"type":"flush","segments":["안녕하세요."]}` |
| `error` | 오류 알림 | `{"type":"error","detail":"추론 실패"}` |

## REST 엔드포인트

### POST /api/asr-v1/offline

파일 업로드 방식의 배치 전사.

#### 요청

`multipart/form-data`의 `audio` 파일, 쿼리 파라미터 `language`(기본 `ko`)와 `model`(ASR 커넥터 키, 기본 `whisper-modal`)

#### 응답 (`ASRResponse`)

```json
{
  "text": "전사된 텍스트",
  "language": "ko",
  "duration_sec": 2.3,
  "latency_ms": 420.0,
  "model": "whisper-modal"
}
```

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `ASR_V1_VAD_MIN_SILENCE_MS` | `800` | 침묵 판정 최소 시간(ms) |
| `ASR_V1_VAD_THRESHOLD` | `0.5` | VAD 음성 감지 임계값 |
| `ASR_V1_VAD_SPEECH_PAD_MS` | `200` | 음성 경계 패딩(ms) |
| `ASR_V1_VAD_MIN_SPEECH_MS` | `250` | 최소 음성 길이(ms) |
| `ASR_V1_TURN_MIN_CHARS` | `2` | 커밋 최소 글자수 |
| `ASR_V1_TURN_GRACE_MS` | `300` | 마지막 음성 후 추가 대기(ms) |
| `ASR_V1_PARTIAL_INTERVAL_MS` | `500` | 부분 전사 주기(ms) |
| `ASR_V1_BUFFER_MAX_SEC` | `20.0` | 버퍼 최대 누적 시간(초) |
| `ASR_V1_MODEL` | `whisper-modal` | ASR 커넥터 레지스트리 키 |
| `ASR_GEMINI_FALLBACK` | `false` | true면 Whisper 실패 때 Gemini ASR로 넘긴다 |
| `ASR_WHISPER_MODAL_APP_NAME` | `asr-whisper-v3-turbo` | Modal 앱 이름(배포와 커넥터가 같이 쓴다) |
| `ASR_WHISPER_MODAL_GPU` | `L4` | 배포 GPU 종류 |
| `ASR_WHISPER_MODAL_SCALEDOWN_SEC` | `30` | 유휴 컨테이너 회수 시간(초) |
| `ASR_WHISPER_MODAL_TIMEOUT_SEC` | `300` | 커넥터 호출 타임아웃(초) |
| `ASR_V1_PORT` | `8011` | 서버 포트 |

## 기존 ASR(port 8001)과의 관계

| | 기존 ASR | ASR V1 |
|---|---|---|
| 포트 | 8001 | 8011 |
| 방식 | HTTP REST 파일 업로드 | WebSocket 스트리밍 |
| VAD | 없음 | Silero VAD |
| 용도 | 단일 파일 전사 | 실시간 대화형 전사 |
| 커넥터 | ASRConnector 직접 사용 | 동일 커넥터 재사용 (배치 호출) |

## 단위 테스트

```bash
cd backend-fastapi
uv run pytest app/modules/ASR_V1/tests/ -v
```

## 모듈 구조

```
app/modules/ASR_V1/
├── pipeline/
│   ├── config.py         # 전체 파라미터 (환경변수 override)
│   ├── vad.py            # Silero VAD 싱글톤 래퍼
│   ├── audio_buffer.py   # 스트리밍 음성 누적 버퍼
│   ├── audio_processor.py # PCM 디코딩, 프레임 단위 VAD 판정
│   ├── commit_handler.py # 턴 커밋 시 전사와 플러시 정책 적용
│   ├── turn_manager.py   # 턴 커밋 정책
│   ├── flush_policy.py   # LLM→TTS 플러시 정책 (순수 함수)
│   └── session.py        # WS 세션 상태 머신
├── connectors/
│   └── asr_engine.py     # numpy→ASRRequest 변환 엔진
├── deploy/
│   └── modal_whisper_app.py # Modal GPU Whisper large-v3-turbo 서버
├── schemas/
│   ├── ws_messages.py    # WebSocket 메시지 Pydantic 모델
│   └── config.py         # 세션 설정 스키마
├── app/
│   ├── main.py           # FastAPI 앱 (port 8011, lifespan VAD 사전 로드)
│   └── routers/asr_v1.py # WS + REST 라우터
├── tests/                # 단위 테스트 (5개 파일)
├── static/               # 브라우저 테스트 HTML (한국어 UI)
└── README.md
```

## 프로덕션 라우터 연동

`backend-fastapi/main.py`에서 이 모듈의 라우터를 include하는 방법:

```python
from app.modules.ASR_V1.app.routers.asr_v1 import router as asr_v1_router
app.include_router(asr_v1_router)
```
