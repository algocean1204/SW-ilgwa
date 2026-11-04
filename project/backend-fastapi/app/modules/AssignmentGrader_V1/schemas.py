"""AssignmentGrader_V1 입출력 스키마."""

from __future__ import annotations

from pydantic import BaseModel, Field


class GradeRequest(BaseModel):
    """Spring이 FastAPI에 보내는 텍스트 채점 요청."""

    # Spring submission PK. sbm_{ULID 26자} 형식
    submission_id: str = Field(min_length=1)
    # 채점 프롬프트에 넣는 과제 제목
    assignment_title: str = Field(default="제출 과제")
    # 과제 설명(채점 기준). 없으면 범용 기준을 쓴다
    assignment_description: str | None = None
    # JSON 배열에서 파싱한 문항 목록
    assignment_questions: list[str] = Field(default_factory=list)
    answer_text: str = Field(min_length=1)


class GradeResponse(BaseModel):
    """채점 요청 수락 응답. 채점 결과는 Spring 콜백으로 따로 간다."""

    accepted: bool
    submission_id: str
