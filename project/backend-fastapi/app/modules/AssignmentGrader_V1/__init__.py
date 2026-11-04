"""텍스트 과제 답안을 Claude Sonnet으로 채점하고 결과를 Spring에 콜백한다."""

from .router import router
from .grading_handler import handle_text_grading_request
from .schemas import GradeRequest, GradeResponse

__all__ = [
    "router",
    "handle_text_grading_request",
    "GradeRequest",
    "GradeResponse",
]
