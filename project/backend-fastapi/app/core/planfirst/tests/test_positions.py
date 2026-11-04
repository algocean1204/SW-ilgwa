"""positions.py 테스트.

  1. balanced_target_positions / seed_from_key 기본 동작
  2. 공용화 전 구현(복사본)과 출력이 같은지
     - ChapterStudio quiz_balance.balanced_target_positions
     - ExamForge concept_blueprint._balanced_target_positions
  3. 경계값 (count=0, num_choices=1, 음수 입력)
"""
from __future__ import annotations

import hashlib
import random

import pytest

from app.core.planfirst.positions import balanced_target_positions, seed_from_key


# 공용화 전 구현 복사본(비교 기준)


def _ref_cs_balanced(count: int, num_choices: int = 4, seed: int = 0) -> list[int]:
    """ChapterStudio quiz_balance.py 기존 구현 복사본."""
    if count < 0:
        raise ValueError("count는 0 이상이어야 한다.")
    if num_choices < 1:
        raise ValueError("num_choices는 1 이상이어야 한다.")
    positions = [idx % num_choices for idx in range(count)]
    random.Random(seed).shuffle(positions)
    return positions


def _ref_ef_balanced(count: int, num_choices: int = 4, seed: int = 0) -> list[int]:
    """ExamForge concept_blueprint.py 기존 구현 복사본."""
    if count <= 0:
        return []
    positions = [idx % num_choices for idx in range(count)]
    random.Random(seed).shuffle(positions)
    return positions


def _ref_cs_seed(seed_key: str) -> int:
    """ChapterStudio quiz_balance.py 기존 _seed_from_key 복사본."""
    if seed_key == "":
        return 0
    digest = hashlib.sha256(seed_key.encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big")


def _ref_ef_seed(seed_key: str) -> int:
    """ExamForge concept_blueprint.py 기존 _seed_from_key 복사본."""
    if not seed_key:
        return 0
    digest = hashlib.sha256(seed_key.encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big")


# 기존 구현과 출력 비교


@pytest.mark.parametrize("count", [1, 2, 5, 10, 20, 50, 100])
@pytest.mark.parametrize("num_choices", [4, 5])
@pytest.mark.parametrize("seed", [0, 42, 999, 1_000_000])
def test_byte_identical_vs_chapterstudio(count: int, num_choices: int, seed: int) -> None:
    """ChapterStudio 기존 구현과 출력이 같다."""
    expected = _ref_cs_balanced(count, num_choices, seed)
    actual = balanced_target_positions(count, num_choices, seed)
    assert actual == expected, (
        f"ChapterStudio 불일치: count={count}, nc={num_choices}, seed={seed}"
    )


@pytest.mark.parametrize("count", [1, 2, 5, 10, 20, 50, 100])
@pytest.mark.parametrize("num_choices", [4, 5])
@pytest.mark.parametrize("seed", [0, 42, 999, 1_000_000])
def test_byte_identical_vs_examforge(count: int, num_choices: int, seed: int) -> None:
    """ExamForge 기존 구현과 출력이 같다."""
    expected = _ref_ef_balanced(count, num_choices, seed)
    actual = balanced_target_positions(count, num_choices, seed)
    assert actual == expected, (
        f"ExamForge 불일치: count={count}, nc={num_choices}, seed={seed}"
    )


@pytest.mark.parametrize("key", ["", "test_key", "exam_123", "한글키", "  spaces  "])
def test_seed_from_key_identical_to_chapterstudio(key: str) -> None:
    """seed_from_key가 ChapterStudio 기존 구현과 같은 값을 낸다."""
    assert seed_from_key(key) == _ref_cs_seed(key)


@pytest.mark.parametrize("key", ["", "test_key", "exam_123", "한글키", "  spaces  "])
def test_seed_from_key_identical_to_examforge(key: str) -> None:
    """seed_from_key가 ExamForge 기존 구현과 같은 값을 낸다."""
    assert seed_from_key(key) == _ref_ef_seed(key)


# 기본 동작


def test_count_zero_returns_empty() -> None:
    assert balanced_target_positions(0) == []
    assert balanced_target_positions(0, num_choices=5, seed=42) == []


def test_length_equals_count() -> None:
    for count in [1, 7, 15]:
        result = balanced_target_positions(count)
        assert len(result) == count


def test_all_values_in_range() -> None:
    result = balanced_target_positions(20, num_choices=4, seed=0)
    assert all(0 <= v < 4 for v in result)


def test_even_distribution_large_count() -> None:
    """4배수 count에서 0..3이 정확히 균등 분포함을 확인한다."""
    result = balanced_target_positions(100, num_choices=4, seed=0)
    counts = {v: result.count(v) for v in range(4)}
    assert all(c == 25 for c in counts.values()), f"분포 불균등: {counts}"


def test_deterministic_same_seed() -> None:
    """같은 seed면 항상 동일 결과를 반환한다."""
    r1 = balanced_target_positions(10, num_choices=4, seed=77)
    r2 = balanced_target_positions(10, num_choices=4, seed=77)
    assert r1 == r2


def test_different_seeds_produce_different_order() -> None:
    """다른 seed는 (충분히 큰 count에서) 다른 순서를 만든다."""
    r1 = balanced_target_positions(20, num_choices=4, seed=1)
    r2 = balanced_target_positions(20, num_choices=4, seed=2)
    assert r1 != r2


def test_num_choices_one() -> None:
    """num_choices=1이면 모든 위치가 0이어야 한다."""
    result = balanced_target_positions(5, num_choices=1, seed=0)
    assert result == [0, 0, 0, 0, 0]


# 경계값: count<=0이면 []


@pytest.mark.parametrize("count", [0, -1, -5, -100])
@pytest.mark.parametrize("num_choices", [1, 4, 5])
@pytest.mark.parametrize("seed", [0, 42, 999])
def test_nonpositive_count_returns_empty_like_examforge(
    count: int, num_choices: int, seed: int
) -> None:
    """count<=0이면 num_choices·seed와 관계없이 []를 반환한다(ExamForge 기존 구현과 같다)."""
    expected = _ref_ef_balanced(count, num_choices, seed)
    actual = balanced_target_positions(count, num_choices, seed)
    assert actual == expected == []


def test_nonpositive_count_empty_even_when_num_choices_invalid() -> None:
    """count<=0이면 num_choices<1이어도 []를 먼저 반환한다."""
    assert balanced_target_positions(0, num_choices=0) == []
    assert balanced_target_positions(-3, num_choices=0) == []
    assert balanced_target_positions(-1, num_choices=-2) == []


# 예외: count>0 인데 num_choices<1


def test_positive_count_zero_num_choices_raises() -> None:
    """count>0·num_choices<1은 ValueError다. ExamForge 기존 구현은 ZeroDivisionError를 냈다."""
    with pytest.raises(ValueError):
        balanced_target_positions(5, num_choices=0)


def test_positive_count_negative_num_choices_raises() -> None:
    with pytest.raises(ValueError):
        balanced_target_positions(5, num_choices=-1)


def test_original_also_fails_on_positive_count_zero_num_choices() -> None:
    """기존 구현도 count>0·num_choices=0에서 예외를 냈는지 확인한다.

    - ExamForge: num_choices 확인 없음 → ZeroDivisionError
    - ChapterStudio: num_choices<1 확인 → ValueError
    - 이 모듈: ChapterStudio와 같은 ValueError
    """
    with pytest.raises(ZeroDivisionError):
        _ref_ef_balanced(5, num_choices=0)
    with pytest.raises(ValueError):
        _ref_cs_balanced(5, num_choices=0)


# seed_from_key 기본 동작


def test_seed_empty_string_is_zero() -> None:
    assert seed_from_key("") == 0


def test_seed_nonzero_for_nonempty() -> None:
    assert seed_from_key("some_key") != 0


def test_seed_reproducible() -> None:
    """같은 키면 항상 같은 seed."""
    assert seed_from_key("abc") == seed_from_key("abc")


def test_seed_different_keys_different_seeds() -> None:
    assert seed_from_key("key_a") != seed_from_key("key_b")


# 호출처가 이 모듈을 쓰는지 확인


def test_chapterstudio_quiz_balance_delegates_to_single_source() -> None:
    """ChapterStudio quiz_balance.balanced_target_positions가 이 모듈과 같은 결과를 내는지 확인한다."""
    from app.modules.ChapterStudio_V1.postprocess.quiz_balance import (
        balanced_target_positions as cs_btp,
    )
    expected = balanced_target_positions(10, num_choices=4, seed=42)
    assert cs_btp(10, num_choices=4, seed=42) == expected


def test_examforge_concept_blueprint_delegates_to_single_source() -> None:
    """ExamForge concept_blueprint._balanced_target_positions가 이 모듈과 같은 결과를 내는지 확인한다."""
    from app.modules.ExamForge_V1.pipeline.nodes.concept_blueprint import (
        _balanced_target_positions as ef_btp,
    )
    expected = balanced_target_positions(10, num_choices=4, seed=42)
    assert ef_btp(10, num_choices=4, seed=42) == expected
