/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.calendar.converter.CalendarEventStatusConverter;
import com.swez.backend.domain.calendar.enums.CalendarEventStatus;
import com.swez.backend.domain.calendar.enums.CalendarEventType;
import com.swez.backend.domain.course.entity.Course;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** calendar_event 테이블 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "calendar_event")
public class CalendarEvent {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private CalendarEventType type;

  @Column(nullable = false)
  private LocalDate eventDate;

  private LocalTime scheduledTime;

  private Integer durationMin;

  @Column(nullable = false, length = 20)
  @Convert(converter = CalendarEventStatusConverter.class)
  @Builder.Default
  private CalendarEventStatus status = CalendarEventStatus.SCHEDULED;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id")
  private Course course;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  /** 이벤트 정보를 부분 수정한다. null 필드는 기존 값을 유지한다. */
  public void update(
      String title,
      LocalDate eventDate,
      LocalTime scheduledTime,
      Integer durationMin,
      CalendarEventStatus status) {
    if (title != null) this.title = title;
    if (eventDate != null) this.eventDate = eventDate;
    if (scheduledTime != null) this.scheduledTime = scheduledTime;
    if (durationMin != null) this.durationMin = durationMin;
    if (status != null) this.status = status;
    this.updatedAt = LocalDateTime.now();
  }
}
