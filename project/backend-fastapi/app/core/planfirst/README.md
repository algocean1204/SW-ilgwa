# app/core/planfirst: plan-first 공통 계약 패키지

## 목적

4개 생성 파이프라인(커리큘럼·슬라이드·음성·모의고사)이 같이 쓰는
plan-first 공통 계약, 검증, 정답 위치 배정, 폴백 등록소를 모았다.

**기존 파이프라인 동작은 바꾸지 않는다.** 새 파이프라인이나 통합 테스트가
공통 인터페이스로 쓸 수 있는 계약만 둔다.

---

## 모듈 구성

| 파일 | 역할 |
|---|---|
| `blueprint.py` | `GenBlueprint`, `GenSlot`, `SlotConstraints` 데이터클래스 |
| `validate.py` | `SlotValidator`: 슬롯 개수·slot_id 집합 검사, 슬롯별 제약 검사 |
| `fallback.py` | `FallbackRegistry`: 기존 패스 함수 등록·조회 |
| `positions.py` | `balanced_target_positions`, `seed_from_key` (ChapterStudio·ExamForge 공용) |

---

## IN/OUT 계약

### `positions.balanced_target_positions(count, num_choices, seed)`

- IN: 슬롯 수, 선택지 수, 정수 seed
- OUT: 길이 `count`인 0-index 위치 목록 (같은 seed면 같은 결과)
- 사용처: ChapterStudio quiz_balance, ExamForge concept_blueprint

### `positions.seed_from_key(seed_key)`

- IN: 문자열 키 (강의 ID, 시험 ID 등)
- OUT: SHA-256 기반 고정 정수 seed
- 사용처: ChapterStudio quiz_balance._seed_from_key, ExamForge concept_blueprint._seed_from_key

### `blueprint.GenSlot`

- `fixed_fields`: 코드가 정하는 값. `slide_idx`, `category`, `difficulty`, `template_id`, `target_answer_position` 등
- `ai_fields`: AI가 채울 수 있는 필드 이름 목록
- `constraints`: `SlotConstraints` (min/max chars, required_markers, exact_choices, sentence_count_range, allowed_enum)

### `validate.SlotValidator`

- `validate_count_and_index_set(blueprint, filled_slot_ids)`: 슬롯이 계획대로 1:1로 채워졌는지 검사
- `validate_slot_constraints(slot, ai_output)`: 슬롯별 제약 검사
- 반환: `list[SlotViolation]` (각 위반의 `slot_id`, `check`, `message`)

### `fallback.FallbackRegistry`

- `FallbackRegistry.default()`: 기존 패스를 등록한 레지스트리
- `registry.get(kind)`: 등록된 핸들러 조회 (`None`이면 미등록)
- 기존 함수를 감싸지 않고 그대로 등록한다

---

## 파이프라인 대응 매핑

| ChapterStudio | ExamForge | 이 패키지 계약 |
|---|---|---|
| `SlidePlan` | 블루프린트 슬롯 dict | `GenSlot` |
| `SlidePlan` 목록 | `question_blueprint` | `GenBlueprint` |
| `visual_type`, `category` 등 | `template_id`, `difficulty` 등 | `fixed_fields` |
| `narration`, `visual.data` 등 | `stem`, `choices` 등 | `ai_fields` |
| `narration_len` | `num_choices` | `constraints` |
| `balanced_target_positions` (quiz_balance) | `_balanced_target_positions` (concept_blueprint) | `positions.balanced_target_positions` |
| `_seed_from_key` (quiz_balance) | `_seed_from_key` (concept_blueprint) | `positions.seed_from_key` |
| `ensure_visual_body` / `render_fallback_visual` | `balance_correct_answer_positions` | `FallbackRegistry` |
| `quiz_balance_pass` | — | `FallbackRegistry` |
| `voice_cohesion_pass` / `structure_gate_errors` | — | `FallbackRegistry` |
| `title_rules` | — | `FallbackRegistry` |
| — | `analyze_coverage`, `answer_position_counts` | `FallbackRegistry` |

---

## 의존성

이 패키지는 `app/core/` 아래에 있으므로 모듈 수준에서 `app/modules/`를 import하지 않는다.
예외로 `FallbackRegistry.default()`는 함수 안에서 import하며, 모듈이 없으면
`ImportError`를 무시하고 그 패스를 등록하지 않는다.

---

## 사용 예시

```python
from app.core.planfirst import balanced_target_positions, seed_from_key

# 20문항, 4지선다, 시험 ID 기반 seed로 균등 배정
seed = seed_from_key("exam_2026_01")
positions = balanced_target_positions(20, num_choices=4, seed=seed)
```

```python
from app.core.planfirst import FallbackRegistry

registry = FallbackRegistry.default()
ensure_visual = registry.get("ensure_visual_body")
if ensure_visual:
    html, warnings = ensure_visual(raw_html, category, slide_idx)
```
