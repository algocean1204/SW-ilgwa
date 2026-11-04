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

import com.swez.backend.domain.course.entity.Chapter;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이해도 체크 퀴즈 (quiz 테이블). FastAPI가 생성하고 Spring은 조회·채점한다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "quiz")
public class Quiz {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chapter_id", nullable = false)
  private Chapter chapter;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "slide_id", nullable = false)
  private Slide slide;

  @Column(nullable = false)
  private Integer quizIdx;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String questionText;

  /** JSON 배열 문자열 (예: '["보기1","보기2","보기3","보기4"]') */
  @Column(columnDefinition = "TEXT")
  private String options;

  /** 0-based 인덱스 (options 배열의 인덱스) */
  @Column(nullable = false)
  private Integer correctOption;

  @Column(columnDefinition = "TEXT")
  private String explanation;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
