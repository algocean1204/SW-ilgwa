/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.admin.enums.AlertEventType;
import com.swez.backend.domain.admin.event.AlertEvent;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GENERATING 상태에서 장기간 멈춘 응시(ExamAttempt)를 FAILED로 전이한다.
 *
 * <p>FastAPI 문항 생성이 크래시·네트워크 단절 등으로 실패하면 생성 완료/실패 콜백이 영영 도착하지 않아 attempt가 GENERATING으로 영구
 * 고착되고, 프론트는 무한 폴링에 빠진다(프론트는 FAILED를 이미 처리한다). 트리거 전송 자체가 실패한 경우는 {@link
 * ExamAttemptGenerationFailureService}가 즉시 FAILED로 전이한다. 트리거 성공 후 콜백이 유실된 경우는 거기서 잡지 못해 이 스케줄러가
 * 처리한다. CurriculumTimeoutScheduler와 같은 방식이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamAttemptTimeoutScheduler {

  private static final String LOG_PREFIX = "[ExamAttemptTimeoutScheduler]";

  private final ExamAttemptRepository attemptRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 문항 생성 최대 허용 시간(분). application.yml의 app.examgrading.generation-timeout-minutes 값을 사용하며, 미설정 시
   * 15분을 기본값으로 쓴다.
   *
   * <p>기본값 근거: FastAPI 생성 파이프라인 타임아웃(MOCK_EXAM_PIPELINE_TIMEOUT_SEC 기본 750초=12.5분) +
   * 콜백 재시도(~15초)보다 길어야, 정상적으로 느리게 완료 중인 생성을 잘못 FAILED 처리하고 뒤늦게 도착한 성공
   * 콜백을 409로 거부하는 레이스가 생기지 않는다.
   */
  @Value("${app.examgrading.generation-timeout-minutes:15}")
  private int timeoutMinutes;

  /**
   * 5분마다 GENERATING 응시 중 generation-timeout-minutes를 초과한 응시를 FAILED로 전이한다.
   *
   * <p>정상 완료 경로는 FastAPI 콜백(ExamAttemptGenerationCallbackService)이 READY/FAILED로 직접 전이하므로 이 스케줄러가
   * 실행될 때 정상 완료된 응시는 이미 GENERATING이 아니다.
   */
  @Scheduled(fixedDelay = 5 * 60 * 1000) // 5분 간격
  @Transactional
  public void timeoutStuckGeneratingAttempts() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(timeoutMinutes);
    List<ExamAttempt> stuck = attemptRepository.findStuckGeneratingAttempts(cutoff);

    if (stuck.isEmpty()) {
      return;
    }

    log.warn(
        "{} GENERATING timeout 응시 {}개 감지 (기준: {}분 초과)", LOG_PREFIX, stuck.size(), timeoutMinutes);

    for (ExamAttempt attempt : stuck) {
      // 사유 코드 없는 markFailed()를 쓰므로 실패 사유는 로그로 남긴다
      log.error(
          "{} 응시 FAILED 전이 — attemptId: {}, createdAt: {}, 사유: FastAPI 생성 콜백 미수신 (timeout {}분 초과)",
          LOG_PREFIX,
          attempt.getAttemptId(),
          attempt.getCreatedAt(),
          timeoutMinutes);
      attempt.markFailed();
      // 관리자가 FastAPI 측 실패를 알 수 있게 문항 생성 실패 알림 이벤트를 발행한다.
      eventPublisher.publishEvent(
          new AlertEvent(
              AlertEventType.EXAM_GENERATION_FAILED, "attemptId=" + attempt.getAttemptId()));
    }
  }
}
