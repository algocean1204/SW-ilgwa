/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.mapper;

import org.springframework.stereotype.Component;

import com.swez.backend.domain.calendar.dto.response.CalendarEventResponse;
import com.swez.backend.domain.calendar.dto.response.SubmissionResponse;
import com.swez.backend.domain.calendar.entity.CalendarEvent;
import com.swez.backend.domain.calendar.entity.Submission;

/** 캘린더·내부 도메인 엔티티와 DTO 를 변환한다. */
@Component
public class CalendarMapper {

  /** CalendarEvent 엔티티를 CalendarEventResponse 로 변환한다. */
  public CalendarEventResponse toEventResponse(CalendarEvent event) {
    return CalendarEventResponse.builder()
        .id(event.getId())
        .title(event.getTitle())
        .type(event.getType())
        .date(event.getEventDate())
        .status(event.getStatus())
        .scheduledTime(event.getScheduledTime())
        .durationMin(event.getDurationMin())
        .courseId(event.getCourse() != null ? event.getCourse().getId() : null)
        .build();
  }

  /** Submission 엔티티를 SubmissionResponse 로 변환한다. */
  public SubmissionResponse toSubmissionResponse(Submission submission) {
    return SubmissionResponse.builder()
        .id(submission.getId())
        .assignmentId(submission.getAssignment().getId())
        .status(submission.getStatus())
        .createdAt(submission.getCreatedAt())
        .build();
  }
}
