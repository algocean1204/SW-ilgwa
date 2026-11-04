"""LangGraph 기반 폴백 ASR 커넥터.

ASRConnector Protocol 을 구현해서 호출부를 바꾸지 않고 끼울 수 있다.
LangGraph StateGraph 가 tier 순서대로 전사하고 품질을 검사해 처음 통과한 결과를 돌려준다.
모든 tier 가 통과하지 못하면 점수가 가장 높은 응답을, 응답이 하나도 없으면 AIConnectorError 를 낸다.
"""
from __future__ import annotations

import logging
from typing import Any, Callable

from ai_connectors.errors import AIConnectorError
from ai_connectors.schemas import ASRRequest, ASRResponse

from .graph import get_compiled_graph
from .state import ASRState

_LOG = logging.getLogger(__name__)


class FallbackASRConnector:
    """LangGraph 기반 폴백 ASR 커넥터.

    tier_factories[i]() 는 i 번째 tier 의 ASRConnector 를 반환해야 한다.
    커넥터를 factory 로 받는 것은 콜드스타트 비용을 그 tier 에 들어갈 때만 내려는 것이다.
    """

    name: str = "asr-fallback"

    def __init__(
        self,
        tier_factories: list[Callable[[], Any]],
        tier_model_names: list[str],
    ) -> None:
        """tier_factories 와 tier_model_names 는 같은 인덱스끼리 짝이다."""
        if len(tier_factories) != len(tier_model_names):
            raise ValueError("tier_factories 와 tier_model_names 길이 불일치")
        if not tier_factories:
            raise ValueError("tier_factories 비어 있음 — 최소 1개 필요")
        self._tier_factories = tier_factories
        self._tier_model_names = tier_model_names
        self._graph = get_compiled_graph()

    async def generate(self, request: ASRRequest) -> ASRResponse:
        """ASR 요청을 폴백 그래프로 돌려 가장 좋은 결과를 돌려준다."""
        initial: ASRState = {
            "request": request,
            "tier_factories": self._tier_factories,
            "tier_model_names": self._tier_model_names,
            "current_tier": 0,
            "attempts": [],
            "done": False,
        }
        final_state = await self._graph.ainvoke(initial)
        return self._pick_best_response(final_state)

    def _pick_best_response(self, state: ASRState) -> ASRResponse:
        """응답이 있는 attempt 중 quality_score 가 가장 높은 것을 고른다.

        응답이 있는 attempt 가 하나도 없으면 AIConnectorError 를 낸다.
        """
        attempts = state["attempts"]
        if not attempts:
            raise AIConnectorError("모든 tier attempts 실패 — 결과 없음")

        # 에러 없이 전사한 attempt 만 후보다
        ok = [a for a in attempts if a["response"] is not None]
        if not ok:
            failures = "; ".join(
                f"tier{a['tier']}:{a['quality_reason']}" for a in attempts
            )
            raise AIConnectorError(f"모든 tier 실패: {failures}")

        # 품질을 통과한 attempt 가 있으면 점수가 가장 높아 그게 뽑힌다
        best = max(ok, key=lambda a: a["quality_score"])
        _LOG.info(
            "폴백 완료 — tier %d (%s) 채택, score=%.2f, 시도 %d회",
            best["tier"],
            best["model_name"],
            best["quality_score"],
            len(attempts),
        )
        return best["response"]

    def supports(self, feature: str) -> bool:
        """지원 기능 플래그."""
        return feature in {"transcription", "language_hint", "fallback"}
