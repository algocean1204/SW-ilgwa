/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.course.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 강의 생성 진행 상태 응답 DTO (GET /tutoring/{courseId}/lessons/{chapterId}/generation-status)
 *
 * <p>FastAPI ChapterStudio가 기록하는 노드 단위 진행률(lesson_generation_status)을 그대로 전달한다.
 * FastAPI 응답 필드명과 1:1 매핑되어 Jackson 역직렬화에 추가 설정이 필요 없다.
 * FastAPI 가 응답하지 않으면 status=unknown 을 반환하고, 프론트는 기존 챕터 단위 표시를 쓴다.
 */
@Builder
@Schema(description = "강의 생성 진행 상태")
public record LessonGenerationStatusResponse(
    @Schema(description = "강의(챕터) ID") String lessonId,
    @Schema(description = "생성 상태 (unknown/pending/running/done/failed)") String status,
    @Schema(description = "마지막으로 완료된 생성 노드명") String currentNode,
    @Schema(description = "완료된 노드 수") int completedNodes,
    @Schema(description = "전체 노드 수") int totalNodes,
    @Schema(description = "노드 단위 진행률 (0~100)") int progressPercent,
    @Schema(description = "실패 시 오류 메시지") String errorMessage) {

  /** FastAPI 미응답·오류 시 쓰는 폴백 응답. 프론트는 unknown 이면 기존 표시를 유지한다. */
  public static LessonGenerationStatusResponse unknown(String lessonId) {
    return LessonGenerationStatusResponse.builder()
        .lessonId(lessonId)
        .status("unknown")
        .completedNodes(0)
        .totalNodes(0)
        .progressPercent(0)
        .build();
  }
}
