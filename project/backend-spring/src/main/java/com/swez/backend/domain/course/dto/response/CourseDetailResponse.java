/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.dto.response;

import java.time.LocalDateTime;

import com.swez.backend.domain.course.enums.CourseStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 과외 상세 응답 DTO (GET /tutoring/{id}) */
@Builder
@Schema(description = "과외 상세")
public record CourseDetailResponse(
    @Schema(description = "과외 ID") String id,
    @Schema(description = "과외명") String title,
    @Schema(description = "과목") String subject,
    @Schema(description = "튜터 ID") String tutorId,
    @Schema(description = "튜터 이름") String tutorName,
    @Schema(description = "튜터 아바타 URL") String tutorAvatarUrl,
    @Schema(description = "진행 상태") CourseStatus status,
    @Schema(description = "자료 유형 (pdf/topic)") String sourceType,
    @Schema(description = "강의 모드") String mode,
    @Schema(description = "진행률 (0.0~1.0)") double progress,
    @Schema(description = "전체 강의 수") int totalLessons,
    @Schema(description = "학습한 강의 수") int completedLessons,
    @Schema(description = "마지막 학습 일시") LocalDateTime lastStudiedAt,
    @Schema(description = "생성 일시") LocalDateTime createdAt) {}
