"""사용자 업로드 음성 파일의 저장, 읽기, 삭제.

로컬 저장 경로: app/modules/TTS_V2/user_voices/{user_id}/{profile_id}.wav
S3 저장 key: voice-profiles/{user_id}/{profile_id}.wav
S3 가 켜져 있으면 S3 에, 아니면 로컬에 저장한다. 입력은 24000Hz mono WAV 로 맞춰 저장한다.
"""
from __future__ import annotations

from pathlib import Path

import httpx
import numpy as np

from app.modules.ObjectStorage_V1 import put_object, s3_enabled
from common.audio_io import (
    duration_sec as calc_duration,
    encode_wav_bytes,
    ensure_mono,
    load_audio_from_bytes,
)

_USER_VOICES_DIR = Path(__file__).resolve().parent.parent / "user_voices"

# 저장 샘플레이트. TTS ICL 모드 입력 기준에 맞춘다
_TARGET_SR = 24000
_REF_DOWNLOAD_TIMEOUT_SEC = 10.0


def _ensure_user_dir(user_id: str) -> Path:
    """사용자 전용 디렉터리가 없으면 생성 후 반환한다."""
    user_dir = _USER_VOICES_DIR / user_id
    user_dir.mkdir(parents=True, exist_ok=True)
    return user_dir


def _profile_path(user_id: str, profile_id: str) -> Path:
    """프로필 파일 경로를 계산한다."""
    return _ensure_user_dir(user_id) / f"{profile_id}.wav"


def _normalize_to_wav(audio_bytes: bytes) -> tuple[bytes, int, float]:
    """입력 오디오를 24000Hz mono WAV 로 바꾼다. DB 에 넣을 sample_rate, duration_sec 도 돌려준다."""
    y, _ = load_audio_from_bytes(audio_bytes, target_sr=_TARGET_SR)
    mono: np.ndarray = ensure_mono(y)
    dur = calc_duration(mono, _TARGET_SR)
    wav_bytes = encode_wav_bytes(mono, _TARGET_SR)
    return wav_bytes, _TARGET_SR, dur


async def save_audio(user_id: str, profile_id: str, audio_bytes: bytes) -> tuple[str, int, float]:
    """오디오를 정규화해 저장하고 (ref_audio_url, sample_rate, duration_sec) 를 반환한다.

    S3 를 쓰면 S3 URL, 아니면 로컬 경로를 ref_audio_url 로 돌려준다.
    """
    wav_bytes, sample_rate, dur = _normalize_to_wav(audio_bytes)
    if s3_enabled():
        url = await put_object(
            wav_bytes,
            key=f"voice-profiles/{user_id}/{profile_id}.wav",
            content_type="audio/wav",
        )
        return url, sample_rate, dur
    dest = _profile_path(user_id, profile_id)
    dest.write_bytes(wav_bytes)
    return str(dest), sample_rate, dur


def load_audio(ref_audio_url: str) -> bytes:
    """ref_audio_url 에서 WAV 바이트를 읽는다. http(s) URL 이면 내려받는다."""
    if ref_audio_url.startswith(("http://", "https://")):
        response = httpx.get(ref_audio_url, timeout=_REF_DOWNLOAD_TIMEOUT_SEC)
        response.raise_for_status()
        return response.content
    path = Path(ref_audio_url)
    if not path.exists():
        raise RuntimeError(f"레퍼런스 음성 파일이 없음: {ref_audio_url}")
    return path.read_bytes()


def delete_audio(ref_audio_url: str) -> None:
    """로컬 파일만 지운다. URL(S3)이거나 파일이 없으면 그냥 넘어간다."""
    if ref_audio_url.startswith(("http://", "https://")):
        return
    path = Path(ref_audio_url)
    if path.exists():
        path.unlink()
