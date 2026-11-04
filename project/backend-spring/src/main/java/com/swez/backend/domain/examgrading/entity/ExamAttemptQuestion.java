/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * exam_attempt_question 테이블. 응시 시점의 서버 스냅샷 문항.
 *
 * <p>FastAPI가 생성한 시험지를 Spring이 DB에 그대로 보존한다.
 * correct_answer·explanation·source_reference·grading_metadata_json은 채점에만 사용하며 절대 프론트 응답 DTO에 매핑하지
 * 않는다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "exam_attempt_question")
public class ExamAttemptQuestion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 소속 응시. 응시 삭제 시 CASCADE DELETE */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attempt_id", nullable = false)
  private ExamAttempt attempt;

  /** FastAPI가 발급한 문항 ID */
  @Column(name = "question_id", length = 80, nullable = false)
  private String questionId;

  /** 문항 유형 템플릿 ID (객관식·서술형·순서형 등 구분) */
  @Column(name = "template_id", length = 80)
  private String templateId;

  /** 프론트 표시 순서 */
  @Column(name = "display_order", nullable = false)
  @Builder.Default
  private Integer displayOrder = 0;

  /** 문항 본문 */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String stem;

  /**
   * 보기 목록 JSONB. QuestionOption[] 직렬화 (label/text/is_correct). is_correct 값이 포함되어 있으므로 프론트 DTO 변환
   * 시 반드시 제거한다.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "options_json", columnDefinition = "jsonb")
  private String optionsJson;

  /** 배점 */
  @Column(nullable = false, precision = 7, scale = 2)
  @Builder.Default
  private BigDecimal points = BigDecimal.ONE;

  /** 서버 저장 정답. 프론트 응답 DTO에 매핑하지 않으며 GradingLeakGuard가 누수를 감시한다. */
  @Column(name = "correct_answer", columnDefinition = "TEXT")
  private String correctAnswer;

  /** 해설. 프론트 응답 DTO에 매핑하지 않으며 GradingLeakGuard가 누수를 감시한다. */
  @Column(columnDefinition = "TEXT")
  private String explanation;

  /** 출처. 프론트 응답 DTO에 매핑하지 않으며 GradingLeakGuard가 누수를 감시한다. */
  @Column(name = "source_reference", columnDefinition = "TEXT")
  private String sourceReference;

  /** 추가 채점 메타데이터 JSONB. 연결형 우항·순서형 정답 등 문항 유형별 정보이며 프론트 응답 DTO에 매핑하지 않는다. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "grading_metadata_json", columnDefinition = "jsonb")
  private String gradingMetadataJson;
}
