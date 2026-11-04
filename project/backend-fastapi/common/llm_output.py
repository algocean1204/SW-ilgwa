"""LLM 출력 정규화 공통 유틸.

텍스트 모델이 Qwen3.6(Modal), Claude, gpt-5.5 중 무엇이든 뒤 단계 파서가 같은 입력을 받게,
응답에서 reasoning·마크다운·잡텍스트를 지우고 첫 유효 JSON 블록을 뽑는다.
특정 기능 모듈에 묶이지 않는 순수 함수만 둔다.

공개 API:
    - strip_thinking(text)      : <think>...</think> 및 선행 reasoning 서문 절단
    - clean_llm_text(text)      : strip_thinking + 마크다운 펜스 + 앞뒤 잡텍스트 제거
    - extract_json_block(text)  : 첫 유효 JSON 객체/배열을 괄호 깊이 추적으로 추출
    - loads_lenient(text)       : clean+extract 후 관용 파싱 (실패 시 명확한 예외)
"""
from __future__ import annotations

import json
import re
from typing import Any

from common._json_repair import (
    escape_raw_control_chars_in_strings as _escape_ctrl,
    fix_unescaped_backslashes as _fix_backslashes,
    strip_trailing_commas as _strip_trailing_commas,
)

# <think> 여는 태그 (대소문자 무시). 닫는 태그가 없어도 이 위치부터 끝까지 절단한다.
_THINK_OPEN = re.compile(r"<think\b[^>]*>", re.IGNORECASE)
# 닫힌 <think>...</think> 블록 (DOTALL로 줄바꿈 포함 본문까지 매칭).
_THINK_BLOCK = re.compile(r"<think\b[^>]*>.*?</think>", re.IGNORECASE | re.DOTALL)


def strip_thinking(text: str) -> str:
    """reasoning 블록을 제거한다.

    1) 닫힌 ``<think>...</think>`` 블록을 통째로 삭제한다.
    2) 닫히지 않은(잘린) ``<think>`` 뒤의 텍스트를 모두 자른다. 출력이 중간에 끊겨
       닫는 태그가 없는 Qwen 류 응답에서도 본문 JSON 을 살리려는 것이다.
    """
    if not text:
        return text
    cleaned = _THINK_BLOCK.sub("", text)
    open_match = _THINK_OPEN.search(cleaned)
    if open_match is not None:
        # 닫는 태그 없이 여는 태그만 있으면 그 지점부터 끝까지 reasoning 으로 보고 자른다.
        cleaned = cleaned[: open_match.start()]
    return cleaned.strip()


def clean_llm_text(text: str) -> str:
    """LLM 텍스트에서 reasoning·마크다운 펜스·앞뒤 잡텍스트를 제거한다.

    JSON 추출 직전 단계의 정규화 용도다. JSON을 추출하지는 않으며, 코드펜스 안의
    본문만 남기거나(펜스가 있을 때) strip만 한다.
    """
    cleaned = strip_thinking(text)
    fenced = _extract_fenced_body(cleaned)
    if fenced is not None:
        return fenced.strip()
    return cleaned.strip()


def extract_json_block(text: str) -> str:
    """첫 유효 JSON 객체/배열을 괄호 깊이 추적으로 추출한다.

    문자열 리터럴과 이스케이프를 인식해 따옴표 안의 괄호는 깊이에 반영하지 않는다.
    reasoning·마크다운 펜스를 먼저 제거한 뒤, ``{``/``[`` 중 먼저 등장하는 것을 시작점
    으로 삼아 짝이 맞는 닫는 괄호까지 잘라낸다. JSON 시작 토큰이 없으면 정리된 전체를
    그대로 돌려준다(다운스트림 파서가 최종 판단하도록).
    """
    cleaned = clean_llm_text(text)
    arr_idx = cleaned.find("[")
    obj_idx = cleaned.find("{")
    if arr_idx == -1 and obj_idx == -1:
        return cleaned.strip()

    if obj_idx == -1 or (arr_idx != -1 and arr_idx < obj_idx):
        start_idx, open_char, close_char = arr_idx, "[", "]"
    else:
        start_idx, open_char, close_char = obj_idx, "{", "}"

    end = _find_matching_close(cleaned, start_idx, open_char, close_char)
    if end == -1:
        # 출력이 잘려 닫는 괄호가 없으면 시작점부터 끝까지 돌려준다.
        return cleaned[start_idx:].strip()
    return cleaned[start_idx : end + 1].strip()


def loads_lenient(text: str) -> Any:
    """clean+extract 후 JSON을 관용적으로 파싱한다.

    3단계로 시도하고, 실패하면 다음 단계로 넘어간다.
        1) 표준 json.loads. Claude 출력은 대부분 여기서 끝난다.
        2) trailing comma 제거, LaTeX·코드용 백슬래시 교정.
        3) JSON 문자열 안의 raw 제어문자(개행·탭·캐리지리턴) 이스케이프.
           xgrammar 가 긴 한국어 script_text 의 제어문자를 다 이스케이프하지 못한 실측 사례
           (989자 음성대본에서 "Expecting value")를 처리한다.
    3단계 뒤에도 실패하면 원인을 담은 예외를 올린다.
    """
    block = extract_json_block(text)
    try:
        return json.loads(block)
    except json.JSONDecodeError:
        pass
    repaired = _fix_backslashes(_strip_trailing_commas(block))
    try:
        return json.loads(repaired)
    except json.JSONDecodeError:
        pass
    ctrl_fixed = _escape_ctrl(repaired)
    try:
        return json.loads(ctrl_fixed)
    except json.JSONDecodeError as final_error:
        # 3단계 모두 실패하면 어떤 입력이 왜 실패했는지 담아 예외를 낸다.
        raise ValueError(
            f"LLM JSON 파싱 실패: {final_error.msg} "
            f"(원본 길이={len(text)}, 추출 길이={len(block)})"
        ) from final_error


def _extract_fenced_body(text: str) -> str | None:
    """마크다운 코드펜스(```json ... ```)가 있으면 그 본문만 반환한다.

    펜스가 없으면 None을 반환해 호출부가 원문을 그대로 쓰도록 한다. 첫 펜스의 본문만
    취하며, 언어 태그(```json)는 제거한다.
    """
    fence_start = text.find("```")
    if fence_start == -1:
        return None
    body_start = text.find("\n", fence_start)
    if body_start == -1:
        return None
    body_start += 1
    fence_end = text.find("```", body_start)
    body = text[body_start:fence_end] if fence_end != -1 else text[body_start:]
    return body


def _find_matching_close(
    text: str, start: int, open_char: str, close_char: str
) -> int:
    """괄호 깊이를 추적해 시작점과 짝이 맞는 닫는 괄호 위치를 반환한다.

    문자열 리터럴 내부의 괄호는 무시하며, 백슬래시 이스케이프를 인식한다.
    짝을 못 찾으면 -1을 반환한다.
    """
    depth = 0
    in_string = False
    escape_next = False
    for i in range(start, len(text)):
        ch = text[i]
        if escape_next:
            escape_next = False
            continue
        if ch == "\\" and in_string:
            escape_next = True
            continue
        if ch == '"':
            in_string = not in_string
            continue
        if in_string:
            continue
        if ch == open_char:
            depth += 1
        elif ch == close_char:
            depth -= 1
            if depth == 0:
                return i
    return -1
