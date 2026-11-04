"""튜터 미리보기 API 라우터. Spring TutorService.previewTutor가 호출한다.

Spring은 POST {fastapi-base}/api/tutors/{tutorId}/preview로 PreviewTutorRequest를
JSON으로 보내고, RestTemplate로 동기 응답 PreviewTutorResponse를 받는다.
그래서 이 엔드포인트는 백그라운드로 넘기지 않고 멘트를 생성해 바로 본문으로 돌려준다.

요청/응답 필드는 Spring DTO(PreviewTutorRequest / PreviewTutorResponse)와 1:1로 맞춘다.
tutorId는 경로 변수이며 요청 바디에 없다(Spring도 path로만 보낸다).
"""
from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException, Path
from pydantic import BaseModel, Field

from app.modules.ChapterStudio_V1.ai_connectors.errors import ConnectorError
from app.modules.ChapterStudio_V1.app.tutor_preview_generate import generate_tutor_preview
from app.modules.ChapterStudio_V1.common.modal_gpu_release import modal_gpu_scope
from app.modules.ChapterStudio_V1.pipeline.tts_routing import TutorVoiceProfile
from app.modules.ChapterStudio_V1.pipeline.voice_audio import synthesize_voice_audio

_LOG = logging.getLogger(__name__)

router = APIRouter(prefix="/api/tutors", tags=["tutor-preview"])


class PreviewTutorRequest(BaseModel):
    """Spring PreviewTutorFastApiRequest와 같은 바디다.

    슬라이더는 0~100 정수(미설정 허용=None), 토글은 불리언, sampleQuestion은 필수다.
    voiceSampleUrl은 Spring이 서버 보관값에서 채워 보내는 선택 필드다. 샘플이 있으면 그 음성을,
    없으면 프리셋·중립 참조 음성을 Modal Qwen3-TTS에 넘겨 합성한다(TTS 라우팅 체인과 같다).
    """

    toneSlider: int | None = Field(default=None, ge=0, le=100)
    speedSlider: int | None = Field(default=None, ge=0, le=100)
    depthSlider: int | None = Field(default=None, ge=0, le=100)
    questionStyleSlider: int | None = Field(default=None, ge=0, le=100)
    useEmoji: bool | None = None
    useFormalSpeech: bool | None = None
    sampleQuestion: str = Field(min_length=1)
    voiceSampleUrl: str | None = None


class PreviewTutorResponse(BaseModel):
    """Spring PreviewTutorResponse와 같은 응답이다(previewText, settingTags, audioUrl).

    audioUrl은 TTS 합성에 성공하면 브라우저가 재생할 오디오 URL이고, 실패하면 None이다.
    음성 합성이 실패해도 텍스트 미리보기는 돌려주도록 best-effort로 처리한다.
    """

    previewText: str
    settingTags: list[str]
    audioUrl: str | None = None


async def _synthesize_preview_audio(
    tutor_id: str,
    preview_text: str,
    voice_sample_url: str | None,
    use_formal_speech: bool | None,
) -> str | None:
    """미리보기 멘트를 TTS로 합성해 오디오 URL을 돌려준다. 실패하면 None이다(best-effort).

    레슨 파이프라인과 같은 TTS 라우팅 체인을 쓰고, 저장은 ObjectStorage_V1(S3/MinIO)을 거친다.
    """
    profile = TutorVoiceProfile(
        tutor_id=tutor_id,
        is_default_tutor=False,
        voice_sample_url=(voice_sample_url or "").strip(),
        use_formal_speech=True if use_formal_speech is None else bool(use_formal_speech),
        tutor_tagline="",
    )
    try:
        async with modal_gpu_scope(f"preview:{tutor_id}"):
            records = await synthesize_voice_audio(
                [{"slide_idx": 0, "script_text": preview_text}],
                tutor_profile=profile,
            )
        audio_url = records[0].get("audio_url") if records else None
        if isinstance(audio_url, str) and audio_url:
            return audio_url
        _LOG.warning("[tutor_preview] TTS 결과에 audio_url 이 없음 — tutorId=%s", tutor_id)
    except Exception as exc:  # noqa: BLE001 음성은 부가 기능이라 모든 실패를 삼키고 텍스트만 돌려준다.
        _LOG.warning("[tutor_preview] 미리보기 TTS 합성 실패 — tutorId=%s, error=%s", tutor_id, exc)
    return None


@router.post("/{tutorId}/preview", response_model=PreviewTutorResponse)
async def preview_tutor(
    req: PreviewTutorRequest,
    tutorId: str = Path(min_length=1),
) -> PreviewTutorResponse:
    """튜터 설정으로 미리보기 멘트를 동기 생성해 PreviewTutorResponse로 돌려준다.

    텍스트를 만든 뒤 같은 멘트를 TTS로 합성해 audioUrl을 채운다(실패하면 None).
    텍스트 생성 실패는 500으로 바꾼다. Spring은 비2xx/빈 본문을 TUT_004로 처리한다.
    """
    _LOG.debug("[tutor_preview] 미리보기 요청 — tutorId=%s", tutorId)
    try:
        preview_text, setting_tags = await generate_tutor_preview(
            sample_question=req.sampleQuestion,
            tone_slider=req.toneSlider,
            speed_slider=req.speedSlider,
            depth_slider=req.depthSlider,
            question_style_slider=req.questionStyleSlider,
            use_emoji=req.useEmoji,
            use_formal_speech=req.useFormalSpeech,
        )
    except (ConnectorError, ValueError) as exc:
        # 커넥터 정규화 예외(인증/한도/타임아웃 등)와 빈 출력(ValueError)을 5xx로 올린다.
        # Spring은 비2xx/빈 본문을 TUT_004로 처리하므로 에러를 삼키지 않는다.
        _LOG.error("[tutor_preview] 미리보기 생성 실패 — tutorId=%s, error=%s", tutorId, exc)
        raise HTTPException(status_code=500, detail="튜터 미리보기 생성에 실패했다.") from exc

    # 음성 합성은 best-effort라 실패해도 텍스트 미리보기는 정상 응답한다.
    audio_url = await _synthesize_preview_audio(
        tutor_id=tutorId,
        preview_text=preview_text,
        voice_sample_url=req.voiceSampleUrl,
        use_formal_speech=req.useFormalSpeech,
    )

    return PreviewTutorResponse(
        previewText=preview_text, settingTags=setting_tags, audioUrl=audio_url
    )
