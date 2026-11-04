"""TTS V2 레퍼런스 결정 async 헬퍼.

vpf_ 사용자 프로필은 DB 조회가 필요해 여기서 처리한다. 나머지 헬퍼는 helpers.py 에 있다.
"""
from __future__ import annotations

import os

import asyncpg
from fastapi import HTTPException

from app.modules.TTS_V2.app.routers.helpers import load_profile_ref, resolve_ref
from app.modules.TTS_V2.voice_profiles import is_user_profile


async def load_user_profile_ref(profile_id: str) -> tuple[bytes, str, str]:
    """DB 에 저장된 사용자 음성 프로필의 레퍼런스를 읽는다.

    DATABASE_URL 이 없거나 조회가 실패하면 503, 프로필이 없으면 404를 낸다.
    """
    from app.modules.TTS_V2.services.voice_profile_db import get_voice_profile
    from app.modules.TTS_V2.services.voice_profile_storage import load_audio

    dsn = os.getenv("DATABASE_URL")
    if dsn is None:
        raise HTTPException(status_code=503, detail="DB 연결을 사용할 수 없습니다.")
    conn: asyncpg.Connection | None = None
    try:
        conn = await asyncpg.connect(dsn)
        record = await get_voice_profile(conn, profile_id)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 조회 실패: {exc}") from exc
    finally:
        if conn is not None:
            await conn.close()
    if record is None:
        raise HTTPException(status_code=404, detail=f"사용자 음성 프로필 없음: {profile_id}")
    audio_bytes = load_audio(record.ref_audio_url)
    return audio_bytes, record.ref_text, profile_id


async def resolve_ref_async(
    ref_audio_base64: str | None,
    ref_text: str | None,
    voice_profile_id: str | None = None,
) -> tuple[bytes, str, str]:
    """요청의 레퍼런스 오디오와 대본을 정한다 (async 버전).

    직접 업로드 값이 하나라도 있으면 resolve_ref 로 넘긴다.
    vpf_ 프로필은 DB 에서 읽고, 나머지는 고정 튜터 프로필을 쓴다.
    """
    if ref_audio_base64 is not None or ref_text is not None:
        return resolve_ref(ref_audio_base64, ref_text, voice_profile_id)
    if is_user_profile(voice_profile_id):
        assert voice_profile_id is not None  # 타입 좁히기용. is_user_profile(None) 은 False 다
        return await load_user_profile_ref(voice_profile_id)
    return load_profile_ref(voice_profile_id)
