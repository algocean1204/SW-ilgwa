from __future__ import annotations

import json

from app.modules.ChapterStudio_V1.pipeline.parallel_generate import _default_personalization_args
from app.modules.ChapterStudio_V1.pipeline.parallel_prompts import build_slides_request, parse_slide, parse_slides


def test_parse_slides_renders_visual_spec_to_html() -> None:
    payload = {
        "slides": [
            {
                "slide_idx": 0,
                "title": "영하 수 비교",
                "category": "math",
                "narration": "수직선에서는 오른쪽에 있는 수가 더 큽니다.",
                "visual": {
                    "type": "number_line",
                    "data": {
                        "min": -10,
                        "max": 0,
                        "ticks": [{"value": -10, "label": "영하 10"}, {"value": -5, "label": "영하 5"}, {"value": 0, "label": "0"}],
                        "points": [{"value": -10, "label": "영하 10"}, {"value": -5, "label": "영하 5"}],
                        "highlights": [{"from": -10, "to": -5, "label": "거리 5"}],
                        "arrow": "right_bigger",
                    },
                },
                "checkpoint": "-10과 -5 중 더 큰 수를 말할 수 있는가?",
            }
        ]
    }

    slide = parse_slides(json.dumps(payload, ensure_ascii=False))[0]

    assert slide.html.startswith('<section class="visual-slide">')
    assert "number-line-visual" in slide.html
    assert "영하 10" in slide.html
    assert slide.narration == "수직선에서는 오른쪽에 있는 수가 더 큽니다."
    assert slide.visual["type"] == "number_line"


def test_parse_slides_uses_safe_fallback_when_visual_missing() -> None:
    payload = {
        "slides": [
            {
                "slide_idx": 0,
                "title": "복구 슬라이드",
                "category": "math",
                "narration": "스펙이 비어도 슬라이드는 깨지지 않아야 합니다.",
                "checkpoint": "안전 폴백 확인",
            }
        ]
    }

    slide = parse_slides(json.dumps(payload, ensure_ascii=False))[0]

    assert "example-box-visual" in slide.html
    assert "<svg" in slide.html
    assert slide.visual["type"] == "example_box"


def test_parse_slides_fallback_uses_voice_script_when_narration_empty() -> None:
    payload = {
        "slides": [
            {
                "slide_idx": 0,
                "title": "음수 비교",
                "category": "text",
                "narration": "",
                "focus": "수직선에서 오른쪽에 있는 음수가 더 크다는 점",
                "voice_script": {
                    "script_text": (
                        "음수 비교에서는 숫자의 크기만 보지 말고 수직선 위치를 먼저 확인해야 합니다. "
                        "-3은 -8보다 오른쪽에 있으므로 실제 값이 더 큽니다. "
                        "절댓값이 크다는 말과 수 자체가 크다는 말을 분리해서 생각하면 실수를 줄일 수 있습니다."
                    )
                },
                "checkpoint": "수직선 기준으로 음수를 비교할 수 있는가?",
            }
        ]
    }

    slide = parse_slides(json.dumps(payload, ensure_ascii=False))[0]

    assert "음수 비교에서는 숫자의 크기만 보지 말고" in slide.narration
    assert "핵심 내용을 예제 카드로 정리한다" not in slide.narration
    assert "시각 자료" not in slide.html
    assert "음수 비교에서는 숫자의 크기만 보지 말고" in slide.html


def test_parse_slides_fallback_uses_title_focus_without_voice_script() -> None:
    payload = {
        "slides": [
            {
                "slide_idx": 0,
                "title": "절댓값 의미",
                "category": "text",
                "narration": "핵심 내용을 예제 카드로 정리한다.",
                "focus": "0에서 떨어진 거리와 실제 수의 크기를 구분한다",
                "checkpoint": "절댓값과 크기 비교를 구분할 수 있는가?",
            }
        ]
    }

    slide = parse_slides(json.dumps(payload, ensure_ascii=False))[0]

    assert slide.narration.startswith("절댓값 의미에서는 0에서 떨어진 거리")
    assert "핵심 내용을 예제 카드로 정리한다" not in slide.narration
    assert "절댓값 의미에서는 0에서 떨어진 거리" in slide.html


def test_parse_slide_pins_requested_index_and_plan_type() -> None:
    raw = {"slide_idx": 0, "title": "음수 비교", "category": "text", "narration": "수직선에서 오른쪽 수가 더 큽니다.",
           "visual": {"type": "example_box", "data": {"problem": "-3과 2", "steps": ["위치 확인"], "answer": "2"}},
           "checkpoint": "더 큰 수를 고를 수 있는가?"}
    plan = [{"slide_idx": 4, "visual_type": "step_flow", "must_have": []}]

    slide = parse_slide(json.dumps(raw, ensure_ascii=False), 4, plan)

    assert slide.slide_idx == 4
    assert slide.visual["type"] == "step_flow"


def test_build_slides_request_for_one_slide_narrows_plan_to_that_slot() -> None:
    plan = [
        {"slide_idx": 0, "visual_type": "number_line", "must_have": []},
        {"slide_idx": 1, "visual_type": "step_flow", "must_have": []},
    ]

    req = build_slides_request("요청", "outline", 10, "concept_flow", _default_personalization_args(), plan, slide_idx=1)

    assert req.extra["slide_idx"] == 1
    assert req.extra["plan_visual_types"] == "step_flow"
    assert "slide_idx=1 슬롯 1장만" in req.user

