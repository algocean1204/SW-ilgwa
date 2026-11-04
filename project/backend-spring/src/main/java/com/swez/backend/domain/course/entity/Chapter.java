/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.entity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.swez.backend.domain.course.enums.ChapterStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 강의 단위 (chapter 테이블). API의 /lessons 가 DB의 chapter 에 해당한다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "chapter")
public class Chapter {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @Column(nullable = false, length = 200)
  private String chapterName;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private Integer orderIndex;

  @Column(nullable = false)
  @Builder.Default
  private Integer totalSlides = 0;

  @Column(nullable = false)
  @Builder.Default
  private Integer completedSlides = 0;

  private Integer estimatedMinutes;

  /** JSON 배열 문자열 (예: '["개념A","개념B"]'). 읽을 때 parseKeyTopics()로 파싱한다. */
  @Column(columnDefinition = "TEXT")
  private String keyTopics;

  @Column(nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private ChapterStatus status = ChapterStatus.LOCKED;

  @Column(nullable = false)
  @Builder.Default
  private Integer xpEarned = 0;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  private LocalDateTime completedAt;

  /**
   * keyTopics JSON 문자열을 List<String>으로 파싱한다.
   *
   * @return 주제 목록 (null이면 빈 리스트)
   */
  public List<String> parseKeyTopics() {
    if (keyTopics == null || keyTopics.isBlank()) return List.of();
    String trimmed = keyTopics.trim();
    if (!trimmed.startsWith("[")) return List.of();
    // 간단 파싱: ["A","B"] → [A, B]
    String inner = trimmed.substring(1, trimmed.length() - 1);
    if (inner.isBlank()) return List.of();
    return Arrays.stream(inner.split(","))
        .map(s -> s.trim().replaceAll("^\"|\"$", ""))
        .filter(s -> !s.isBlank())
        .toList();
  }
}
