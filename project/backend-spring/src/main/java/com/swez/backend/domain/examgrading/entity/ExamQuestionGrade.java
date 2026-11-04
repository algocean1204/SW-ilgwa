/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.swez.backend.domain.examgrading.enums.GradingMode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * exam_question_grade 테이블. 문항별 채점 결과.
 *
 * <p>grading_mode에 따라 채점 주체가 다르다: - DETERMINISTIC: Spring이 서버 스냅샷으로 즉시 채점 - AI_RUBRIC: FastAPI
 * /api/exam-forge/grade-submission 결과 - MANUAL_REVIEW_REQUIRED: 신뢰도 미달 또는 예외 상황
 *
 * <p>feedback은 GradingLeakGuard가 정답·해설 문자열 누수를 검사한 뒤에만 저장한다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "exam_question_grade")
public class ExamQuestionGrade {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 소속 응시. 응시 삭제 시 CASCADE DELETE */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attempt_id", nullable = false)
  private ExamAttempt attempt;

  /** 채점 대상 문항 ID */
  @Column(name = "question_id", length = 80, nullable = false)
  private String questionId;

  /** 획득 점수 */
  @Column(nullable = false, precision = 7, scale = 2)
  @Builder.Default
  private BigDecimal score = BigDecimal.ZERO;

  /** 만점 */
  @Column(name = "max_score", nullable = false, precision = 7, scale = 2)
  @Builder.Default
  private BigDecimal maxScore = BigDecimal.ZERO;

  /** 채점 방식 */
  @Enumerated(EnumType.STRING)
  @Column(name = "grading_mode", nullable = false, length = 30)
  private GradingMode gradingMode;

  /** 공개 피드백. GradingLeakGuard 검사 통과 후에만 저장하며 정답·해설·출처·answerKeySeal 문자열이 섞이면 LeakException이 난다. */
  @Column(columnDefinition = "TEXT")
  private String feedback;

  /**
   * AI 루브릭 기준별 점수 JSONB. RubricCriterion[] 직렬화 (criterion/score/max_score/reason). AI_RUBRIC 방식이
   * 아니면 null.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rubric_breakdown_json", columnDefinition = "jsonb")
  private String rubricBreakdownJson;

  /** AI 채점 신뢰도 (0.000~1.000). DETERMINISTIC이면 1.000 고정 */
  @Column(nullable = false, precision = 4, scale = 3)
  @Builder.Default
  private BigDecimal confidence = BigDecimal.ZERO;

  /** 수동 검토 필요 여부. true이면 상태가 MANUAL_REVIEW_REQUIRED */
  @Column(name = "needs_manual_review", nullable = false)
  @Builder.Default
  private Boolean needsManualReview = false;
}
