/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.entity;

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

/** 과제 단위 엔티티(assignment 테이블). */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "assignment")
public class Assignment {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id")
  private Course course;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chapter_id")
  private Chapter chapter;

  /** active / completed (과제 자체 상태. queued/grading/done/failed는 submission 몫) */
  @Column(nullable = false, length = 20)
  @Builder.Default
  private String status = "active";

  /** 과제 제목 */
  @Column(length = 200)
  private String title;

  /** 과제 설명 */
  @Column(columnDefinition = "TEXT")
  private String description;

  /** 마감일 */
  private LocalDateTime dueDate;

  /** 총 문제 수 */
  @Column(nullable = false)
  @Builder.Default
  private int totalQuestions = 0;

  /** 문제 목록 (JSON 배열 문자열) */
  @Column(columnDefinition = "TEXT")
  private String questions;

  /** 최대 점수 */
  private Integer maxScore;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  /** 과제 채점 완료 상태로 전이한다. 이미 완료된 과제에는 부작용 없이 반환한다. */
  public void complete() {
    if ("completed".equals(this.status)) {
      return;
    }
    this.status = "completed";
    this.updatedAt = LocalDateTime.now();
  }
}
