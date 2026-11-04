/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 커리큘럼 응답 DTO (GET /tutoring/{courseId}/curriculum) */
@Schema(description = "커리큘럼 (강의 목록 + 상태)")
public record CurriculumResponse(
    @Schema(description = "과외 ID") String courseId,
    @Schema(description = "과외 상태") String status,
    @Schema(description = "챕터(강의) 목록") List<ChapterSummaryResponse> chapters) {}
