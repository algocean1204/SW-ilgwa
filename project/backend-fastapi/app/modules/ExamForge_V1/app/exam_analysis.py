"""모의고사 AI 총평(약점 진단) 로직 모듈.

채점 결과를 받아 채점 커넥터(기본 Claude Sonnet)로 개인별 학습 총평을 만든다.
Spring submitExam이 동기 호출해 결과지의 analysis 필드를 채운다.

- 정답·채점 스키마는 건드리지 않는다.
- LLM을 한 번만 부르므로 예산 카운터를 1로 따로 둔다.
- 호출이 실패하면 로그를 남기고 AnalysisGenerationError로 올린다.
"""
from __future__ import annotations

from pydantic import BaseModel, Field, field_validator

from app.modules.ExamForge_V1.app._analysis_prompt import (
    build_user_prompt,
    restore_option_text,
    split_weak_topics,
    system_prompt,
)
from app.modules.ExamForge_V1.app._analysis_sanitize import sanitize_analysis
from app.modules.ExamForge_V1.common.ai_bridge import (
    ChapterAIRequest,
    LLMBudgetCounter,
    get_grading_connector,
    set_current_budget,
)
from app.modules.ExamForge_V1.common.ai_bridge import _current_budget
from app.modules.ExamForge_V1.common.errors import BudgetExceededError
from app.modules.ExamForge_V1.common.logger import get_logger

logger = get_logger(__name__)

# 총평 호출 파라미터. 한 번만 생성하므로 온도를 낮게 둔다.
# 한국어 350~700자 본문(약 350~700 토큰)과 약점주제 줄이 잘리지 않게 토큰을 넉넉히 둔다.
# 모델이 사고 과정을 섞으면 본문 토큰이 줄어 잘리기 때문이다. 사고 과정 노출 자체는
# 프롬프트(_analysis_prompt)와 정제(_analysis_sanitize)로 막는다.
_MAX_TOKENS = 2048
_TEMPERATURE = 0.5
# 총평 전용 예산(서킷 브레이커). 한 번만 호출하므로 1이다.
_ANALYSIS_BUDGET = 1
# 정제 후 본문이 비었을 때(메타만 나온 경우) 보여 줄 대체 문구.
_EMPTY_BODY_FALLBACK = "총평 본문을 생성하지 못했어요. 점수와 문항별 해설을 참고해 주세요."


class AnalysisGenerationError(RuntimeError):
    """총평 생성 실패. 라우터가 잡아 폴백 문구나 503으로 바꾼다."""


class MockExamResultItem(BaseModel):
    """문항별 채점 결과. Spring과 정한 고정 계약이다.

    selectedText/correctText는 examgrading(다유형) 경로 확장 필드다.
    값이 있으면 인덱스로 복원하지 않고 텍스트를 그대로 쓴다(단답·서술·빈칸 등).
    """

    model_config = {"extra": "ignore"}

    questionIdx: int
    questionText: str
    correct: bool
    options: str = ""  # 보기 텍스트의 JSON 배열 문자열 (객관식 전용, 비객관식은 빈 문자열)
    selectedOption: str = ""  # 0-based 인덱스 문자열
    correctOption: str = ""  # 0-based 인덱스 문자열
    selectedText: str = ""  # 제출 답 사람이 읽는 텍스트 (있으면 인덱스 복원보다 우선)
    correctText: str = ""  # 정답 사람이 읽는 텍스트 (있으면 인덱스 복원보다 우선)
    explanation: str = ""

    @field_validator(
        "questionText",
        "options",
        "selectedOption",
        "correctOption",
        "selectedText",
        "correctText",
        "explanation",
        mode="before",
    )
    @classmethod
    def _none_to_empty(cls, value: object) -> object:
        """Spring(Jackson)이 null을 보내는 필드를 빈 문자열로 흡수한다.

        분석은 채점의 부가 기능이라 필드 하나가 null이어도 422로 전체가 실패하지 않게 한다.
        """
        return "" if value is None else value


class MockExamAnalyzeRequest(BaseModel):
    """POST /api/mock-exams/analyze 요청 바디(Spring 고정 계약)."""

    model_config = {"extra": "ignore"}

    examId: str = Field(min_length=1)
    subject: str = ""
    score: int = Field(ge=0)
    totalPoints: int = Field(ge=0)
    grade: str = ""
    results: list[MockExamResultItem] = Field(default_factory=list)

    @field_validator("subject", "grade", mode="before")
    @classmethod
    def _none_to_empty(cls, value: object) -> object:
        """Spring(Jackson)이 null을 보내는 선택 필드를 빈 문자열로 흡수한다."""
        return "" if value is None else value


class MockExamAnalyzeResponse(BaseModel):
    """응답 바디. analysis(총평 본문)와 weakTopics(약점 주제 목록)를 담는다.

    weakTopics는 mock_exam_submission.analysis JSON에 저장되고
    ChapterStudio 약점 집계(weakness_aggregator)가 읽는다.
    """

    analysis: str
    weakTopics: list[str] = Field(default_factory=list)


def _format_question_line(item: MockExamResultItem) -> str:
    """문항 하나를 프롬프트용 한 줄로 변환한다(보기 텍스트 복원 포함)."""
    # 텍스트 필드가 오면 그대로 사용(비객관식), 없으면 보기 인덱스에서 복원(레거시 객관식)
    selected = item.selectedText.strip() or restore_option_text(item.options, item.selectedOption)
    correct = item.correctText.strip() or restore_option_text(item.options, item.correctOption)
    mark = "정답" if item.correct else "오답"
    line = (
        f"[{mark}] {item.questionIdx}번. {item.questionText}\n"
        f"  - 내가 고른 보기: {selected}\n"
        f"  - 정답 보기: {correct}"
    )
    if item.explanation.strip():
        line += f"\n  - 해설: {item.explanation.strip()}"
    return line


def _build_request(req: MockExamAnalyzeRequest) -> ChapterAIRequest:
    """분석 요청을 ChapterAIRequest로 만든다."""
    is_perfect = req.totalPoints > 0 and req.score >= req.totalPoints
    question_lines = [_format_question_line(item) for item in req.results]
    user = build_user_prompt(
        subject=req.subject or "모의고사",
        score=req.score,
        total_points=req.totalPoints,
        grade=req.grade or "-",
        question_lines=question_lines,
        is_perfect=is_perfect,
    )
    return ChapterAIRequest(
        system=system_prompt(),
        user=user,
        max_tokens=_MAX_TOKENS,
        temperature=_TEMPERATURE,
        # 총평 기본 모델은 추론을 본문에 내지 않는 Claude Sonnet이다.
        # Gemini 폴백을 켜면 gemini-3.x 추론이 본문에 섞여서 thinking_budget=0으로 끈다.
        # Anthropic 커넥터는 이 extra를 무시한다.
        extra={"thinking_budget": 0},
    )


async def generate_analysis(req: MockExamAnalyzeRequest) -> tuple[str, list[str]]:
    """채점 결과로 (총평 본문, 약점 주제 목록)을 만든다.

    약점 주제는 총평 마지막 줄('약점주제: a | b | c')을 파싱해 나눈다.
    그 줄이 없으면 빈 목록을 쓰고 경고를 남긴다.
    호출이 실패하면 로그를 남기고 AnalysisGenerationError로 올린다.
    """
    ai_req = _build_request(req)
    connector = get_grading_connector()

    # 총평 전용 예산 카운터를 이 컨텍스트에만 둔다.
    budget = LLMBudgetCounter(_ANALYSIS_BUDGET)
    token = set_current_budget(budget)
    try:
        resp = await connector.generate(ai_req)
    except (RuntimeError, TimeoutError, BudgetExceededError) as exc:
        # 비정상 종료·타임아웃·예산 초과를 AnalysisGenerationError로 바꿔 올린다.
        logger.warning("[mock-exam-analyze] codex 총평 생성 실패 — examId=%s: %s", req.examId, exc)
        raise AnalysisGenerationError(str(exc)) from exc
    finally:
        _current_budget.reset(token)

    text = resp.text.strip()
    if not text:
        logger.warning("[mock-exam-analyze] codex 빈 응답 — examId=%s", req.examId)
        raise AnalysisGenerationError("codex가 빈 총평을 반환했어요.")

    # 사고 과정·영문 메타 줄을 먼저 지우고 약점주제 줄을 나눈다.
    # 메타 줄이 약점주제 줄 뒤에 오면 split이 잘못 나눠서 이 순서를 지킨다.
    sanitized = sanitize_analysis(text)
    if sanitized != text:
        logger.info(
            "[mock-exam-analyze] 메타/사고 누출 정제 — examId=%s, %d자→%d자",
            req.examId,
            len(text),
            len(sanitized),
        )

    body, weak_topics = split_weak_topics(sanitized)
    is_perfect = req.totalPoints > 0 and req.score >= req.totalPoints

    # 정제 후 본문이 비면(메타만 나온 경우) 대체 문구를 쓴다.
    if not body.strip():
        logger.warning(
            "[mock-exam-analyze] 정제 후 본문 공백 — 폴백 문구 사용, examId=%s",
            req.examId,
        )
        body = _EMPTY_BODY_FALLBACK
    if not weak_topics and not is_perfect:
        # 약점주제 줄이 없으면 본문은 그대로 쓰고 빈 목록임을 로그로 남긴다.
        logger.warning(
            "[mock-exam-analyze] 약점주제 줄 누락 — weakTopics 빈 목록으로 반환, examId=%s",
            req.examId,
        )
    logger.info(
        "[mock-exam-analyze] 총평 생성 완료 — examId=%s, %d자, 약점주제 %d개",
        req.examId,
        len(body),
        len(weak_topics),
    )
    return body, weak_topics
