/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.swez.backend.domain.examgrading.enums.GradingStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * exam_grade_result 테이블. 응시별 최종 채점 결과 요약.
 *
 * <p>attempt_id를 PK 겸 FK로 사용하는 1:1 관계 엔티티. GradingLeakGuard 누수 검사를 통과한 이후에만 저장(saveGradingResult
 * 호출)한다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "exam_grade_result")
public class ExamGradeResult {

  /** ExamAttempt의 PK와 같은 attempt_id. @MapsId + @OneToOne 조합으로 공유 PK 전략을 쓴다. */
  @Id
  @Column(name = "attempt_id", length = 80)
  private String attemptId;

  /** 연결된 응시 엔티티 (attempt_id를 공유 PK로 사용) */
  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "attempt_id")
  private ExamAttempt attempt;

  /** 응시자 획득 총점 */
  @Column(name = "total_score", nullable = false, precision = 9, scale = 2)
  @Builder.Default
  private BigDecimal totalScore = BigDecimal.ZERO;

  /** 시험 만점 */
  @Column(name = "max_score", nullable = false, precision = 9, scale = 2)
  @Builder.Default
  private BigDecimal maxScore = BigDecimal.ZERO;

  /** 득점 백분율 (0.00~100.00) */
  @Column(nullable = false, precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal percentage = BigDecimal.ZERO;

  /** 합격 여부 (pass_percentage 기준 초과 여부) */
  @Column(nullable = false)
  @Builder.Default
  private Boolean passed = false;

  /** 최종 채점 완료 상태 */
  @Enumerated(EnumType.STRING)
  @Column(name = "grading_status", nullable = false, length = 30)
  private GradingStatus gradingStatus;

  /** 채점 완료 시각 */
  @Column(name = "graded_at", nullable = false)
  @Builder.Default
  private OffsetDateTime gradedAt = OffsetDateTime.now();

  /**
   * AI 총평 JSON: {@code {"summary": 총평본문, "weakTopics": [약점주제]}}.
   *
   * <p>채점 직후 FastAPI 분석 호출 결과를 저장한다. 분석에 실패하면 null이고 채점은 성공으로 남는다.
   */
  @Column(columnDefinition = "TEXT")
  private String analysis;

  /** 채점 후 생성된 AI 총평 JSON을 부착한다. 분석 실패 시 호출하지 않는다(null 유지). */
  public void attachAnalysis(String analysisJson) {
    this.analysis = analysisJson;
  }
}
