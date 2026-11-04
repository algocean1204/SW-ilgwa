"""ASR V1 라우터. WebSocket 스트리밍 전사와 REST 오프라인 전사를 제공한다."""
from __future__ import annotations

from fastapi import APIRouter, File, Query, UploadFile, WebSocket

from ai_connectors.schemas import ASRResponse
from app.modules.ASR_V1.schemas.config import SessionConfig

router = APIRouter()


@router.websocket("/ws/asr-v1")
async def asr_v1_ws(websocket: WebSocket) -> None:
    """실시간 ASR WebSocket 엔드포인트.

    업스트림: 바이너리 PCM Int16LE 16kHz mono 또는 JSON 제어 커맨드
    다운스트림: JSON 메시지 (vad_state, partial, final, flush, error, session_ack)
    """
    from app.modules.ASR_V1.pipeline.session import StreamingASRSession

    await websocket.accept()
    # 클라이언트 설정은 아직 받지 않고 기본값으로 세션을 연다
    config = SessionConfig()
    session = StreamingASRSession(websocket=websocket, config=config)
    await session.run()


@router.post("/api/asr-v1/offline", response_model=ASRResponse)
async def offline_transcribe(
    audio: UploadFile = File(..., description="전사할 오디오 파일"),
    language: str = Query(default="ko", description="ISO 639-1 언어 코드"),
    model: str | None = Query(default=None, description="ASR 커넥터 키"),
) -> ASRResponse:
    """레퍼런스 오디오용 오프라인 배치 전사.

    업로드 파일을 16kHz로 리샘플링한 뒤 배치 ASR을 실행한다.
    """
    from app.modules.ASR_V1.connectors.asr_engine import ASREngine
    from app.modules.ASR_V1.pipeline.config import ASR_MODEL
    from common.audio_io import load_audio_from_bytes

    raw = await audio.read()
    audio_np, sr = load_audio_from_bytes(raw, target_sr=16000)
    engine = ASREngine(model_name=model or ASR_MODEL)
    return await engine.transcribe(audio_np, sr=sr, lang=language)
