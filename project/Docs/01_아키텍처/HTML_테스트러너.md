# HTML 테스트 러너 아키텍처

## 문서 메타

| 항목 | 내용 |
|---|---|
| 버전 | v1.0 |
| 작성일 | 2026-04-20 |
| 최종 수정 | 2026-04-20 |
| 작성자 | 시스템팀 |
| 목적 | `Test_FastAPI_Module/` 샌드박스에서 모듈별 subprocess를 기동·종료하고 상태를 HTML UI로 확인하는 아키텍처를 기록한다. |
| 관련 파일 | `Test_FastAPI_Module/README.md` (저장소에 포함하지 않음), [`파이프라인_플로우.md`](./파이프라인_플로우.md) |

---

## 1. 배경

`Test_FastAPI_Module/` 샌드박스는 ASR / Denoise / TTS / OCR 4개 MLX 모델을 격리 검증하는 환경이다. 4개 모델을 동시에 적재하면 M4 Pro 48GB 메모리 한계에 가깝고, 각 모델 로드 시간이 길어 개발 사이클이 느려진다.

그래서 허브 포트(8000)를 늘 띄워 두고 개별 모듈 subprocess를 필요할 때만 올리고 내리는 컨트롤 레이어를 만들었다. HTML UI에서 버튼 하나로 각 모듈을 올리고 내릴 수 있으며, 기존 개별 모듈 HTML 파일(`asr.html`, `denoise.html`, `tts_clone.html`, `ocr.html`)은 수정 없이 리버스 프록시를 통해 그대로 동작한다.

---

## 2. 구조도

```
브라우저
  │
  ├─ GET /           → static/index.html  (모듈 컨트롤 UI)
  ├─ GET /asr.html   → static/asr.html    (기존 모듈별 테스트 UI)
  └─ POST /api/*     → 리버스 프록시 (httpx) → 해당 모듈 포트

허브 (uvicorn :8000, 항상 실행)
  ├─ /control/*      컨트롤 엔드포인트
  ├─ /api/*          리버스 프록시 (httpx 포워딩)
  └─ lifespan hook   허브 종료 시 모든 subprocess 정리

subprocess 모듈 (on-demand)
  ├─ ASR      :8001  (mlx-whisper 등)
  ├─ Denoise  :8002  (MossFormer2 등)
  ├─ TTS      :8003  (Qwen3-TTS-MLX 등)
  └─ OCR      :8004  (PaddleOCR-VL 등)
```

각 subprocess는 `subprocess.Popen`으로 기동되며, stdout/stderr는 `logs/<module>.log`로 리다이렉트된다.

---

## 3. 포트 맵

| 역할 | 포트 | 상태 |
|---|---:|---|
| 허브 (컨트롤 + 프록시) | 8000 | 항상 실행 |
| ASR 모듈 | 8001 | on-demand |
| Denoise 모듈 | 8002 | on-demand |
| TTS 모듈 | 8003 | on-demand |
| OCR 모듈 | 8004 | on-demand |

---

## 4. 컨트롤 API

### 4-1. 모듈 기동

```
POST /control/{module}/start
```

- `{module}`: `asr` | `denoise` | `tts` | `ocr`
- 이미 실행 중이면 `409 Conflict` 반환

응답 예시:
```json
{
  "module": "asr",
  "status": "started",
  "port": 8001,
  "pid": 12345
}
```

### 4-2. 모듈 종료

```
POST /control/{module}/stop
```

- SIGTERM 우선 → 2초 대기 → 응답 없으면 SIGKILL
- 이미 종료 상태면 `200 OK`로 멱등 처리

응답 예시:
```json
{
  "module": "asr",
  "status": "stopped"
}
```

### 4-3. 단일 모듈 상태 조회

```
GET /control/{module}/status
```

응답 예시:
```json
{
  "module": "asr",
  "alive": true,
  "port": 8001,
  "pid": 12345,
  "uptime_seconds": 142,
  "health_ok": true
}
```

- `health_ok`: 모듈의 `/health` 엔드포인트를 내부 호출해 확인한 결과

### 4-4. 전체 모듈 상태 일괄 조회

```
GET /control/status
```

응답 예시:
```json
{
  "asr":    { "alive": true,  "port": 8001, "uptime_seconds": 142 },
  "denoise":{ "alive": false, "port": 8002, "uptime_seconds": 0   },
  "tts":    { "alive": true,  "port": 8003, "uptime_seconds": 87  },
  "ocr":    { "alive": false, "port": 8004, "uptime_seconds": 0   }
}
```

---

## 5. 프로세스 라이프사이클

```
[start 요청]
     │
     ├─ subprocess.Popen(uvicorn ...)
     │       stdout → logs/<module>.log
     │       stderr → logs/<module>.log
     │       (FD close 필수 — Popen 직후 log_handle.close() 호출)
     │
     ├─ PID + 시작 시각 기록
     │
     └─ /health 폴링 (모듈 준비 대기)

[stop 요청]
     │
     ├─ SIGTERM 전송
     ├─ 2초 대기
     ├─ 응답 없으면 SIGKILL
     └─ lsof -i :<port>로 포트 해제 확인

[허브 종료 — lifespan hook]
     │
     └─ 등록된 모든 subprocess에 순차 SIGTERM → SIGKILL fallback
```

> FD 누수 막기: `Popen` 직후 `log_handle.close()`를 반드시 호출한다. 파일 디스크립터를 열어둔 채 프로세스를 반복 기동하면 macOS에서 FD 한계에 걸린다. 이는 현재 구현에 반영되어 있다.

---

## 6. 리버스 프록시 경로

허브의 `/api/*` 경로는 httpx를 사용해 해당 모듈 포트로 포워딩된다.

```
GET  /api/asr/transcribe   → http://localhost:8001/api/transcribe
POST /api/tts/generate     → http://localhost:8003/api/generate
GET  /api/denoise/process  → http://localhost:8002/api/process
POST /api/ocr/extract      → http://localhost:8004/api/extract
```

기존 모듈별 HTML (`asr.html`, `denoise.html`, `tts_clone.html`, `ocr.html`)은 `/api/*` 경로를 그대로 사용하므로 수정이 불필요하다. 해당 모듈이 실행 중이면 정상 응답하고, 종료 상태이면 `503 Service Unavailable`을 반환한다.

---

## 7. UI 상태 폴링

`static/index.html`은 4개 모듈 카드로 구성된다.

- 각 카드: 모듈명 + LED(초록/빨강) + 업타임 표시 + 시작/종료 버튼
- **폴링 주기**: 2초마다 `GET /control/status` 호출해 LED·업타임 갱신
- 기동 직후에는 `/health`가 응답할 때까지 LED가 노란색(대기 중)으로 표시된다
- 기존 모듈별 테스트 HTML 링크도 카드에 포함되어 있어, 모듈 기동 후 바로 이동 가능하다

---

## 8. Smoke 테스트

`scripts/smoke_control.sh`는 curl로 컨트롤 API를 차례로 호출하는 테스트다.

```bash
# 사용법
./scripts/smoke_control.sh

# 테스트 순서
# 1. GET /control/status — 전체 상태 조회
# 2. POST /control/asr/start — ASR 기동
# 3. GET /control/asr/status — 상태 확인 (alive + health_ok)
# 4. POST /control/asr/stop — ASR 종료
# 5. GET /control/asr/status — 종료 확인
# 6. 나머지 모듈(denoise, tts, ocr) 동일 순환
```

각 단계에서 HTTP 상태코드와 응답 JSON을 출력하며, 기대값과 다르면 빨간색으로 표시하고 스크립트를 종료한다.

---

## 9. 제약 사항

| 항목 | 내용 |
|---|---|
| git 미추적 | `Test_FastAPI_Module/`은 어느 서브레포에도 들어 있지 않고 `.gitignore`로도 제외한다. |
| Docker 미사용 | 샌드박스는 `uv venv` 전용. Metal GPU 접근이 필요한 MLX 모델은 컨테이너 내에서 GPU를 직접 사용할 수 없음. |
| 포트 충돌 | 8000~8004 중 기본 점유 포트가 있으면 허브나 모듈이 기동 실패한다. 실행 전 `lsof -i :8000`으로 확인 필요. |
| 동시 기동 한계 | MLX 모델 4개 동시 적재는 M4 Pro 48GB에서 메모리 한계에 가깝다. 2개 이하 동시 사용 권장. |
| 샌드박스 전용 | 이 아키텍처는 개발·검증 단계 전용이다. `backend-fastapi` 통합 후에는 Docker Compose 기반 구성으로 전환한다. |

---

## 10. 운영 주의사항

- subprocess 기동 후 모델 로드 완료까지 10~30초 소요될 수 있다. `/health` 응답 전에 `/api/*` 요청을 보내면 503이 반환된다.
- 허브(`8000`)를 먼저 기동한 뒤 모듈을 올린다. 허브가 없으면 컨트롤 API를 호출할 방법이 없다.
- 종료 시에는 허브 종료 전에 각 모듈을 먼저 내린다. 허브 lifespan hook이 자동 정리하지만, Metal GPU 메모리 해제를 확실히 하려면 수동 종료가 안전하다.
- `logs/<module>.log`는 자동으로 덮어쓴다. 이전 로그가 필요하면 기동 전에 파일명을 바꿔두어야 한다.

---

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|---|---|---|
| v1.0 | 2026-04-20 | 최초 작성 — subprocess 컨트롤 허브 아키텍처, 포트 맵, 컨트롤 API, 리버스 프록시, UI 폴링, smoke 테스트, 제약 사항 정리 |
