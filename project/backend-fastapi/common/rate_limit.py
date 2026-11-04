"""API 키 기반 토큰 버킷 속도 제한 미들웨어.

API 키(없으면 클라이언트 IP)마다 토큰 버킷을 따로 둔다.
버킷 크기는 .env 의 RATE_LIMIT_RPM(분당 요청 수)과 RATE_LIMIT_BURST(순간 허용량)로 정한다.
버킷 상태는 asyncio.Lock 으로 감싸 코루틴끼리 겹쳐 쓰지 않게 한다.
"""
from __future__ import annotations

import asyncio
import time

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

# 버킷 상태: (현재 토큰 수, 마지막 리필 시각)
_BucketState = tuple[float, float]


class RateLimitMiddleware(BaseHTTPMiddleware):
    """API 키 기반 토큰 버킷 속도 제한 미들웨어.

    요청마다 X-API-Key 헤더(없으면 클라이언트 IP)를 키로 쓴다.
    exempt_paths 경로(헬스체크·docs 등)는 제한하지 않는다.
    """

    def __init__(
        self,
        app,
        rpm: int = 60,
        burst: int = 10,
        exempt_paths: set[str] | None = None,
    ) -> None:
        super().__init__(app)
        # 버킷 계산은 초 단위라 분당 리필 속도를 초당으로 바꾼다
        self._refill_rate: float = rpm / 60.0
        self._burst: float = float(burst)
        self._exempt: set[str] = exempt_paths or set()
        # {키: (토큰, 마지막_리필_시각)}
        self._buckets: dict[str, _BucketState] = {}
        # 버킷 전체에 락 하나를 쓴다. 키별 락은 아직 필요 없다.
        self._lock = asyncio.Lock()

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        """요청을 가로채 속도 제한 여부를 판단한다."""
        # 면제 경로는 토큰을 쓰지 않는다
        if request.url.path in self._exempt:
            return await call_next(request)

        # API 키, 없으면 클라이언트 IP
        key = request.headers.get("X-API-Key") or (
            request.client.host if request.client else "unknown"
        )
        allowed = await self._try_consume(key)
        if not allowed:
            # 429 와 함께 Retry-After 헤더로 다시 시도할 시간을 알린다
            return JSONResponse(
                status_code=429,
                content={"detail": "요청 속도 제한을 초과했습니다. 잠시 후 다시 시도하세요."},
                headers={"Retry-After": "60"},
            )
        return await call_next(request)

    def reset_buckets(self) -> None:
        """모든 버킷 상태를 초기화한다.

        테스트 픽스처가 테스트마다 토큰 소비를 되돌릴 때 쓴다. 운영 코드에서는 부르지 않는다.
        """
        self._buckets.clear()

    async def _try_consume(self, key: str) -> bool:
        """토큰 버킷에서 토큰 1개를 소비하려 시도한다.

        토큰이 충분하면 True를 반환하고 버킷을 갱신한다.
        토큰이 부족하면 False를 반환한다.
        """
        now = time.monotonic()
        async with self._lock:
            tokens, last_refill = self._buckets.get(key, (self._burst, now))
            # 마지막 리필 뒤 지난 시간만큼 토큰을 채우되 버스트 상한을 넘지 않는다
            elapsed = now - last_refill
            tokens = min(self._burst, tokens + elapsed * self._refill_rate)

            if tokens < 1.0:
                # 토큰이 모자라면 버킷만 갱신하고 쓰지 않는다
                self._buckets[key] = (tokens, now)
                return False

            self._buckets[key] = (tokens - 1.0, now)
            return True
