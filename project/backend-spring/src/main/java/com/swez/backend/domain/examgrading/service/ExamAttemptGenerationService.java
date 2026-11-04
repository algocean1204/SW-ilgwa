/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import java.math.BigDecimal;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.examgrading.client.ExamForgeFastApiClient;
import com.swez.backend.domain.examgrading.dto.request.ExamGenerateRequest;
import com.swez.backend.domain.examgrading.dto.response.ExamGenerateStartResponse;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.enums.AttemptStatus;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모의고사 문항 비동기 생성 서비스.
 *
 * <p>처리 흐름:
 *
 * <ol>
 *   <li>attempt_id를 ULID prefix(eat_)로 생성한다.
 *   <li>ExamAttempt를 GENERATING 상태로 DB에 저장한다. exam_id는 FastAPI 콜백 수신 전이므로 "pending_{attemptId}"로
 *       초기화하고, 콜백에서 실제 값으로 갱신한다.
 *   <li>{@link ExamForgeFastApiClient#generateAsync()}로 FastAPI에 생성 트리거를 전송한다.
 *   <li>attempt_id를 즉시 반환하고, 클라이언트는 이 ID로 상태를 폴링한다.
 * </ol>
 *
 * <p>FastAPI 트리거가 실패하면 예외를 다시 던지지 않고 attempt를 FAILED로 바꾼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.fastapi",
    name = {"base-url", "api-key"})
public class ExamAttemptGenerationService {

  private final ExamAttemptRepository attemptRepository;
  private final UserRepository userRepository;
  private final CourseRepository courseRepository;
  private final ExamForgeFastApiClient fastApiClient;
  private final ExamAttemptGenerationFailureService failureService;

  /**
   * 모의고사 문항 생성을 시작한다.
   *
   * @param userId 요청자 userId (소유권 설정용)
   */
  @Transactional
  public ExamGenerateStartResponse startGeneration(ExamGenerateRequest request, String userId) {
    // 형식: eat_{ULID 26자}
    String attemptId = IdGenerator.examAttempt();

    // exam_id는 콜백에서 실제 값으로 바꾸기 전까지 placeholder로 둔다
    String examIdPlaceholder = "pending_" + attemptId;

    // getReferenceById로 SELECT 없이 FK만 바인딩한다
    User userRef = userRepository.getReferenceById(userId);

    Course courseRef = null;
    if (request.courseId() != null && !request.courseId().isBlank()) {
      courseRef = courseRepository.getReferenceById(request.courseId());
    }

    BigDecimal passPercentage =
        request.passPercentage() != null ? request.passPercentage() : BigDecimal.valueOf(60);

    ExamAttempt attempt =
        ExamAttempt.builder()
            .attemptId(attemptId)
            .examId(examIdPlaceholder)
            .user(userRef)
            .course(courseRef)
            .status(AttemptStatus.GENERATING)
            .passPercentage(passPercentage)
            .mockExamId(request.mockExamId())
            .build();

    attemptRepository.save(attempt);
    log.info(
        "[ExamAttemptGenerationService] attempt 생성 완료 | attemptId: {}, userId: {}",
        attemptId,
        userId);

    // FastAPI 생성 트리거는 attempt INSERT가 커밋된 이후(afterCommit)에 보낸다.
    // 커밋 전에 호출하면 FastAPI가 매우 빨리 생성을 끝내고 콜백을 보낼 때
    // Spring이 아직 attempt를 커밋하지 않아 ATTEMPT_NOT_FOUND(404) 레이스가 발생한다.
    // (AssignmentService.triggerGradingAfterCommit과 동일한 afterCommit 패턴)
    triggerGenerationAfterCommit(attemptId, request, passPercentage);

    log.info("[ExamAttemptGenerationService] 생성 트리거 예약 완료 | attemptId: {}", attemptId);
    return new ExamGenerateStartResponse(attemptId, AttemptStatus.GENERATING);
  }

  /**
   * attempt 저장 트랜잭션 커밋 후 FastAPI 생성 트리거를 전송한다. 트랜잭션 동기화가 없으면(테스트 등) 즉시 실행한다.
   *
   * <p>커밋 이후의 트리거 실패는 원 트랜잭션으로 롤백할 수 없으므로
   * {@link ExamAttemptGenerationFailureService}가 REQUIRES_NEW 새 트랜잭션으로 FAILED 전이를 영속한다.
   */
  private void triggerGenerationAfterCommit(
      String attemptId, ExamGenerateRequest request, BigDecimal passPercentage) {
    Runnable trigger =
        () -> {
          try {
            fastApiClient.generateAsync(
                attemptId,
                request.courseId(),
                request.subject(),
                request.topic(),
                request.questionCount(),
                // null은 빈 목록으로 바꾼다. 빈 목록은 FastAPI 유형 자동 분배(최종모의고사) 신호다
                request.questionTypes() != null ? request.questionTypes() : List.of(),
                request.difficulty(),
                passPercentage);
            log.info("[ExamAttemptGenerationService] 생성 트리거 완료 | attemptId: {}", attemptId);
          } catch (RuntimeException e) {
            // 커밋 이후 실패라 새 트랜잭션으로 FAILED 전이한다 (클라이언트는 폴링으로 FAILED를 감지한다).
            // CustomException 외의 런타임 오류도 같이 잡는다. 놓치면 attempt가 타임아웃
            // 스케줄러가 돌 때까지 GENERATING에 머물고, 예외가 커밋 단계 밖으로 나가
            // 이미 커밋된 요청이 500으로 응답된다.
            log.error(
                "[ExamAttemptGenerationService] 생성 트리거 실패 → FAILED 전이 | attemptId: {}",
                attemptId,
                e);
            failureService.markFailed(attemptId);
          }
        };

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      trigger.run();
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            trigger.run();
          }
        });
  }
}
