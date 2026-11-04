/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.entity;

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
import com.swez.backend.domain.course.entity.Course;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 노트 (note 테이블). Spring은 user_memo만 만들고 ai_auto는 FastAPI만 만든다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "note")
public class Note {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chapter_id")
  private Chapter chapter;

  /** Spring POST API는 user_memo만 허용한다. ai_auto는 FastAPI만 쓴다. */
  @Column(nullable = false, length = 20)
  @Builder.Default
  private String type = "user_memo";

  @Column(length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  private Integer slideIndex;

  @Column(columnDefinition = "NUMERIC(10,2)")
  private Double timestampSec;

  @Column(nullable = false)
  @Builder.Default
  private Boolean isStarred = false;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  /** 별표 상태를 토글한다. */
  public void toggleStar() {
    this.isStarred = !this.isStarred;
    this.updatedAt = LocalDateTime.now();
  }

  /** 노트 제목을 변경한다. */
  public void updateTitle(String title) {
    this.title = title;
    this.updatedAt = LocalDateTime.now();
  }

  /** 노트 본문을 변경한다. */
  public void updateContent(String content) {
    this.content = content;
    this.updatedAt = LocalDateTime.now();
  }
}
