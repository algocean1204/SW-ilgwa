/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.entity;

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

/** 퀴즈 답변 기록 (quiz_answer 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "quiz_answer")
public class QuizAnswer {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private Integer selectedOption;

  @Column(nullable = false)
  private Boolean isCorrect;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime answeredAt = LocalDateTime.now();
}
