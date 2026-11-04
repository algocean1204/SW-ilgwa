/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.entity;

import java.time.LocalDateTime;

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
import com.swez.backend.domain.mockexam.enums.MockExamStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AI 모의고사 단위 (mock_exam 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "mock_exam")
public class MockExam {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(name = "exam_type", nullable = false, length = 20)
  private String examType;

  @Column(name = "question_count", nullable = false)
  private Integer questionCount;

  @Column(nullable = false, length = 20)
  private String difficulty;

  @Column(name = "time_limit")
  private Integer timeLimit;

  @Column(nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private MockExamStatus status = MockExamStatus.GENERATING;

  @Column(name = "total_points")
  private Integer totalPoints;

  @Column(name = "created_at", nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  /** 상태를 변경한다. */
  public void updateStatus(MockExamStatus status) {
    this.status = status;
  }
}
