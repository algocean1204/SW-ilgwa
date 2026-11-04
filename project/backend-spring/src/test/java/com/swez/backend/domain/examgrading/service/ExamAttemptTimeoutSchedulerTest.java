/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.swez.backend.domain.admin.enums.AlertEventType;
import com.swez.backend.domain.admin.event.AlertEvent;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.enums.AttemptStatus;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;

/**
 * ExamAttemptTimeoutScheduler 단위 테스트.
 *
 * <p>FastAPI 생성 콜백 유실로 GENERATING에 고착된 응시를 안전망 스케줄러가 FAILED로 전이하는지 검증한다:
 *
 * <ul>
 *   <li>timeout 초과 GENERATING 응시 → FAILED 전이 + 관리자 알림 이벤트 발행
 *   <li>고착 응시 없음 → 아무 동작 없음 (이벤트 발행 없음)
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExamAttemptTimeoutSchedulerTest {

  @Mock private ExamAttemptRepository attemptRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private ExamAttemptTimeoutScheduler scheduler;

  private static final String ATTEMPT_ID = "eat_stuck_001";

  @BeforeEach
  void setUp() {
    scheduler = new ExamAttemptTimeoutScheduler(attemptRepository, eventPublisher);
    // @Value 대신 application.yml 기본값과 같은 15분을 넣는다
    // (FastAPI 파이프라인 타임아웃 750초=12.5분 + 콜백 재시도보다 길어야 한다)
    ReflectionTestUtils.setField(scheduler, "timeoutMinutes", 15);
  }

  /** timeout을 넘겨 GENERATING에 머문 응시 시드 (20분 전 생성) */
  private ExamAttempt stuckAttempt() {
    return ExamAttempt.builder()
        .attemptId(ATTEMPT_ID)
        .examId("pending_" + ATTEMPT_ID)
        .passPercentage(BigDecimal.valueOf(60))
        .status(AttemptStatus.GENERATING)
        .createdAt(OffsetDateTime.now().minusMinutes(20))
        .build();
  }

  @Test
  @DisplayName("timeout 초과 GENERATING 응시 — FAILED 전이 + 관리자 알림 이벤트 발행")
  void timeoutStuckAttemptTransitionsToFailed() {
    ExamAttempt stuck = stuckAttempt();
    when(attemptRepository.findStuckGeneratingAttempts(any(OffsetDateTime.class)))
        .thenReturn(List.of(stuck));

    scheduler.timeoutStuckGeneratingAttempts();

    // 프론트 폴링이 실패를 알 수 있게 상태가 FAILED로 바뀌어야 한다
    assertThat(stuck.getStatus()).isEqualTo(AttemptStatus.FAILED);

    // 관리자 알림 이벤트: EXAM_GENERATION_FAILED 유형과 attemptId 상세
    ArgumentCaptor<AlertEvent> eventCaptor = ArgumentCaptor.forClass(AlertEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    AlertEvent published = eventCaptor.getValue();
    assertThat(published.eventType()).isEqualTo(AlertEventType.EXAM_GENERATION_FAILED);
    assertThat(published.detail()).contains(ATTEMPT_ID);
  }

  @Test
  @DisplayName("고착 응시 없음 — 상태 전이·이벤트 발행 없이 조용히 종료")
  void noStuckAttemptsDoesNothing() {
    when(attemptRepository.findStuckGeneratingAttempts(any(OffsetDateTime.class)))
        .thenReturn(List.of());

    scheduler.timeoutStuckGeneratingAttempts();

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("고착 응시 여러 개 — 전부 FAILED 전이, 응시별 이벤트 발행")
  void multipleStuckAttemptsAllTransitioned() {
    ExamAttempt first = stuckAttempt();
    ExamAttempt second =
        ExamAttempt.builder()
            .attemptId("eat_stuck_002")
            .examId("pending_eat_stuck_002")
            .passPercentage(BigDecimal.valueOf(60))
            .status(AttemptStatus.GENERATING)
            .createdAt(OffsetDateTime.now().minusMinutes(30))
            .build();
    when(attemptRepository.findStuckGeneratingAttempts(any(OffsetDateTime.class)))
        .thenReturn(List.of(first, second));

    scheduler.timeoutStuckGeneratingAttempts();

    assertThat(first.getStatus()).isEqualTo(AttemptStatus.FAILED);
    assertThat(second.getStatus()).isEqualTo(AttemptStatus.FAILED);
    verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(any(AlertEvent.class));
  }
}
