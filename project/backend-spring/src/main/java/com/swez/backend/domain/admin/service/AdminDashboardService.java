/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.admin.dto.response.AdminDashboardResponse;
import com.swez.backend.domain.admin.repository.SocraticSessionRepository;
import com.swez.backend.domain.auth.enums.UserStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.calendar.repository.AssignmentRepository;
import com.swez.backend.domain.calendar.repository.SubmissionRepository;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.repository.CourseRepository;

import lombok.RequiredArgsConstructor;

/** 관리자 대시보드 KPI를 산출하는 서비스 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

  private final UserRepository userRepository;
  private final SubmissionRepository submissionRepository;
  private final AssignmentRepository assignmentRepository;
  private final CourseRepository courseRepository;
  private final SocraticSessionRepository socraticSessionRepository;

  /** 대시보드 KPI 4개를 한 번에 조회한다 */
  public AdminDashboardResponse getDashboard() {
    int totalUsers = (int) userRepository.countByStatus(UserStatus.ACTIVE);
    double weeklySubmissionRate = calculateWeeklySubmissionRate();
    double averageProgress = courseRepository.findAverageProgressByStatus(CourseStatus.ACTIVE);
    int unansweredCount = (int) socraticSessionRepository.countByTranscriptIsNull();

    return new AdminDashboardResponse(
        totalUsers, weeklySubmissionRate, averageProgress, unansweredCount);
  }

  /** 이번 주(월요일 00:00 ~ 현재) 제출률을 계산한다. 마감 과제가 없으면 0.0을 반환한다. */
  private double calculateWeeklySubmissionRate() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime weekStart =
        now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(LocalTime.MIN);
    /* 주간 마감 범위는 월요일~일요일 자정 */
    LocalDateTime weekEnd = weekStart.plusDays(7);

    long assignmentsDue = assignmentRepository.countByDueDateBetween(weekStart, weekEnd);
    if (assignmentsDue == 0) {
      return 0.0;
    }
    long submissionsThisWeek = submissionRepository.countBySubmittedAtBetween(weekStart, now);
    return Math.min(1.0, (double) submissionsThisWeek / assignmentsDue);
  }
}
