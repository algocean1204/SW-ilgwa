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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 모의고사 개별 문항 (mock_exam_question 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "mock_exam_question")
public class MockExamQuestion {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "mock_exam_id", nullable = false)
  private MockExam mockExam;

  @Column(name = "question_idx", nullable = false)
  private Integer questionIdx;

  @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
  private String questionText;

  @Column(name = "question_type", nullable = false, length = 20)
  @Builder.Default
  private String questionType = "multiple_choice";

  /** 선택지 목록 (JSON 배열 문자열) */
  @Column(columnDefinition = "TEXT")
  private String options;

  /** 0-based 인덱스 (options 배열의 인덱스) */
  @Column(name = "correct_option", length = 10)
  private String correctOption;

  @Column(columnDefinition = "TEXT")
  private String explanation;

  @Column(nullable = false)
  @Builder.Default
  private Integer points = 5;

  @Column(name = "created_at", nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
