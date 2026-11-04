"""문항 생성 재호출 요청 보조 함수."""
from __future__ import annotations

from app.modules.ExamForge_V1.common.ai_bridge import ChapterAIRequest

# gemini-3.5-flash는 thinking 토큰(실측 0~20735)이 max_output_tokens를 잠식한다.
# 4000/5000에서는 JSON이 잘려(finish_reason=MAX_TOKENS) 10건 중 3건만 유효했다.
# 그래서 1차 16000, 2차 24000을 쓴다. 문항 1개 출력은 1000~3000 토큰이라
# 나머지는 thinking 여유분이다(모델 한도 65536).
_GEN_MAX_TOKENS_FIRST = 16000  # 1차 시도: thinking 헤드룸 확보
_GEN_MAX_TOKENS_RETRY = 24000  # 2차 시도: JSON 재출력 안정성 우선


def build_generation_request(system: str, prompt: str, attempt: int) -> ChapterAIRequest:
    """문항 생성 요청을 만든다. 2차 시도는 JSON 안정성을 우선한다."""
    return ChapterAIRequest(
        system=system,
        user=prompt,
        max_tokens=_GEN_MAX_TOKENS_RETRY if attempt else _GEN_MAX_TOKENS_FIRST,
        temperature=0.1 if attempt else 0.45,
    )


def strict_generation_prompt(prompt: str, task: dict) -> str:
    """깨진 JSON 후 재호출할 때 더 좁은 계약을 붙인다."""
    return (
        f"{prompt}\n\n[JSON 재출력 강제]\n"
        f"- JSON 배열 하나만 출력하시오. 정확히 {task.get('count', 0)}개여야 한다.\n"
        "- 설명문, 마크다운, 주석, trailing comma를 금지한다.\n"
        "- 문자열 안의 큰따옴표와 줄바꿈은 JSON 표준에 맞게 이스케이프하시오.\n"
        "- 코드 조각, 백틱, 원본 코드 줄은 JSON 문자열에 직접 넣지 말고 자연어로 지칭하시오."
    )
