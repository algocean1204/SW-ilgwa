/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 과목별 진행률 응답 DTO */
@Schema(description = "과목별 진행률 응답")
@Builder
public record ProgressReportResponse(
    @Schema(description = "전체 과외 수", example = "5") int totalCourses,
    @Schema(description = "완료한 과외 수", example = "2") int completedCourses,
    @Schema(description = "과목별 진행률 목록") List<SubjectProgress> subjects) {

  /** 과목별 챕터 진행률 */
  @Schema(description = "과목별 챕터 진행률")
  @Builder
  public record SubjectProgress(
      @Schema(description = "과목명", example = "수학") String subject,
      @Schema(description = "전체 챕터 수", example = "10") int totalChapters,
      @Schema(description = "완료한 챕터 수", example = "4") int completedChapters,
      @Schema(description = "진행률 (%)", example = "40") int progressPercent) {}
}
