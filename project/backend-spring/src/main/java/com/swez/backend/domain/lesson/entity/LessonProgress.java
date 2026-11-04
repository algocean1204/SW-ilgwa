/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.entity;

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
import com.swez.backend.domain.course.entity.Chapter;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 강의 진행률 (lesson_progress 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "lesson_progress")
public class LessonProgress {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chapter_id", nullable = false)
  private Chapter chapter;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  @Builder.Default
  private Integer currentSlideIdx = 0;

  @Column(nullable = false, precision = 10, scale = 1)
  @Builder.Default
  private BigDecimal playbackSec = BigDecimal.ZERO;

  @Column(nullable = false)
  @Builder.Default
  private Boolean isCompleted = false;

  private LocalDateTime completedAt;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  /** 진행률을 갱신한다. isCompleted=true면 completedAt을 현재 시각으로 설정한다. */
  public void updateProgress(int currentSlideIdx, BigDecimal playbackSec, boolean isCompleted) {
    this.currentSlideIdx = currentSlideIdx;
    this.playbackSec = playbackSec;
    this.isCompleted = isCompleted;
    this.updatedAt = LocalDateTime.now();
    if (isCompleted && this.completedAt == null) {
      this.completedAt = LocalDateTime.now();
    }
  }
}
