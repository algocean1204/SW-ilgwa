"""TTS V2 텍스트 합성 요청 Pydantic 모델."""
from __future__ import annotations

from pydantic import BaseModel, Field


class TTSV2TextRequest(BaseModel):
    """텍스트 직접 입력 TTS V2 요청 모델.

    ref_audio_base64, ref_text 가 없으면 voice_profile_id 의 프로필을 쓰고,
    그것도 없으면 기본 고정 튜터 프로필을 쓴다.
    """

    text: str = Field(..., min_length=1, max_length=50000)
    # 고정 튜터 프로필(tutor_1 등) 또는 사용자 프로필(vpf_...) ID
    voice_profile_id: str | None = Field(default=None, max_length=80)
    # base64 인코딩한 레퍼런스 음성. ref_text 와 함께 보내야 한다
    ref_audio_base64: str | None = None
    # 레퍼런스 음성 전사문. 실제 발화와 다르면 클로닝 품질이 떨어진다
    ref_text: str | None = Field(default=None, max_length=500)
    language: str = Field(default="ko")
    # 재생 속도 배율 (0.5~2.0)
    speed: float = Field(default=1.0, ge=0.5, le=2.0)
    # True 면 LLM 낭독 재작성을 건너뛰고 normalized_text 를 그대로 합성한다
    skip_planner: bool = Field(default=False)
    # True 면 청크별 후처리(묵음 트림, LUFS 정규화, 피크 제한)를 건너뛴다
    skip_postfx: bool = Field(default=False)
    # QC 에 쓸 ASR 엔진: "whisperx", "v1_asr", "both"
    qc_engine: str = Field(default="whisperx")
