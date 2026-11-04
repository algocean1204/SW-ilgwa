/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.entity;

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

/** 세션 단위 학습 시간 기록 (study_time_log 테이블). 리포트 통계는 이 테이블로 집계한다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "study_time_log")
public class StudyTimeLog {

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

  @Column(nullable = false, length = 20)
  private String activityType;

  @Column(length = 20)
  private String deviceType;

  @Column(nullable = false)
  private LocalDateTime startedAt;

  private LocalDateTime endedAt;

  @Column(nullable = false)
  @Builder.Default
  private Integer durationMinutes = 0;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
