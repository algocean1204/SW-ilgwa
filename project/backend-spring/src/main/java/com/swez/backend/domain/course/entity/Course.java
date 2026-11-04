/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.exception.CourseErrorCode;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.global.exception.CustomException;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 과외 단위 (course 테이블). FastAPI 가 생성하고 Spring 이 조회한다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "course")
public class Course {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tutor_id")
  private Tutor tutor;

  @Column(nullable = false, length = 200)
  private String courseName;

  @Column(length = 100)
  private String subject;

  @Column(length = 20)
  private String sourceType;

  @Column(columnDefinition = "TEXT")
  private String sourcePdfUrl;

  @Column(columnDefinition = "TEXT")
  private String topicText;

  @Column(length = 20)
  private String mode;

  @Column(nullable = false)
  @Builder.Default
  private Integer totalChapters = 0;

  @Column(nullable = false)
  @Builder.Default
  private Integer completedSlides = 0;

  @Column(nullable = false, precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal progressPercent = BigDecimal.ZERO;

  @Column(nullable = false, length = 30)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private CourseStatus status = CourseStatus.CURRICULUM_GENERATING;

  private LocalDate examDate;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  private LocalDateTime completedAt;

  /** 과외 제목·과목·시험일 중 null 이 아닌 필드만 갱신한다. */
  public void updateInfo(String courseName, String subject, LocalDate examDate) {
    if (courseName != null) this.courseName = courseName;
    if (subject != null) this.subject = subject;
    if (examDate != null) this.examDate = examDate;
    this.updatedAt = LocalDateTime.now();
  }

  /** 커리큘럼을 확정한다. CURRICULUM_READY 상태에서만 ACTIVE 로 전환한다. */
  public void confirmCurriculum() {
    if (this.status != CourseStatus.CURRICULUM_READY) {
      throw new CustomException(CourseErrorCode.CRS_004);
    }
    this.status = CourseStatus.ACTIVE;
    this.updatedAt = LocalDateTime.now();
  }

  /** 소프트 삭제한다. 이미 DELETED 면 CRS_006 예외를 던진다. */
  public void softDelete() {
    if (this.status == CourseStatus.DELETED) {
      throw new CustomException(CourseErrorCode.CRS_006);
    }
    this.status = CourseStatus.DELETED;
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * CURRICULUM_GENERATING 상태를 CURRICULUM_FAILED로 바꾼다.
   *
   * <p>스케줄러와 서비스 양쪽에서 호출하며, 상태 검사 없이 단방향으로만 전이한다. 호출자
   * (CurriculumTimeoutScheduler)가 findStuckGeneratingCourses 조건으로 거르므로 READY·ACTIVE 등
   * 다른 상태에서는 호출되지 않는다.
   */
  public void markCurriculumFailed() {
    this.status = CourseStatus.CURRICULUM_FAILED;
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * 모든 레슨을 완료한 시점에 ACTIVE → COMPLETED 로 전이한다.
   *
   * <p>LessonService 가 "완료된 LessonProgress 수 == totalChapters" 조건을 확인한 뒤에만 호출한다.
   * 이미 COMPLETED 상태면 아무 일도 하지 않아, 네트워크 재시도나 중복 호출에도 completedAt 이 다시
   * 갱신되지 않는다. DELETED / CURRICULUM_FAILED 같은 상태에서 호출하지 않는 것은 호출자가 확인하므로
   * 여기서는 완강 여부만 보고 멱등 처리한다.
   */
  public void markCompleted() {
    // 이미 완강 상태면 중복 전이 없이 종료한다 (멱등)
    if (this.status == CourseStatus.COMPLETED) {
      return;
    }
    this.status = CourseStatus.COMPLETED;
    this.completedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }
}
