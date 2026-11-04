# ai_connectors 커넥터 추가 가이드

새 AI 모델을 붙일 때는 아래 3단계를 따른다.

## 설계 원칙

- 텍스트 커넥터는 `base.AIConnector` 프로토콜을 구현한다. ASR·TTS·OCR 등은 `base.py`의 도메인별 프로토콜을 따른다.
- 호출부는 구체 커넥터를 직접 import하지 않고 `registry.get_connector(model_name)` 같은 레지스트리 함수로 받는다.
- 벤더 예외는 `errors.py`의 공통 예외로 바꿔 올린다. 그래야 FastAPI 핸들러가 HTTP 코드를 한 가지 방식으로 매핑한다.

## 기본 모델과 폴백

- 기본 텍스트 커넥터는 `claude_sonnet`이다(`ACTIVE_TEXT_MODEL`).
- `gemini_flash`는 명시할 때만 쓰고, 이때 붙는 OpenAI 폴백은 `OPENAI_FALLBACK_ENABLED=true`일 때만 켜진다.
- 기본 ASR 커넥터는 `whisper-modal`이다(`AI_MODEL_ASR`). Modal GPU(L4)의 Whisper large-v3-turbo를 `remote.aio`로 부른다.
- Gemini ASR(`gemini-asr`)은 `ASR_GEMINI_FALLBACK=true`일 때만 Whisper 뒤에 붙는다. `asr-fallback`의 기본 tier도 `whisper-modal` 하나이고, 이 값이 true면 끝에 `gemini-asr`가 붙는다.
- `qwen3-asr-modal`은 아직 스텁이라 등록만 해 두고 기본 경로에는 넣지 않았다.
- 강의·문제 생성(Modal Qwen3.6-27B → Claude)과 커리큘럼 설계(Claude Opus 4.6)는 각 모듈 레지스트리(`app/modules/ChapterStudio_V1`, `app/modules/ExamForge_V1`)에서 고른다.

## 커넥터 추가 3단계

### Step 1: 구현 파일 생성

`ai_connectors/<vendor>.py` 예시 (Kanana API):

```python
from __future__ import annotations
import os
import httpx
from .base import AIConnector
from .schemas import AIRequest, AIResponse
from .errors import AuthError, RateLimitError, AIConnectorError


class KananaConnector:
    """Kanana API 커넥터."""

    name = "kanana-v1"

    def __init__(self) -> None:
        key = os.getenv("KANANA_API_KEY")
        if not key:
            raise AuthError("KANANA_API_KEY not set")
        self._key = key

    async def generate(self, request: AIRequest) -> AIResponse:
        async with httpx.AsyncClient(timeout=30.0) as client:
            # 실제 엔드포인트/페이로드로 치환
            resp = await client.post(
                "https://api.kakao-kanana.example/v1/generate",
                headers={"Authorization": f"Bearer {self._key}"},
                json={"prompt": request.prompt},
            )
        if resp.status_code == 429:
            raise RateLimitError("Kanana rate limit")
        if resp.status_code >= 400:
            raise AIConnectorError(f"Kanana error: {resp.status_code}")
        data = resp.json()
        return AIResponse(text=data["text"], model=self.name, raw=data)

    def supports(self, feature: str) -> bool:
        return feature in {"text_generation"}
```

### Step 2: 서브레지스트리에 등록

텍스트 커넥터는 `_registry_text.py`의 `CONNECTORS`에 넣는다. TTS는 `_registry_tts.py`, ASR은 `_registry_asr.py`처럼 도메인별 파일에 넣는다.

```python
# ai_connectors/_registry_text.py
from .kanana import KananaConnector

CONNECTORS["kanana-v1"] = lambda: KananaConnector()
```

### Step 3: .env.example 갱신

필요한 API 키 이름을 알 수 있게 `KANANA_API_KEY=` 같은 빈 값만 추가한다. 실제 키는 커밋하지 않는다.

## 로컬과 Modal 커넥터 나누기

같은 모델을 로컬(Mac)과 Modal GPU에서 다르게 돌릴 때는 커넥터 이름을 나눈다.

```python
TTS_CONNECTORS = {
    "gemini-tts": lambda: GeminiTTSConnector(),
    "qwen3-tts-modal": lambda: Qwen3TTSModalConnector(),   # Modal GPU
}
```

`.env`의 선택자(`AI_MODEL_TTS` 등) 값만 바꾸면 호출부 코드는 그대로 두고 커넥터가 바뀐다.
