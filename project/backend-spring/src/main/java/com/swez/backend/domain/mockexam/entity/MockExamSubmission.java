/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.entity;

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

/** 모의고사 제출 및 채점 결과 (mock_exam_submission 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "mock_exam_submission")
public class MockExamSubmission {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "mock_exam_id", nullable = false)
  private MockExam mockExam;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** 제출한 답안 목록 (JSON) */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String answers;

  private Integer score;

  @Column(name = "total_points")
  private Integer totalPoints;

  @Column(name = "time_taken")
  private Integer timeTaken;

  @Column(length = 5)
  private String grade;

  /** strongTopics, weakTopics, recommendation (JSON) */
  @Column(columnDefinition = "TEXT")
  private String analysis;

  @Column(name = "submitted_at", nullable = false)
  @Builder.Default
  private LocalDateTime submittedAt = LocalDateTime.now();
}
