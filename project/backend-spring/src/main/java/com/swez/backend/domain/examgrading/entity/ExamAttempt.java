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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.examgrading.enums.AttemptStatus;
import com.swez.backend.domain.examgrading.enums.GenerationFailureReason;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * exam_attempt 테이블의 응시 단위 엔티티. 생성 시점부터 채점 완료(또는 수동 검토 대기)까지의 수명주기를 관리한다. answer_key_seal은 FastAPI가
 * 서명한 정답지 봉인값으로 절대 프론트 DTO에 매핑하지 않는다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "exam_attempt")
public class ExamAttempt {

  /** 응시 식별자. 생성 방식이 정해지지 않아 길이 제약만 둔다. */
  @Id
  @Column(name = "attempt_id", length = 80)
  private String attemptId;

  /** FastAPI가 발급한 시험 ID */
  @Column(name = "exam_id", length = 80, nullable = false)
  private String examId;

  /** 응시한 사용자. 탈퇴 시 CASCADE DELETE */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** 소속 과외. 독립 응시(과외 없음)인 경우 null. 과외 삭제 시 SET NULL 처리. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id")
  private Course course;

  /** 응시 수명주기 상태 */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  @Builder.Default
  private AttemptStatus status = AttemptStatus.CREATED;

  /** 합격 기준 점수 비율 (0.00~100.00) */
  @Column(name = "pass_percentage", nullable = false, precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal passPercentage = BigDecimal.ZERO;

  /**
   * 연결된 모의고사 카드 ID (nullable). V1의 mock_exam.id (VARCHAR 30, mex_ 접두사). 독립 응시(모의고사 카드 없이 생성)인 경우
   * null. 모의고사 삭제 시 SET NULL 처리.
   */
  @Column(name = "mock_exam_id", length = 30)
  private String mockExamId;

  /** FastAPI 생성 시 서명된 정답지 seal. 채점 시 무결성 검증에만 사용하며 절대 프론트 응답에 포함하지 않는다. */
  @Column(name = "answer_key_seal", columnDefinition = "TEXT")
  private String answerKeySeal;

  /**
   * 사용자가 요청한 문항 수 (nullable). FastAPI 생성 완료 콜백에서 보존한다. 프론트가 "요청 N / 생성 M" 표기에 사용한다. 레거시
   * 콜백(필드 미전송)이나 미생성 상태에서는 null.
   */
  @Column(name = "requested_question_count")
  private Integer requestedQuestionCount;

  /**
   * 실제 생성된 문항 수 (nullable). 부분 출고(partial_success) 시 요청보다 적을 수 있다. 프론트가 "요청 N / 생성 M" 표기에
   * 사용한다. 레거시 콜백이나 미생성 상태에서는 null.
   */
  @Column(name = "actual_question_count")
  private Integer actualQuestionCount;

  /**
   * FAILED 사유 구분 코드 (nullable). needs_more_source(자료 부족)와 진짜 에러를 구분한다. 프론트는 status=FAILED +
   * 이 코드로 안내 문구·행동 제안을 분기한다. FAILED가 아니거나 사유 미상이면 null.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "failure_reason", length = 40)
  private GenerationFailureReason failureReason;

  @Column(name = "created_at", nullable = false)
  @Builder.Default
  private OffsetDateTime createdAt = OffsetDateTime.now();

  /** 제출 완료 시각. 제출 전이면 null */
  @Column(name = "submitted_at")
  private OffsetDateTime submittedAt;

  /** 채점 완료 시각. 채점 전이면 null */
  @Column(name = "graded_at")
  private OffsetDateTime gradedAt;

  // 상태 전환 메서드

  /** 응시를 시작한다 (CREATED → IN_PROGRESS). */
  public void startAttempt() {
    this.status = AttemptStatus.IN_PROGRESS;
  }

  /** 제출 시각을 기록하고 제출 완료로 전환한다 (IN_PROGRESS → SUBMITTED). */
  public void markSubmitted(OffsetDateTime submittedAt) {
    this.submittedAt = submittedAt;
    this.status = AttemptStatus.SUBMITTED;
  }

  /** 채점 시각을 기록하고 채점 완료로 전환한다 (SUBMITTED → GRADED). */
  public void markGraded(OffsetDateTime gradedAt) {
    this.gradedAt = gradedAt;
    this.status = AttemptStatus.GRADED;
  }

  /** 수동 검토 필요 상태로 전환 */
  public void markManualReviewRequired() {
    this.status = AttemptStatus.MANUAL_REVIEW_REQUIRED;
  }

  /** 문항 비동기 생성을 시작한다 (CREATED → GENERATING). */
  public void markGenerating() {
    this.status = AttemptStatus.GENERATING;
  }

  /** 문항 생성을 완료하고 answer_key_seal을 저장한다 (GENERATING → READY). */
  public void markReady(String answerKeySeal) {
    this.answerKeySeal = answerKeySeal;
    this.status = AttemptStatus.READY;
  }

  /**
   * 문항 생성을 완료하고 봉인값과 요청/생성 문항 수를 저장한다 (GENERATING → READY).
   *
   * <p>부분 출고(partial_success)에서도 정상 READY로 전이한다. 생성 문항 수가 요청보다 적어도 출고를 막지 않으며, 프론트가
   * "요청 N / 생성 M"을 표기할 수 있도록 두 카운트를 함께 저장한다.
   *
   * @param requestedQuestionCount 사용자가 요청한 문항 수 (nullable)
   * @param actualQuestionCount 실제 생성된 문항 수 (nullable)
   */
  public void markReady(
      String answerKeySeal, Integer requestedQuestionCount, Integer actualQuestionCount) {
    this.answerKeySeal = answerKeySeal;
    this.requestedQuestionCount = requestedQuestionCount;
    this.actualQuestionCount = actualQuestionCount;
    this.status = AttemptStatus.READY;
  }

  /**
   * 문항 생성 실패로 전환한다 (GENERATING → FAILED).
   *
   * <p>상태만 전환하며 실패 사유는 별도 로그/서비스 레이어에서 관리한다. (사유 코드 없이 호출되는 레거시/타임아웃 경로용)
   */
  public void markFailed() {
    this.status = AttemptStatus.FAILED;
  }

  /**
   * 문항 생성 실패로 전환하고 사유 코드와 요청/생성 문항 수를 저장한다 (GENERATING → FAILED).
   *
   * <p>needs_more_source(자료 부족)와 진짜 에러를 사유 코드로 구분 저장한다. 상태는 동일하게 FAILED지만, 프론트는
   * failureReason으로 안내 문구·행동 제안을 분기할 수 있다.
   *
   * @param reason 실패 사유 구분 코드 (nullable이면 사유 미상)
   * @param requestedQuestionCount 사용자가 요청한 문항 수 (nullable)
   * @param actualQuestionCount 실제 생성된 문항 수 (nullable)
   */
  public void markFailed(
      GenerationFailureReason reason,
      Integer requestedQuestionCount,
      Integer actualQuestionCount) {
    this.failureReason = reason;
    this.requestedQuestionCount = requestedQuestionCount;
    this.actualQuestionCount = actualQuestionCount;
    this.status = AttemptStatus.FAILED;
  }

  /**
   * 생성 콜백 수신 시 FastAPI가 발급한 실제 examId로 갱신한다.
   *
   * <p>초기 저장 시 "pending_{attemptId}" placeholder로 저장되었던 값을 실제 시험 ID로 교체한다.
   */
  public void updateExamId(String examId) {
    this.examId = examId;
  }
}
