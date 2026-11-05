# AI Connector Registry 설계 명세

> 목적: 코드 변경 없이 벤더를 바꿀 수 있는 커넥터 패턴을 독립 스펙으로 정의한다.
> 버전: v1.5
> 최종 업데이트: 2026-04-24
>
> 상태 (2026-06-07): 이 스펙의 폴더 구조·레지스트리는 실제 `backend-fastapi/ai_connectors/`와 일부 다르다. MLX 로컬 커넥터(`mlx_whisper_turbo_connector.py`, `sensevoice_small_connector.py`, TTS MLX 등)는 제거했다. 이 문서는 커넥터 패턴의 설계 원칙으로 남기고, 실제 등록 커넥터 목록은 `backend-fastapi/ai_connectors/_registry_*.py`를 기준으로 한다.
>
> 최종 기본 경로 (2026-11-02): ASR은 `whisper-modal`(Whisper V3 Turbo, Modal 앱 `asr-whisper-v3-turbo`)이고 `gemini-asr`는 `ASR_GEMINI_FALLBACK=true`일 때만 쓴다. TTS는 `qwen3-tts-modal`(Qwen3-TTS, Modal H100)이다. 텍스트 생성은 Modal Qwen3.6-27B FP8이 실패하면 Claude로 넘어간다. TTS·ASR·임베딩은 Claude에 해당 API가 없어 Claude 폴백이 없다. 아래 §5 코드의 Gemini 기본값은 2026-06-07 시점 기록이다.

---

## 1. 해결하려는 문제

AI 모델은 교체가 잦다. 더 좋은 모델이 나오거나, 비용 구조가 바뀌거나, 로컬과 프로덕션 환경이 달라진다.

커넥터 패턴 없이 개발하면 이런 일이 생긴다:

- Kanana → Gemini 교체 시 호출부 코드 수십 곳 수정
- 로컬 테스트와 Modal 배포에서 `if env == "local":` 분기가 코드 전체에 퍼짐
- 벤더별 예외(rate limit, auth error 등)가 각자 다른 방식으로 처리됨

이를 막는 방법: **Protocol 기반 인터페이스 + Registry + `.env` 스위칭**. 호출부는 벤더를 모른다. `.env` 한 줄만 바꾸면 된다.

---

## 2. 폴더 구조

```
backend-fastapi/ai_connectors/
├── __init__.py
├── base.py              # AIConnector Protocol/ABC 정의
├── schemas.py           # 모달리티별 공유 Pydantic 스키마
├── registry.py          # 환경변수 → 커넥터 인스턴스 매핑
├── errors.py            # 벤더 에러 → 공유 예외 정규화
├── asr/
│   ├── mlx_whisper_turbo_connector.py   # 기본값 (로컬 Mac)
│   ├── whisper_turbo_modal_connector.py # 기본값 (Modal 배포)
│   ├── mlx_qwen3_asr_connector.py       # TTS ref_text 보조 용도
│   ├── qwen3_asr_modal_connector.py     # TTS ref_text 보조 용도 (Modal)
│   ├── sensevoice_small_connector.py    # SenseVoice-Small (MLX, 로컬 Mac)
│   └── fallback/
│       ├── __init__.py
│       ├── state.py           # ASRState TypedDict
│       ├── config.py          # 환경변수 기반 tier 구성
│       ├── quality.py         # CER/WER 품질 점수 계산
│       ├── nodes.py           # transcribe/evaluate/route 노드
│       ├── graph.py           # LangGraph StateGraph 조립
│       └── connector.py       # FallbackASRConnector (ASRConnector Protocol 구현)
├── denoise/
│   ├── clearvoice_local_connector.py
│   └── clearvoice_modal_connector.py
├── tts/
│   ├── qwen3_tts_mlx_connector.py
│   └── qwen3_tts_modal_connector.py
├── ocr/
│   └── paddleocr_onnx_cpu_connector.py   # 환경 무관, 단일 파일
└── llm/
    ├── kanana_nano_local_connector.py
    └── qwen3_35b_modal_connector.py
```

---

## 3. base.py — 모달리티별 Protocol

각 모달리티마다 Protocol을 정의한다. 구현 클래스는 이 인터페이스를 따라야 한다.

```python
# backend-fastapi/ai_connectors/base.py
from typing import Protocol, runtime_checkable, AsyncGenerator

@runtime_checkable
class AsrConnector(Protocol):
    async def transcribe(
        self,
        audio_bytes: bytes,
        language: str = "ko",
        **opts,
    ) -> "AsrResponse": ...

    def supports(self, feature: str) -> bool: ...


@runtime_checkable
class TtsConnector(Protocol):
    async def synthesize(
        self,
        text: str,
        voice_id: str = "default",
        **opts,
    ) -> bytes: ...  # PCM or wav bytes

    def supports(self, feature: str) -> bool: ...


@runtime_checkable
class DenoiseConnector(Protocol):
    async def denoise(
        self,
        audio_bytes: bytes,
        sample_rate: int = 48000,
        **opts,
    ) -> bytes: ...

    def supports(self, feature: str) -> bool: ...


@runtime_checkable
class OcrConnector(Protocol):
    async def extract_text(
        self,
        image: "numpy.ndarray",   # (H, W, 3) uint8 RGB
        **opts,
    ) -> "OcrResponse": ...

    def supports(self, feature: str) -> bool: ...


@runtime_checkable
class LlmConnector(Protocol):
    async def generate(
        self,
        prompt: str,
        system: str | None = None,
        **opts,
    ) -> "LlmResponse": ...

    async def stream(
        self,
        prompt: str,
        system: str | None = None,
        **opts,
    ) -> AsyncGenerator[str, None]: ...

    def supports(self, feature: str) -> bool: ...
```

`supports(feature)` 예시: `connector.supports("streaming")` → `True/False`. 벤더 간 기능 차이를 조용히 처리하는 데 사용한다.

---

## 4. schemas.py — 공유 Pydantic 스키마

```python
# backend-fastapi/ai_connectors/schemas.py
from pydantic import BaseModel

class AsrResponse(BaseModel):
    text: str
    confidence: float | None = None
    language: str | None = None

class TtsResponse(BaseModel):
    audio_bytes: bytes
    sample_rate: int
    duration_sec: float | None = None

class OcrResponse(BaseModel):
    text: str
    confidence: float | None = None
    bounding_boxes: list[dict] | None = None

class LlmResponse(BaseModel):
    content: str
    model: str
    usage: dict | None = None   # {"input_tokens": N, "output_tokens": M}
```

---

## 5. registry.py — 환경변수 기반 lazy-load 싱글턴

```python
# backend-fastapi/ai_connectors/registry.py
import asyncio
import os
from importlib import import_module
from typing import Any

# 환경변수 값 → 커넥터 클래스 경로 매핑
_REGISTRY: dict[str, dict[str, str]] = {
    "asr": {
        # 2026-06-07: mlx-whisper-turbo / whisper-turbo-modal / mlx-qwen3-asr 커넥터 제거됨. Gemini ASR 단일 경로로 통합.
        "gemini-asr":         "ai_connectors.asr.gemini_asr_connector.GeminiAsrConnector",                   # 2026-06-07 기본값. 최종은 ASR_GEMINI_FALLBACK=true일 때만
        "qwen3-asr-modal":    "ai_connectors.asr.qwen3_asr_modal_connector.Qwen3AsrModalConnector",          # 대체 경로 (Modal)
        "asr-fallback":       "ai_connectors.asr.fallback.connector.FallbackASRConnector",                    # LangGraph 2-tier 자동 폴백
    },
    "tts": {
        # 2026-06-07: qwen3-tts-mlx 커넥터 제거됨. Gemini TTS 단일 경로로 통합.
        "gemini-tts":         "ai_connectors.tts.gemini_tts_connector.GeminiTtsConnector",                   # 2026-06-07 기본값. 최종은 대체 경로
        "qwen3-tts-modal":    "ai_connectors.tts.qwen3_tts_modal_connector.Qwen3TtsModalConnector",          # 최종 기본값 (Modal H100)
    },
    "denoise": {
        "clearvoice-mossformer2-local":  "ai_connectors.denoise.clearvoice_local_connector.ClearvoiceLocalConnector",
        "clearvoice-mossformer2-modal":  "ai_connectors.denoise.clearvoice_modal_connector.ClearvoiceModalConnector",
    },
    "ocr": {
        # 2026-06-07: paddleocr-onnx-cpu 제거됨. OCR_V1(Marker+MinerU) 단일 경로로 통합.
        "ocr-v1": "ai_connectors.ocr.ocr_v1_connector.OcrV1Connector",
    },
    "llm": {
        # 2026-06-07: kanana-nano-q4-local 제거됨. Gemini Flash 단일 경로로 통합.
        "gemini-flash":         "ai_connectors.llm.gemini_flash_connector.GeminiFlashConnector",              # 2026-06-07 기본값. 최종은 Modal → Claude
        "qwen3-27b-modal":      "ai_connectors.llm.qwen3_35b_modal_connector.Qwen335bModalConnector",
        "qwen3-35b-modal":      "ai_connectors.llm.qwen3_35b_modal_connector.Qwen335bModalConnector",
    },
}

# 싱글턴 캐시와 초기화 락
_instances: dict[str, Any] = {}
_locks: dict[str, asyncio.Lock] = {}


def get_connector(modality: str) -> Any:
    """
    모달리티 이름으로 커넥터 인스턴스를 반환한다.
    첫 호출 시에만 로드하고 이후는 캐시에서 반환한다.
    모델 로딩이 무거우므로 lazy-load + 싱글턴이 필수다.
    """
    env_key = f"AI_MODEL_{modality.upper()}"
    model_name = os.getenv(env_key)
    if not model_name:
        raise EnvironmentError(f"환경변수 {env_key}가 설정되지 않음")

    if model_name in _instances:
        return _instances[model_name]

    module_path, class_name = _REGISTRY[modality][model_name].rsplit(".", 1)
    module = import_module(module_path)
    cls = getattr(module, class_name)
    instance = cls()
    _instances[model_name] = instance
    return instance
```

비동기 초기화가 필요한 커넥터(모델 로딩이 `async`인 경우)는 별도 `async def get_connector_async(modality)` 변형을 추가한다.

---

## 6. errors.py — 벤더 에러 정규화

```python
# backend-fastapi/ai_connectors/errors.py

class AiConnectorError(Exception):
    """모든 커넥터 에러의 base class"""
    pass

class RateLimitError(AiConnectorError):
    """요청 속도 초과. 재시도 가능."""
    retry_after: float | None = None

class AuthError(AiConnectorError):
    """인증 실패. API 키 확인 필요."""
    pass

class ModelNotFoundError(AiConnectorError):
    """요청한 모델을 찾을 수 없음."""
    pass

class TimeoutError(AiConnectorError):
    """추론 시간 초과."""
    pass

class VendorUnavailable(AiConnectorError):
    """벤더 서비스 일시 불가."""
    pass
```

각 커넥터 구현에서 벤더 고유 예외를 잡아 위 공유 예외로 재발생시킨다. FastAPI 라우터는 공유 예외만 처리한다.

예시:

```python
# ai_connectors/llm/qwen3_35b_modal_connector.py 내부
try:
    result = await self._client.generate(prompt)
except ModalRateLimitException as e:
    raise RateLimitError(str(e)) from e
except ModalAuthException as e:
    raise AuthError(str(e)) from e
```

---

## 7. 호출부 계약

FastAPI 라우터와 에이전트 코드는 아래 방식만 사용한다.

```python
from ai_connectors.registry import get_connector

# ASR 예시
asr = get_connector("asr")
response = await asr.transcribe(audio_bytes, language="ko")
print(response.text)

# LLM 예시
llm = get_connector("llm")
response = await llm.generate(prompt, system="너는 한국어 튜터야")
print(response.content)
```

금지 사항: 라우터나 에이전트에서 벤더 SDK를 직접 import하지 않는다. 예를 들어 `from openai import OpenAI`는 `openai_connector.py` 안에서만 허용된다.

---

## 8. 새 벤더 추가 체크리스트

1. `ai_connectors/{modality}/{vendor}_connector.py` 생성
2. 해당 모달리티 Protocol(`base.py`) 구현
3. `registry.py`의 `_REGISTRY`에 `"새-env-값": "경로.클래스명"` 추가
4. `.env.local` 또는 `.env.modal`에 새 환경변수 값 기록
5. `Test_FastAPI_Module/static/` 아래 한국어 HTML 테스트 페이지 추가
6. 환경별 모델 매핑 표 업데이트

---

## 9. OCR_v1 Protocol 기반 설계 — ai_connectors 패턴과의 공존

OCR_v1은 기존 `ai_connectors/` 패턴과 다른 접근 방식을 사용한다. 기존 커넥터는 `AIConnector` 단일 Protocol로 벤더를 추상화하지만, OCR_v1은 **기능 단위별 Protocol 5개**로 파이프라인 각 단계를 독립적으로 추상화한다.

### OCR_v1 폴더 구조

```
Test_FastAPI_Module/OCR_v1/          (샌드박스 → backend-fastapi/app/modules/OCR_v1/ 마이그레이션)
├── __init__.py          # 공개 API: OCRv1Pipeline
├── config.py            # 환경변수 20개+ (OCR_V1_* 접두사)
├── protocols.py         # Protocol 5개 정의
├── registry.py          # 구현체 레지스트리
├── connector.py         # OCRv1Pipeline (ingest, search 메서드)
├── schemas/             # state.py, request.py, response.py
├── pipeline/            # graph.py + nodes/ (8개 노드)
├── engines/             # marker_engine.py, mineru_engine.py
├── quality/             # classifier, char_corruption, reading_order, table_integrity, formula_check, aggregate
├── postprocess/         # header_footer, hyphen_restore, paragraph_merge, table_convert, formula_convert
├── chunking/            # section_chunker.py
├── embedders/           # bge_m3_embedder.py
├── stores/              # qdrant_store.py
├── rerankers/           # bge_reranker.py
├── search/              # hybrid_retriever.py, citation_builder.py
├── app/                 # FastAPI 서브앱 (port 8005)
└── tests/               # 단위/통합 테스트
```

### OCR_v1 Protocol 5개

기존 `base.py`의 `OcrConnector`는 단일 `extract_text` 메서드만 정의한다. OCR_v1은 OCR 이후 단계까지 포함하는 5개 Protocol을 `protocols.py`에 별도 정의한다.

```python
# OCR_v1/protocols.py

class OCREngine(Protocol):
    async def extract(
        self,
        pdf_bytes: bytes,
        pages: list[int] | None,
        force_ocr: bool,
        use_llm: bool,
    ) -> list[PageExtraction]: ...

    def supports(self, feature: str) -> bool: ...


class Chunker(Protocol):
    def chunk(
        self,
        markdown: str,
        pages: list[PageExtraction],
    ) -> list[ChunkRecord]: ...


class Embedder(Protocol):
    async def embed(
        self,
        texts: list[str],
    ) -> list[dict]:  # [{"dense": list[float], "sparse": {"indices": list[int], "values": list[float]}}]
        ...


class VectorStore(Protocol):
    async def upsert(self, collection: str, points: list[dict]) -> int: ...
    async def search(self, collection: str, query_embedding: dict, top_k: int, filters: dict | None) -> list[dict]: ...
    async def ensure_collection(self, collection: str) -> None: ...


class Reranker(Protocol):
    async def rerank(
        self,
        query: str,
        documents: list[str],
        top_k: int,
    ) -> list[dict]:  # [{"index": int, "score": float, "text": str}]
        ...
```

### ai_connectors registry에 OCR_v1 등록 방법

OCR_v1은 독립 모듈이지만 기존 `registry.py`에 엔트리 포인트를 등록해 통합 관리할 수 있다.

```python
# backend-fastapi/ai_connectors/registry.py _REGISTRY에 추가

"ocr_v1_pipeline": {
    "ocr-v1-default": "app.modules.OCR_v1.connector.OCRv1Pipeline",
},
```

호출부는 기존 패턴과 동일하게 `get_connector("ocr_v1_pipeline")`으로 `OCRv1Pipeline` 인스턴스를 받아 사용한다. `OCRv1Pipeline`은 `ingest(pdf_bytes, pdf_filename) -> IngestResponse`와 `search(query, top_k, filters) -> SearchResponse` 두 메서드를 공개한다.

### 기존 ai_connectors OCR 커넥터와의 관계

| 항목 | `ocr/paddleocr_onnx_cpu_connector.py` | OCR_v1 |
|---|---|---|
| 용도 | ①b 참고도서 OCR | ①c 범용 PDF OCR (독립 파이프라인) |
| 추상화 단위 | 단일 `OcrConnector` Protocol | 기능별 5-Protocol |
| 폴백 전략 | 없음 (단일 경로) | MinerU 자동 폴백 (품질 게이트 실패 시) |
| 벡터 저장 | `book_chunks` 컬렉션 (dense only) | `ocr_v1_chunks` 컬렉션 (dense + sparse) |
| 상호 교체 | 불가 (용도 다름) | 독립 모듈로 병행 운영 |

두 커넥터는 서로를 대체하지 않는다. 동시에 운영하며 각 파이프라인이 독립적으로 호출한다.

---

## 10. ASR 커넥터 확장 — SenseVoice-Small 및 LangGraph 2-tier 폴백

### 10-1. SenseVoice-Small 커넥터

`sensevoice_small_connector.py`는 [Blaizzy/mlx-audio](https://github.com/Blaizzy/mlx-audio)의 `stt.models.sensevoice` 래퍼다. 로컬 Mac 전용이며, 속도 우선 시나리오(실시간 자막 등)에 적합하다.

#### 주요 특징


| 항목 | 값 |
|---|---|
| 방식 | NAR (Non-Autoregressive) 인코더 |
| RTF | 0.009 (86× 실시간) |
| CER | 0.122 |
| 지원 언어 | 6개 (auto / zh / en / yue / ja / ko) |
| supports("emotion_detection") | True |
| supports("event_detection") | True |
| supports("language_detection") | True |

품질보다 속도가 우선인 파이프라인에서 기본 ASR을 대체하거나, Whisper 전사 전 선별 필터로 사용한다. CER 임계값(0.122)이 허용 범위 안에 있다면 단독 운영도 가능하다.

```python
# ai_connectors/asr/sensevoice_small_connector.py 구조 (개요)

from mlx_audio.stt.models.sensevoice import SenseVoiceModel
from ai_connectors.schemas import AsrResponse


class SenseVoiceSmallConnector:
    """
    SenseVoice-Small (MLX, 로컬 Mac 전용) 커넥터.
    NAR 방식으로 RTF 0.009를 달성한다. 실시간 자막 등 속도 우선 시나리오에 사용한다.
    emotion_detection / event_detection / language_detection feature flag를 지원한다.
    """

    _SUPPORTED = {"emotion_detection", "event_detection", "language_detection"}

    def __init__(self):
        # 앱 기동 시 1회만 모델 로드 (MLX 가중치 로딩 포함)
        self._model = SenseVoiceModel.from_pretrained("FunAudioLLM/SenseVoiceSmall")

    async def transcribe(
        self,
        audio_bytes: bytes,
        language: str = "auto",
        **opts,
    ) -> AsrResponse:
        # emotion/event/language 감지 플래그 처리
        detect_emotion = opts.get("emotion_detection", False)
        detect_event = opts.get("event_detection", False)
        detect_lang = opts.get("language_detection", False)

        result = await self._model.transcribe(
            audio_bytes,
            language=language,
            with_emotion=detect_emotion,
            with_event=detect_event,
            with_language_id=detect_lang,
        )
        return AsrResponse(
            text=result.text,
            confidence=result.confidence,
            language=result.language,
        )

    def supports(self, feature: str) -> bool:
        return feature in self._SUPPORTED
```

---

### 10-2. ASR Fallback — LangGraph 2-tier 자동 폴백

`fallback/` 서브패키지는 LangGraph `StateGraph` 기반 tier 자동 폴백 커넥터다. `ASRConnector` Protocol과 동일한 시그니처를 구현하므로 호출부 변경 없이 교체할 수 있다.

#### 폴백 동작 원리


1. tier 0(기본값: Whisper Turbo)에서 전사 후 CER/WER 품질 점수를 계산한다.
2. 점수가 임계값을 통과하면 즉시 반환한다.
3. 임계값 미달이면 tier 1(SenseVoice-Small)로 진행한다.
4. tier 1도 실패하면 tier 2(Qwen3-ASR)로 진행한다.
5. 전 tier 실패 시 가장 높은 점수의 응답을 best-effort로 반환한다.

tier 순서는 `config.py`의 `ASR_FALLBACK_TIERS` 환경변수로 변경할 수 있다. 2026-06-07 기본값은 `gemini-asr,qwen3-asr-modal,asr-fallback`였다. 최종 구성에서 `gemini-asr`는 `ASR_GEMINI_FALLBACK=true`일 때만 쓴다.

#### 폴더 내 파일 역할


| 파일 | 역할 |
|---|---|
| `state.py` | `ASRState` TypedDict — tier 인덱스, 각 tier 결과, 품질 점수 누적 |
| `config.py` | 환경변수(`ASR_FALLBACK_TIERS`, `ASR_QUALITY_THRESHOLD`) 파싱 |
| `quality.py` | CER / WER 기반 품질 점수 계산 |
| `nodes.py` | `transcribe` / `evaluate` / `route` 노드 함수 정의 |
| `graph.py` | `StateGraph` 조립 — 노드 연결 및 조건부 엣지 설정 |
| `connector.py` | `FallbackASRConnector` — `ASRConnector` Protocol 구현체 |

```python
# ai_connectors/asr/fallback/connector.py 구조 (개요)

from ai_connectors.asr.fallback.graph import build_graph
from ai_connectors.schemas import AsrResponse


class FallbackASRConnector:
    """
    LangGraph 2-tier 자동 폴백 ASR 커넥터.
    ASRConnector Protocol과 동일한 인터페이스를 제공하므로 호출부 변경 없이 교체 가능하다.
    tier 순서는 ASR_FALLBACK_TIERS 환경변수로 제어한다.
    """

    def __init__(self):
        # 그래프는 앱 기동 시 1회만 컴파일한다
        self._graph = build_graph()

    async def transcribe(
        self,
        audio_bytes: bytes,
        language: str = "ko",
        **opts,
    ) -> AsrResponse:
        state = await self._graph.ainvoke({
            "audio_bytes": audio_bytes,
            "language": language,
            "opts": opts,
            "tier_index": 0,
            "results": [],
        })
        # 최종 선택 결과를 공유 스키마로 반환
        best = state["best_result"]
        return AsrResponse(
            text=best["text"],
            confidence=best.get("confidence"),
            language=best.get("language"),
        )

    def supports(self, feature: str) -> bool:
        # 폴백 커넥터 자체는 tier 공통 feature만 지원한다
        return feature in {"fallback_tier"}
```

---

## 11. TTS V2 오디오북 파이프라인 — ai_connectors 패턴과의 공존

TTS V2는 기존 `ai_connectors/tts/` 커넥터를 내부적으로 소비하는 **상위 파이프라인 모듈**이다. 텍스트·마크다운 입력을 받아 오디오북 품질의 WAV를 출력한다.

### TTS V2 폴더 구조

```
Test_FastAPI_Module/TTS_V2/          (샌드박스 → backend-fastapi 마이그레이션 대상)
├── schemas/             # AudiobookState, TTSV2Request/Response
├── pipeline/
│   ├── config.py        # 환경변수 기반 파이프라인 설정
│   ├── graph.py         # LangGraph StateGraph 9노드 + retry 조건부 루프
│   └── nodes/           # load_input, clean_text, plan_reading, chunk_text, synthesize, postfx, qc, retry_router, merge
├── connectors/
│   ├── tts_engine.py    # V1 _mlx_audio_runtime 래핑 (TTSEngineSession)
│   ├── whisperx_qc.py   # mlx_whisper 기반 QC 전사
│   ├── asr_qc.py        # V1 ASR 기반 CER 검증
│   ├── llm_planner.py   # Qwen3-4B-Instruct Reading Planner
│   └── _icl_trimmer.py  # ref_text ICL 꼬리 트리밍
├── processors/
│   ├── input_adapter.py      # txt/md/raw text 읽기
│   ├── text_cleaner.py       # OCR 잔여물 정리
│   ├── text_normalizer.py    # 한국어 TTS 정규화
│   ├── _ko_number_reader.py  # 숫자 발화형 변환 헬퍼
│   ├── semantic_chunker.py   # 의미 단위 40~120자 분할
│   └── audio_postfx.py       # LUFS 정규화, 크로스페이드
├── app/                 # FastAPI 서브앱 (port 8010)
└── voice_samples/       # ref_5/6/7.m4a + 대본
```

### LangGraph 파이프라인 흐름

```
load_input → clean_text → plan_reading → chunk_text → synthesize → postfx → qc → [retry_router] → merge
```

- `retry_router`: QC 실패 청크만 재합성한다 (최대 3회). 초과 시 `failed_chunks`를 포함해 `merge`로 진행한다.

### 기존 ai_connectors TTS 커넥터와의 관계

| 항목 | `tts/qwen3_tts_mlx_connector.py` | TTS V2 |
|---|---|---|
| 용도 | 단일 텍스트 → 단일 음성 합성 (Q&A 응답, 짧은 문장) | 긴 텍스트 → 오디오북 품질 WAV (챕터 단위) |
| 추상화 단위 | 단일 `TtsConnector` Protocol | 9-노드 LangGraph 파이프라인 |
| QC | 없음 (단일 패스) | WhisperX + V1 ASR 이중 엔진 CER/WER 검증 |
| 재시도 | 없음 | 자동 retry (seed/temp/repetition_penalty 변형, 최대 3회) |
| LLM 전처리 | 없음 | Qwen3-4B Reading Planner (낭독용 텍스트 재작성) |
| 상호 교체 | 불가 (용도 다름) | V1 TTS 엔진을 내부적으로 소비 |

두 커넥터는 서로를 대체하지 않는다. V2는 V1의 `_mlx_audio_runtime`을 내부 컴포넌트로 재사용한다.

---

## 12. TTS 커넥터 내부 ASR 의존성 주입

Qwen3-TTS는 `ref_audio + ref_text + target_text` 3-입력 방식이다. `ref_text`는 `ref_audio`를 Qwen3-ASR로 전사한 결과여야 한다. TTS 커넥터는 `ref_text`가 없을 때 ASR 커넥터를 직접 호출해 자동 생성한다.

### TTS 커넥터 내부 ASR 의존성 주입 예시

```python
# ai_connectors/tts/qwen3_tts_mlx_connector.py

from ai_connectors.base import AsrConnector, TtsConnector
from ai_connectors.schemas import AsrResponse, TtsResponse


class Qwen3TtsMlxConnector:
    """
    Qwen3-TTS-Base (MLX bf16, 로컬 Mac 전용) 커넥터.
    ref_text가 없으면 주입된 ASR 커넥터로 ref_audio를 자동 전사한다.
    이는 qwen3-tts-mlx-studio의 "Transcribe Reference" 버튼과 동일한 공식 스펙이다.
    """

    def __init__(self, asr: AsrConnector | None = None):
        # ref_text 자동 전사용 ASR 커넥터 — 없어도 동작하지만 품질이 저하됨
        self._asr = asr

    async def synthesize(
        self,
        target_text: str,
        ref_audio: bytes | None = None,
        ref_text: str | None = None,
        **opts,
    ) -> TtsResponse:
        # ref_text가 없고 ASR 커넥터가 있으면 자동 전사
        if ref_text is None and self._asr is not None and ref_audio is not None:
            asr_result: AsrResponse = await self._asr.transcribe(ref_audio)
            ref_text = asr_result.text

        # ref_text가 여전히 없으면 x_vector_only_mode로 fallback (화자 유사도 저하)
        x_vector_only = ref_text is None

        # Qwen3-TTS-Base (MLX) 호출
        # ...
        audio_bytes = await self._run_mlx_tts(
            target_text=target_text,
            ref_audio=ref_audio,
            ref_text=ref_text,
            x_vector_only_mode=x_vector_only,
            **opts,
        )
        return TtsResponse(audio_bytes=audio_bytes, sample_rate=24000)
```

주의: `x_vector_only_mode=True`는 fallback이다. 이 모드에서는 화자 유사도가 0.89 → 0.75로 떨어진다. 기본 파이프라인에서는 `ref_audio + ref_text`를 모두 넘긴다.

파이프라인 전체 흐름(ref_audio 업로드 → ASR 전사 → TTS 합성)은 [`AI_파이프라인_설계.md`](./AI_파이프라인_설계.md) §D-3 ②b에 있다.

### Registry에 ASR 의존성 주입 방식

TTS 커넥터 인스턴스 생성 시 ASR 커넥터를 생성자에 주입한다. registry의 `get_connector`는 기본적으로 인수 없이 클래스를 초기화하므로, TTS처럼 의존성이 필요한 커넥터는 `get_connector_with_deps`를 별도 제공한다.

```python
# registry.py 에 추가

def get_tts_connector() -> Qwen3TtsMlxConnector:
    """
    TTS 커넥터를 생성하면서 ASR 커넥터를 의존성으로 주입한다.
    싱글턴 캐시를 그대로 사용하되, asr 인스턴스를 먼저 확보한 뒤 tts에 전달한다.
    """
    asr = get_connector("asr")   # 기존 싱글턴 반환
    tts_key = os.getenv("AI_MODEL_TTS", "")
    if tts_key not in _instances:
        module_path, class_name = _REGISTRY["tts"][tts_key].rsplit(".", 1)
        module = import_module(module_path)
        cls = getattr(module, class_name)
        _instances[tts_key] = cls(asr=asr)   # ASR 주입
    return _instances[tts_key]
```

기존 `_REGISTRY` 구조와 `_instances` 캐시는 그대로 유지한다. `get_tts_connector()`는 TTS를 필요로 하는 호출부(②b 음성 Q&A, ④ 챕터 생성)에서만 사용한다.

---

## 변경 이력

| 날짜 | 버전 | 내용 |
|---|---|---|
| 2026-04-17 | v1.0 | 최초 작성. 문제 정의, 폴더 구조, Protocol 정의, schemas/registry/errors 코드 스니펫, 호출부 계약, 새 벤더 추가 체크리스트. |
| 2026-04-17 | v1.1 | §10 추가 — TTS 커넥터 내부 ASR 의존성 주입 예시 (Qwen3TtsMlxConnector, ref_text 자동 전사, x_vector_only_mode fallback 조건). get_tts_connector() registry 확장 패턴 추가. fastapi_ai_pipeline_spec.md §D-3 ②b 교차 참조 추가. |
| 2026-04-17 | v1.2 | §10 TTS↔ASR 컴포지션 섹션 신규 공식화 — ref_text 자동 전사 패턴 명시. dangling cross-reference 해소 (fastapi_ai_pipeline_spec.md §D-3 ②b → §10 링크 유효화). |
| 2026-04-23 | v1.3 | **§10 OCR_v1 Protocol 기반 설계 추가** — 기존 `paddleocr_onnx_cpu_connector.py` OCR 섹션 다음에 OCR_v1 독립 모듈 설계 서브섹션 신설. Protocol 5개 코드 스니펫, 폴더 구조, registry 등록 방법, 기존 커넥터와 비교 표 포함. 기존 §10(TTS-ASR)은 §11로 번호 변경. |
| 2026-04-23 | v1.4 | **ASR 최종 결정 반영** — §2 폴더 구조에 `mlx_whisper_turbo_connector.py` / `whisper_turbo_modal_connector.py` 추가. §5 registry에 `"mlx-whisper-turbo"` 및 `"whisper-turbo-modal"` 항목 추가(기본값), 기존 Qwen3-ASR 항목은 TTS ref_text 보조 표기 유지. |
| 2026-04-24 | v1.5 | **ASR 커넥터 3종 추가** — §2/§5에 `sensevoice-small` (NAR 초고속, CER 0.122, RTF 0.009) 추가. §2/§5에 `asr-fallback` (LangGraph 2-tier 자동 폴백, Whisper→SenseVoice→Qwen 순) 추가. **§11 ASR 커넥터 확장** 신규 — SenseVoice-Small 코드 스니펫 및 특징 표, FallbackASRConnector 구조 및 폴더 역할 표 포함. **§12 TTS V2 오디오북 파이프라인** 신규 — 9-노드 LangGraph 파이프라인 구조, 기존 TTS 커넥터와 비교 표, V1 재사용 관계 명시. 기존 §11(TTS-ASR 의존성)은 §13으로 번호 변경. |
| 2026-06-07 | v1.6 | **AI 모델 Gemini 단일 경로 통합** — ASR registry: mlx-whisper-turbo / whisper-turbo-modal / mlx-qwen3-asr 제거, gemini-asr 기본값 추가. TTS registry: qwen3-tts-mlx 제거, gemini-tts 기본값 추가. OCR registry: paddleocr-onnx-cpu 제거, ocr-v1 추가. LLM registry: kanana-nano-q4-local 제거, gemini-flash 기본값 추가. ASR_FALLBACK_TIERS 기본값 갱신(gemini-asr 우선). |
| 2026-11-02 | — | 포트폴리오 정리: 문서 위치를 `01_아키텍처/`로 옮기고 비공개 문서 링크와 이동된 문서 링크를 정리. |
