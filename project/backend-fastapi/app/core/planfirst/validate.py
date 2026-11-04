"""plan-first 슬롯 검증 함수.

ChapterStudio의 _validate_indices와 ExamForge의 _validate_question_count/
_validate_concept_key_uniqueness가 각자 하던 슬롯 개수·slot_id 집합 검사와
슬롯별 제약 검사를 일반화했다. 기존 검증 경로는 그대로 두고 필요한 곳에서 골라 쓴다.
"""
from __future__ import annotations

from dataclasses import dataclass

from app.core.planfirst.blueprint import GenBlueprint, GenSlot, SlotConstraints


@dataclass
class SlotViolation:
    """검증 실패 1건."""
    slot_id: str
    check: str       # 'count' | 'index_set' | 'chars' | 'markers' | 'choices' | 'enum'
    message: str


class SlotValidator:
    """GenBlueprint 슬롯 검증기.

    (a) 슬롯 개수와 slot_id 집합이 계획과 같은지
    (b) 슬롯별 제약(min/max chars, required markers, 보기 수, enum)
    """

    def validate_count_and_index_set(
        self,
        blueprint: GenBlueprint,
        filled_slot_ids: set[str],
    ) -> list[SlotViolation]:
        """블루프린트 slot_id 집합과 실제 채워진 slot_id 집합을 비교한다.

        누락 슬롯과 초과 슬롯을 각각 violations로 반환한다.
        """
        plan_ids = {s.slot_id for s in blueprint.slots}
        missing = plan_ids - filled_slot_ids
        extra = filled_slot_ids - plan_ids
        violations: list[SlotViolation] = []
        for sid in sorted(missing):
            violations.append(SlotViolation(
                slot_id=sid,
                check="index_set",
                message=f"슬롯 {sid}가 채워지지 않았다.",
            ))
        for sid in sorted(extra):
            violations.append(SlotViolation(
                slot_id=sid,
                check="index_set",
                message=f"슬롯 {sid}는 블루프린트에 없는 초과 슬롯이다.",
            ))
        if len(blueprint.slots) != len(filled_slot_ids):
            violations.append(SlotViolation(
                slot_id="__blueprint__",
                check="count",
                message=(
                    f"슬롯 수 불일치: 계획={len(blueprint.slots)}, "
                    f"실제={len(filled_slot_ids)}"
                ),
            ))
        return violations

    def validate_slot_constraints(
        self,
        slot: GenSlot,
        ai_output: dict[str, object],
    ) -> list[SlotViolation]:
        """슬롯 하나의 ai_output이 SlotConstraints를 만족하는지 검사한다."""
        c = slot.constraints
        violations: list[SlotViolation] = []
        # 문자열인 ai_fields를 공백으로 이어 붙여 글자 수를 잰다
        text_value = " ".join(
            str(ai_output.get(f, ""))
            for f in slot.ai_fields
            if isinstance(ai_output.get(f), str)
        )
        violations.extend(self._check_chars(slot.slot_id, text_value, c))
        violations.extend(self._check_markers(slot.slot_id, text_value, c))
        violations.extend(self._check_choices(slot.slot_id, ai_output, c))
        violations.extend(self._check_enum(slot.slot_id, ai_output, slot.fixed_fields, c))
        return violations

    # 내부 검사 메서드

    @staticmethod
    def _check_chars(
        slot_id: str,
        text: str,
        c: SlotConstraints,
    ) -> list[SlotViolation]:
        """min/max chars 제약을 검사한다."""
        violations: list[SlotViolation] = []
        length = len(text)
        if c.min_chars is not None and length < c.min_chars:
            violations.append(SlotViolation(
                slot_id=slot_id,
                check="chars",
                message=f"텍스트 길이 {length}자가 최소 {c.min_chars}자 미달이다.",
            ))
        if c.max_chars is not None and length > c.max_chars:
            violations.append(SlotViolation(
                slot_id=slot_id,
                check="chars",
                message=f"텍스트 길이 {length}자가 최대 {c.max_chars}자 초과다.",
            ))
        return violations

    @staticmethod
    def _check_markers(
        slot_id: str,
        text: str,
        c: SlotConstraints,
    ) -> list[SlotViolation]:
        """required_markers 제약을 검사한다."""
        violations: list[SlotViolation] = []
        for marker in c.required_markers:
            if marker not in text:
                violations.append(SlotViolation(
                    slot_id=slot_id,
                    check="markers",
                    message=f"필수 마커 '{marker}'가 출력에 없다.",
                ))
        return violations

    @staticmethod
    def _check_choices(
        slot_id: str,
        ai_output: dict[str, object],
        c: SlotConstraints,
    ) -> list[SlotViolation]:
        """exact_choices(보기 수) 제약을 검사한다."""
        if c.exact_choices is None:
            return []
        # 'options' 또는 'choices' 필드를 보기 목록으로 인식한다
        choices = ai_output.get("options") or ai_output.get("choices") or []
        if not isinstance(choices, (list, tuple)):
            return []
        actual = len(choices)
        if actual != c.exact_choices:
            return [SlotViolation(
                slot_id=slot_id,
                check="choices",
                message=f"보기 수 {actual}개가 요구 {c.exact_choices}개와 다르다.",
            )]
        return []

    @staticmethod
    def _check_enum(
        slot_id: str,
        ai_output: dict[str, object],
        fixed_fields: dict[str, object],
        c: SlotConstraints,
    ) -> list[SlotViolation]:
        """allowed_enum 제약을 검사한다.

        ai_output의 type, visual_type, fixed_fields의 kind 중 처음 있는 값을 본다.
        """
        if c.allowed_enum is None:
            return []
        value = str(
            ai_output.get("type")
            or ai_output.get("visual_type")
            or fixed_fields.get("kind", "")
        )
        if value and value not in c.allowed_enum:
            return [SlotViolation(
                slot_id=slot_id,
                check="enum",
                message=f"값 '{value}'이 허용 enum {sorted(c.allowed_enum)} 밖이다.",
            )]
        return []


__all__ = ["SlotValidator", "SlotViolation"]
