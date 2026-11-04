/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.examgrading.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 생성 트리거 실패 시 attempt를 FAILED로 전환하는 독립 트랜잭션 서비스.
 *
 * <p>FastAPI 생성 트리거가 afterCommit(원 트랜잭션 커밋 이후)에서 실패하면 원 트랜잭션으로는 상태를 되돌릴 수
 * 없으므로, REQUIRES_NEW 새 트랜잭션으로 FAILED 전이를 영속한다. 별도 빈으로 분리한 이유는 자기 호출
 * (self-invocation) 시 @Transactional 프록시가 동작하지 않기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAttemptGenerationFailureService {

  private final ExamAttemptRepository attemptRepository;

  /** attempt를 FAILED로 전환한다 (새 트랜잭션). */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(String attemptId) {
    ExamAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
    if (attempt == null) {
      log.error(
          "[ExamAttemptGenerationFailureService] FAILED 전환 대상 attempt 없음 | attemptId: {}",
          attemptId);
      return;
    }
    attempt.markFailed();
    attemptRepository.save(attempt);
    log.warn(
        "[ExamAttemptGenerationFailureService] FastAPI 트리거 실패 → attempt FAILED 전환 | attemptId: {}",
        attemptId);
  }
}
