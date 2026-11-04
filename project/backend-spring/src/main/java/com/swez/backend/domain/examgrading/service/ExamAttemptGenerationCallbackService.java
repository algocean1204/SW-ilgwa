/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.examgrading.dto.request.GenerationCallbackQuestionRequest;
import com.swez.backend.domain.examgrading.dto.request.GenerationCompletedCallbackRequest;
import com.swez.backend.domain.examgrading.dto.request.GenerationFailedCallbackRequest;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion;
import com.swez.backend.domain.examgrading.enums.AttemptStatus;
import com.swez.backend.domain.examgrading.enums.GenerationFailureReason;
import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.mapper.ExamGradingPersistenceMapper;
import com.swez.backend.domain.examgrading.repository.ExamAttemptQuestionRepository;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;
import com.swez.backend.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FastAPI 문항 생성 콜백 수신 서비스.
 *
 * <p>과제채점 콜백({@code InternalService})과 엔드포인트·DTO·서비스를 모두 따로 둔다.
 *
 * <h3>생성 완료 콜백 처리 흐름</h3>
 *
 * <ol>
 *   <li>attempt_id로 ExamAttempt를 조회하고, 없으면 ATTEMPT_NOT_FOUND 예외를 던진다.
 *   <li>attempt 상태가 GENERATING이 아니면 ATTEMPT_NOT_IN_GENERATING_STATUS(409) 예외 (중복 콜백 차단).
 *   <li>generationOutcome에 따라 분기한다:
 *       <ul>
 *         <li>passed / partial_success / null(레거시): questions[]를 영속하고 READY로 전이.
 *             부분 출고(요청보다 적은 문항)도 정상 출고한다. 요청/생성 문항 수를 attempt에 보존.
 *         <li>needs_more_source: 자료 부족 비-에러 실패. FAILED로 두되 failure_reason을
 *             NEEDS_MORE_SOURCE로 구분 저장한다(진짜 에러와 구분). 프론트는 안내+행동 제안을 띄운다.
 *         <li>failed 또는 questions 비어 있음: 진짜 실패. FAILED + GENERATION_ERROR.
 *       </ul>
 * </ol>
 *
 * <p>needs_more_source는 AttemptStatus enum 값을 늘리지 않고 failure_reason 사유 코드로 처리한다. enum을 추가하면
 * DB CHECK constraint·제출 가능 확인·폴링 응답 등을 함께 손대고 마이그레이션으로 상태값을 재정의해야 한다. 사유 코드는
 * nullable 컬럼 하나만 추가하면 되고 기존 FAILED 흐름(제출 차단·폴링)을 그대로 쓴다.
 *
 * <h3>생성 실패 콜백 처리 흐름</h3>
 *
 * <ol>
 *   <li>attempt_id로 ExamAttempt를 조회하고, 없으면 ATTEMPT_NOT_FOUND 예외를 던진다.
 *   <li>attempt 상태가 GENERATING이 아니면 ATTEMPT_NOT_IN_GENERATING_STATUS(409) 예외.
 *   <li>attempt.markFailed()로 FAILED 상태 전환.
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAttemptGenerationCallbackService {

  private final ExamAttemptRepository attemptRepository;
  private final ExamAttemptQuestionRepository questionRepository;
  private final ExamGradingPersistenceMapper persistenceMapper;

  /** 자료 부족(비-에러) 실패를 뜻하는 generationOutcome 값. */
  private static final String OUTCOME_NEEDS_MORE_SOURCE = "needs_more_source";

  /** 진짜 실패를 뜻하는 generationOutcome 값. */
  private static final String OUTCOME_FAILED = "failed";

  /**
   * 생성 완료 콜백을 받아 generationOutcome에 따라 READY 또는 FAILED(사유 코드 포함)로 전이한다.
   *
   * <p>passed/partial_success/null(레거시)은 questions를 영속하고 READY로 전이한다(부분 출고 포함).
   * needs_more_source는 자료 부족 비-에러 실패로 FAILED + NEEDS_MORE_SOURCE 사유를 저장한다. failed 또는
   * questions가 비어 있으면 진짜 실패로 FAILED + GENERATION_ERROR를 저장한다.
   *
   * @throws CustomException attempt가 없으면 ATTEMPT_NOT_FOUND
   * @throws CustomException 이미 처리된 콜백(중복 수신)이면 ATTEMPT_NOT_IN_GENERATING_STATUS
   * @throws CustomException outcome이 정상(passed/partial_success/미상)인데 questions가 비어 있으면(계약 위반)
   *     INVALID_GENERATION_CALLBACK
   */
  @Transactional
  public void handleGenerationCompleted(
      String attemptId, GenerationCompletedCallbackRequest request) {
    ExamAttempt attempt = loadAndValidateGeneratingAttempt(attemptId);

    List<GenerationCallbackQuestionRequest> questions = request.questions();
    int questionCount = questions == null ? 0 : questions.size();
    // actualQuestionCount 미전송(레거시) 시 questions 길이로 대체
    Integer actualCount =
        request.actualQuestionCount() != null ? request.actualQuestionCount() : questionCount;
    Integer requestedCount = request.requestedQuestionCount();
    String outcome =
        request.generationOutcome() == null ? null : request.generationOutcome().trim();

    // needs_more_source: 자료 부족 비-에러 실패라 FAILED와 구분되는 사유 코드를 저장한다
    if (OUTCOME_NEEDS_MORE_SOURCE.equalsIgnoreCase(outcome)) {
      // FastAPI 발급 examId가 있으면 갱신(추후 참조용), 봉인값/문항은 저장하지 않는다.
      if (request.examId() != null && !request.examId().isBlank()) {
        attempt.updateExamId(request.examId());
      }
      attempt.markFailed(
          GenerationFailureReason.NEEDS_MORE_SOURCE, requestedCount, actualCount);
      log.warn(
          "[ExamAttemptGenerationCallbackService] 자료 부족 처리(needs_more_source) → FAILED | attemptId: {}, 요청: {}, 생성: {}",
          attemptId,
          requestedCount,
          actualCount);
      return;
    }

    // failed 명시 또는 questions 비어 있음: 진짜 실패 처리
    if (OUTCOME_FAILED.equalsIgnoreCase(outcome) || questionCount == 0) {
      // outcome이 정상(passed/partial_success/미상)인데 questions가 비면 계약 위반이라 INVALID_GENERATION_CALLBACK
      boolean explicitFailed = OUTCOME_FAILED.equalsIgnoreCase(outcome);
      if (!explicitFailed) {
        log.warn(
            "[ExamAttemptGenerationCallbackService] 빈 questions 수신 — 페이로드 유효하지 않음 | attemptId: {}, outcome: {}",
            attemptId,
            outcome);
        throw new CustomException(ExamForgeGradingErrorCode.INVALID_GENERATION_CALLBACK);
      }
      attempt.markFailed(GenerationFailureReason.GENERATION_ERROR, requestedCount, actualCount);
      log.warn(
          "[ExamAttemptGenerationCallbackService] 생성 실패 처리(failed) → FAILED | attemptId: {}, 요청: {}, 생성: {}",
          attemptId,
          requestedCount,
          actualCount);
      return;
    }

    // passed / partial_success / null(레거시): 정상 출고 (부분 출고 포함)
    // 민감 필드(correct_answer/explanation/grading_metadata)까지 exam_attempt_question에 저장한다
    List<ExamAttemptQuestion> questionEntities =
        questions.stream()
            .map(dto -> persistenceMapper.toExamAttemptQuestionEntity(dto, attempt))
            .toList();
    questionRepository.saveAll(questionEntities);

    // placeholder를 FastAPI가 발급한 실제 examId로 바꾼다
    attempt.updateExamId(request.examId());

    // 부분 출고도 READY로 전환하며 answerKeySeal과 요청/생성 문항 수를 함께 저장한다
    attempt.markReady(request.answerKeySeal(), requestedCount, actualCount);

    log.info(
        "[ExamAttemptGenerationCallbackService] 생성 완료 처리 | attemptId: {}, examId: {}, outcome: {}, 요청: {}, 생성: {}",
        attemptId,
        request.examId(),
        outcome == null ? "passed(legacy)" : outcome,
        requestedCount,
        questionEntities.size());
  }

  /**
   * 생성 실패 콜백을 받아 attempt를 FAILED로 전환한다.
   *
   * @throws CustomException attempt가 없으면 ATTEMPT_NOT_FOUND
   * @throws CustomException 이미 처리된 콜백(중복 수신)이면 ATTEMPT_NOT_IN_GENERATING_STATUS
   */
  @Transactional
  public void handleGenerationFailed(String attemptId, GenerationFailedCallbackRequest request) {
    ExamAttempt attempt = loadAndValidateGeneratingAttempt(attemptId);

    // generation-failed 경로는 진짜 실패(0문항/에러)만 받으므로 GENERATION_ERROR 사유로 저장한다
    // (자료 부족 needs_more_source는 /generated 콜백의 outcome 분기에서 처리한다)
    attempt.markFailed(GenerationFailureReason.GENERATION_ERROR, null, null);

    log.warn(
        "[ExamAttemptGenerationCallbackService] 생성 실패 처리 | attemptId: {}, reason: {}",
        attemptId,
        request.reason());
  }

  /**
   * attempt를 조회하고 GENERATING 상태임을 검증한다.
   *
   * <p>없으면 ATTEMPT_NOT_FOUND, GENERATING이 아니면 ATTEMPT_NOT_IN_GENERATING_STATUS(409) 예외.
   */
  private ExamAttempt loadAndValidateGeneratingAttempt(String attemptId) {
    ExamAttempt attempt =
        attemptRepository
            .findById(attemptId)
            .orElseThrow(() -> new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));

    if (attempt.getStatus() != AttemptStatus.GENERATING) {
      log.warn(
          "[ExamAttemptGenerationCallbackService] GENERATING 상태 아님 — 중복 콜백 의심 | attemptId: {}, 현재 status: {}",
          attemptId,
          attempt.getStatus());
      throw new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_IN_GENERATING_STATUS);
    }
    return attempt;
  }
}
