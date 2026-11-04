"""plan-first 공통 계약 패키지.

커리큘럼·슬라이드·음성·모의고사 생성 파이프라인이 같이 쓰는 슬롯 계약, 검증,
정답 위치 배정, 폴백 등록소를 모았다. 기존 파이프라인 코드는 바꾸지 않는다.
"""
from app.core.planfirst.blueprint import GenBlueprint, GenSlot, SlotConstraints
from app.core.planfirst.validate import SlotValidator
from app.core.planfirst.fallback import FallbackRegistry
from app.core.planfirst.positions import (
    balanced_target_positions,
    seed_from_key,
)

__all__ = [
    "GenBlueprint",
    "GenSlot",
    "SlotConstraints",
    "SlotValidator",
    "FallbackRegistry",
    "balanced_target_positions",
    "seed_from_key",
]
