/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.swez.backend.domain.auth.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 제출물 엔티티(submission 테이블). 내부 API 로 생성하고 채점 콜백으로 결과를 받는다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "submission")
public class Submission {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assignment_id", nullable = false)
  private Assignment assignment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** queued / grading / done / failed */
  @Column(nullable = false, length = 20)
  @Builder.Default
  private String status = "queued";

  /** 과제 제출 시 사용자가 입력한 답안 (JSON) */
  @Column(columnDefinition = "TEXT")
  private String submittedAnswers;

  @Column(columnDefinition = "TEXT")
  private String officialFileUrl;

  @Column(columnDefinition = "TEXT")
  private String officialOcrText;

  private Integer officialScore;

  @Column(columnDefinition = "TEXT")
  private String officialFeedback;

  @Column(precision = 4, scale = 3)
  private BigDecimal officialAiConfidence;

  @Column(nullable = false)
  @Builder.Default
  private Integer ocrFailedPages = 0;

  private Integer totalPages;

  @Column(nullable = false)
  @Builder.Default
  private Boolean isRetry = false;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime submittedAt = LocalDateTime.now();

  private LocalDateTime gradedAt;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  /**
   * AI 채점 결과를 반영하고 상태를 전이한다.
   *
   * @param statusTransition 전이할 상태 (done/failed/grading)
   */
  public void applyAiResult(
      String statusTransition,
      String ocrText,
      Integer score,
      String feedback,
      BigDecimal aiConfidence,
      Integer ocrFailedPages,
      Integer totalPages) {
    this.status = statusTransition;
    this.officialOcrText = ocrText;
    this.officialScore = score;
    this.officialFeedback = feedback;
    this.officialAiConfidence = aiConfidence;
    if (ocrFailedPages != null) this.ocrFailedPages = ocrFailedPages;
    this.totalPages = totalPages;
    if ("done".equals(statusTransition) || "failed".equals(statusTransition)) {
      this.gradedAt = LocalDateTime.now();
    }
  }
}
