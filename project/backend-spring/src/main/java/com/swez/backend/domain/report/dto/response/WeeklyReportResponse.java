/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.dto.response;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 주간 학습 통계 응답 DTO */
@Schema(description = "주간 학습 통계 응답")
@Builder
public record WeeklyReportResponse(
    @Schema(description = "주 라벨 (ISO 주 형식)", example = "2026-W20") String weekLabel,
    @Schema(description = "일일 학습 목표 시간(분)", example = "30") int dailyStudyGoalMinutes,
    @Schema(description = "총 학습 시간(분)", example = "120") int totalStudyMinutes,
    @Schema(description = "주간 총 퀴즈 완료 수", example = "12") int totalQuizCount,
    @Schema(description = "주간 평균 정답률 (0~100)", example = "81") int averageScorePercent,
    @Schema(description = "해당 주에 완료한 챕터 수", example = "3") int completedLessons,
    @Schema(description = "완료한 과제 수", example = "2") int completedAssignmentCount,
    @Schema(description = "과제 평균 점수 (0~100)", example = "78") int assignmentAvgScore,
    @Schema(description = "연속 학습일 수", example = "5") int streakDays,
    @Schema(description = "일별 학습 통계 목록") List<DailyStat> dailyStats) {

  /** 일별 학습 통계 */
  @Schema(description = "일별 학습 통계")
  @Builder
  public record DailyStat(
      @Schema(description = "날짜", example = "2026-05-12") LocalDate date,
      @Schema(description = "학습 시간(분)", example = "30") int studyMinutes,
      @Schema(description = "퀴즈 수", example = "3") int quizCount,
      @Schema(description = "평균 정답률 (0~100)", example = "85") int averageScore) {}
}
