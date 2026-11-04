"""Telegram 서비스 내부에서 공유하는 구조적 타입 프로토콜 정의."""
from __future__ import annotations

from typing import Protocol, runtime_checkable


@runtime_checkable
class TelegramClientLike(Protocol):
    """send_message만 있으면 되는 구조적 타입.

    TelegramClient와 테스트용 가짜 클라이언트를 같은 타입으로 받는다.
    """

    async def send_message(self, token: str, chat_id: str, message: str) -> object:
        """메시지를 보낸다. 반환값은 호출자가 쓰지 않는다."""
        ...
