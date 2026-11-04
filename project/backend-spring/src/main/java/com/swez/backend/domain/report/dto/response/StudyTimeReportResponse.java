/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.dto.response;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 학습 시간 추이 응답 DTO */
@Schema(description = "학습 시간 추이 응답")
@Builder
public record StudyTimeReportResponse(
    @Schema(description = "조회 기간", example = "30d") String period,
    @Schema(description = "총 학습 시간(분)", example = "1200") int totalMinutes,
    @Schema(description = "일 평균 학습 시간(분)", example = "40.0") double averageMinutesPerDay,
    @Schema(description = "기간 내 완료한 퀴즈 수", example = "12") int totalQuizCount,
    @Schema(description = "기간 내 평균 퀴즈 정답률(0~100)", example = "75") int averageScorePercent,
    @Schema(description = "일별 학습 시간 목록") List<DailyStudyTime> dailyData) {

  /** 일별 학습 시간 */
  @Schema(description = "일별 학습 시간")
  @Builder
  public record DailyStudyTime(
      @Schema(description = "날짜", example = "2026-05-12") LocalDate date,
      @Schema(description = "학습 시간(분)", example = "45") int minutes) {}
}
