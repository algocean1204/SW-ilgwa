/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 최근 학습 과외 목록 응답 DTO (GET /tutoring/recent) */
@Builder
@Schema(description = "최근 학습 과외 목록")
public record RecentTutoringResponse(
    @Schema(description = "최근 학습 과외 항목 목록") List<RecentTutoringItem> items) {

  /** 최근 학습 과외 개별 항목 */
  @Builder
  @Schema(description = "최근 학습 과외 항목")
  public record RecentTutoringItem(
      @Schema(description = "과외 ID", example = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV") String courseId,
      @Schema(description = "과외명", example = "수학 기초") String courseName,
      @Schema(description = "과목", example = "수학") String subject,
      @Schema(description = "진행 상태", example = "ACTIVE") String status,
      @Schema(description = "진행률 (0~100)", example = "45") int progressPercent,
      @Schema(description = "마지막 접근 일시") LocalDateTime lastAccessedAt) {}
}
