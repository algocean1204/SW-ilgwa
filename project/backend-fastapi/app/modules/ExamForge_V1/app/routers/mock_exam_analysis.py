"""Spring ↔ FastAPI 모의고사 AI 총평 라우터.

Spring submitExam이 채점 결과를 보내 동기 호출하면, 채점 커넥터(기본 Claude Sonnet)로
총평을 만들어 결과지 analysis 필드에 넣을 문자열을 돌려준다.

엔드포인트: POST /api/mock-exams/analyze
응답: {"analysis": "<한국어 총평 문자열>"}

실패하면 빈 analysis 대신 폴백 문구를 200으로 돌려준다. 폴백 문구는 사용자에게
그대로 보여 줄 수 있는 안내문이며, 에러는 라우터에서 로그로 남긴다.
"""
from __future__ import annotations

from fastapi import APIRouter

from app.modules.ExamForge_V1.app.exam_analysis import (
    AnalysisGenerationError,
    MockExamAnalyzeRequest,
    MockExamAnalyzeResponse,
    generate_analysis,
)
from app.modules.ExamForge_V1.common.logger import get_logger

logger = get_logger(__name__)

router = APIRouter(prefix="/api/mock-exams", tags=["mock-exams"])

# 총평 생성 실패 시 사용자에게 보여 줄 폴백 문구
_FALLBACK_ANALYSIS = "총평 생성에 실패했어요. 점수와 문항별 해설을 참고해 주세요."


@router.post("/analyze", response_model=MockExamAnalyzeResponse)
async def analyze_mock_exam(req: MockExamAnalyzeRequest) -> MockExamAnalyzeResponse:
    """채점 결과로 총평을 만들어 돌려준다.

    실패해도 200과 폴백 문구를 돌려 Spring 흐름을 끊지 않는다.
    """
    try:
        analysis, weak_topics = await generate_analysis(req)
    except AnalysisGenerationError as exc:
        # 원인은 generate_analysis가 로그로 남겼고, 여기서는 폴백을 썼다는 사실만 남긴다.
        logger.warning(
            "[mock-exam-analyze] 폴백 총평 반환 — examId=%s: %s", req.examId, exc
        )
        analysis = _FALLBACK_ANALYSIS
        weak_topics = []
    return MockExamAnalyzeResponse(analysis=analysis, weakTopics=weak_topics)
