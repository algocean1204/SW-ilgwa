/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.admin.enums.AlertEventType;
import com.swez.backend.domain.admin.event.AlertEvent;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.repository.CourseRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CURRICULUM_GENERATING 상태에서 장기간 멈춘 코스를 CURRICULUM_FAILED로 전이한다.
 *
 * <p>FastAPI 백그라운드 태스크가 DB 연결 불가·AI 타임아웃 등으로 실패할 때 mark_course_curriculum_failed 도
 * DB 접근에 실패하면 FAILED 전이가 빠진다. Spring 쪽에서 한 번 더 확인해 코스가 GENERATING 에 계속 머물지 않게
 * 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurriculumTimeoutScheduler {

  private static final String LOG_PREFIX = "[CurriculumTimeoutScheduler]";

  private final CourseRepository courseRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 커리큘럼 생성 최대 허용 시간(분). application.yml 의 app.curriculum.timeout-minutes 값이며, 미설정 시
   * 10분이다.
   */
  @Value("${app.curriculum.timeout-minutes:10}")
  private int timeoutMinutes;

  /**
   * 5분마다 CURRICULUM_GENERATING 코스 중 timeout-minutes 를 넘긴 코스를 CURRICULUM_FAILED로 전이한다.
   *
   * <p>정상 완료된 코스는 FastAPI persist_curriculum 이 이미 CURRICULUM_READY 로 바꿔 두므로 이 스케줄러의
   * 대상이 아니다.
   */
  @Scheduled(fixedDelay = 5 * 60 * 1000)
  @Transactional
  public void timeoutStuckGeneratingCourses() {
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
    List<Course> stuck = courseRepository.findStuckGeneratingCourses(cutoff);

    if (stuck.isEmpty()) {
      return;
    }

    log.warn(
        "{} CURRICULUM_GENERATING timeout 코스 {}개 감지 (기준: {}분 초과)",
        LOG_PREFIX,
        stuck.size(),
        timeoutMinutes);

    for (Course course : stuck) {
      log.error(
          "{} 코스 FAILED 전이 — courseId: {}, createdAt: {}",
          LOG_PREFIX,
          course.getId(),
          course.getCreatedAt());
      course.markCurriculumFailed();
      // 관리자가 FastAPI 쪽 커리큘럼 생성 실패를 알 수 있게 알림 이벤트를 발행한다.
      eventPublisher.publishEvent(
          new AlertEvent(
              AlertEventType.CURRICULUM_GENERATION_FAILED, "courseId=" + course.getId()));
    }
  }
}
