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

import com.swez.backend.domain.course.entity.Chapter;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 슬라이드 단위 (slide 테이블). FastAPI가 생성하고 Spring은 조회만 한다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "slide")
public class Slide {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chapter_id", nullable = false)
  private Chapter chapter;

  @Column(nullable = false)
  private Integer slideIdx;

  @Column(length = 200)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String content;

  @Column(columnDefinition = "TEXT")
  private String audioUrl;

  @Column(precision = 8, scale = 1)
  @Builder.Default
  private BigDecimal durationSec = BigDecimal.ZERO;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
