"""AI 커넥터 공통 스키마, 프로토콜, 예산 카운터 정의."""
from __future__ import annotations

import contextvars
import math
from typing import Protocol, runtime_checkable

# 하드 상한은 문항 비례 예산 풀의 이 배수 이상으로 둔다. 풀 안에서 끝나는 정상 시험은
# 끊지 않고 풀을 크게 넘는 폭주만 막는다. 고정 60은 20문항 1패스(약 81회)도 끊었다.
_RUNAWAY_FACTOR = 1.25

from pydantic import BaseModel, ConfigDict, Field

from app.modules.ExamForge_V1.common.config import (
    mock_exam_max_llm_calls,
    pipeline_llm_budget,
)
from app.modules.ExamForge_V1.common.errors import BudgetExceededError


class ChapterAIRequest(BaseModel):
    """AI 요청 스키마. ChapterStudio_V1과 같다."""

    model_config = ConfigDict(strict=True, frozen=True)

    system: str = ""
    user: str = Field(min_length=1)
    max_tokens: int = Field(ge=1, le=32000)
    temperature: float = Field(ge=0.0, le=2.0)
    extra: dict[str, str | int | float | bool] = Field(default_factory=dict)


class ChapterAIResponse(BaseModel):
    """AI 응답 스키마. ChapterStudio_V1과 같다."""

    model_config = ConfigDict(strict=True, frozen=True)

    text: str
    model: str
    input_tokens: int = Field(ge=0)
    output_tokens: int = Field(ge=0)
    finish_reason: str


@runtime_checkable
class AIConnector(Protocol):
    """AI 커넥터 프로토콜."""

    name: str

    async def generate(self, req: ChapterAIRequest) -> ChapterAIResponse: ...
    def supports(self, feature: str) -> bool: ...


class LLMBudgetCounter:
    """파이프라인 실행 단위로 공유되는 LLM 호출 카운터.

    budget을 넘으면 BudgetExceededError를 던져 재시도로 비용이 불어나는 것을 막는다.

    [reserve: 해설 생성 몫]
    출고 문항은 모두 해설이 있어야 한다. 앞 단계 재시도가 예산을 다 쓰면 해설이
    비어 출고됐다. 그래서 예산 일부(reserve)를 떼어 일반 호출은 'budget - reserve'까지,
    해설 생성은 allow_reserve=True로 reserve까지 쓰게 한다.

    [max_calls: 시험당 하드 상한]
    budget과 별도로 시험 1회의 호출 상한(env MOCK_EXAM_MAX_LLM_CALLS)을 둔다.
    reserve 호출도 이 상한을 넘지 못한다. 넘으면 BudgetExceededError를 던지고
    라우터 except가 확보한 문항으로 끝낸다.
    """

    def __init__(
        self,
        budget: int | None = None,
        reserve: int = 0,
        max_calls: int | None = None,
    ) -> None:
        self._budget = budget if budget is not None else pipeline_llm_budget()
        # reserve는 0 이상, 전체 예산 이하로 맞춘다.
        self._reserve = max(0, min(reserve, self._budget))
        # 시험당 하드 상한.
        # - max_calls 인자를 주면 그 값을 쓴다(테스트·운영자 강제용).
        # - 안 주면 env 하한과 "예산 풀 × _RUNAWAY_FACTOR" 중 큰 값이라 풀보다 낮아지지 않는다.
        if max_calls is not None:
            self._max_calls = max(1, max_calls)
        else:
            self._max_calls = max(
                1, mock_exam_max_llm_calls(), math.ceil(self._budget * _RUNAWAY_FACTOR)
            )
        self._count = 0

    @property
    def count(self) -> int:
        """현재까지 사용된 LLM 호출 횟수."""
        return self._count

    @property
    def budget(self) -> int:
        """설정된 예산 한도(reserve 포함 전체)."""
        return self._budget

    @property
    def reserve(self) -> int:
        """해설 생성 전용으로 떼어 둔 호출 수."""
        return self._reserve

    @property
    def max_calls(self) -> int:
        """시험당 LLM 호출 하드 상한."""
        return self._max_calls

    def _limit(self, allow_reserve: bool) -> int:
        """현재 호출이 사용할 수 있는 상한을 반환한다.

        allow_reserve=False(일반 호출): budget - reserve 까지만 허용.
        allow_reserve=True(해설 생성): 전체 budget 까지 허용.
        두 경우 모두 max_calls를 넘지 않는다.
        """
        pool_limit = self._budget if allow_reserve else self._budget - self._reserve
        return min(pool_limit, self._max_calls)

    @property
    def exceeded(self) -> bool:
        """일반 호출 기준 예산 초과 여부(reserve 제외분 소진 여부).

        retry_router 서킷 브레이커가 참조한다. reserve를 제외한 일반 풀이
        소진되면 더 이상의 upstream 재시도를 막아 reserve를 해설 생성에 남긴다.
        """
        return self._count >= self._budget - self._reserve

    def _effective_allow_reserve(self, allow_reserve: bool | None) -> bool:
        """allow_reserve 인자가 None이면 contextvar(해설 생성 구간)에서 읽는다.

        커넥터마다 같은 코드를 두지 않도록 여기서 allow_reserve_budget을 읽는다.
        """
        if allow_reserve is not None:
            return allow_reserve
        return allow_reserve_budget.get()

    def increment(self, *, allow_reserve: bool | None = None) -> None:
        """호출 1회 카운트 증가. 예산 초과 시 예외 발생.

        allow_reserve가 True이거나 해설 생성 구간이면 reserve 풀까지 사용 가능.
        """
        effective = self._effective_allow_reserve(allow_reserve)
        self._count += 1
        if self._count > self._limit(effective):
            raise BudgetExceededError(
                f"LLM 호출 예산 초과: {self._count}/{self._limit(effective)}회"
                f"(전체 {self._budget}, 예약 {self._reserve}). "
                "파이프라인을 중단하고 부분 결과를 반환한다."
            )

    def check(self, *, allow_reserve: bool | None = None) -> None:
        """호출 전 예산 잔여 확인. 이미 초과 시 예외 발생.

        allow_reserve가 True이거나 해설 생성 구간이면 reserve 풀까지 사용 가능.
        """
        effective = self._effective_allow_reserve(allow_reserve)
        if self._count >= self._limit(effective):
            raise BudgetExceededError(
                f"LLM 호출 예산 소진: {self._count}/{self._limit(effective)}회"
                f"(전체 {self._budget}, 예약 {self._reserve}). "
                "추가 호출을 차단한다."
            )


# 파이프라인 실행 단위로 공유되는 예산 카운터 (contextvars로 요청 격리)
current_budget: contextvars.ContextVar[LLMBudgetCounter | None] = contextvars.ContextVar(
    "_current_budget", default=None
)

# 해설 생성 구간 플래그. True인 동안 커넥터 호출은 reserve 풀까지 쓴다.
# 해설 생성 노드가 들어갈 때 set, 나올 때 reset한다.
# ai_bridge가 커넥터를 import하므로 순환 import를 피하려고 여기에 둔다.
allow_reserve_budget: contextvars.ContextVar[bool] = contextvars.ContextVar(
    "_allow_reserve_budget", default=False
)
