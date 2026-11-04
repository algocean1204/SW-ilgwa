/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swez.backend.domain.examgrading.dto.request.GenerationCallbackOptionRequest;
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

/**
 * ExamAttemptGenerationCallbackService 단위 테스트.
 *
 * <p>시뮬레이션 콜백 페이로드로 생성완료·실패 콜백 처리 흐름을 검증한다:
 *
 * <ul>
 *   <li>생성 완료 콜백 → exam_attempt_question 영속 + READY 상태 전환 + examId 갱신
 *   <li>생성 실패 콜백 → FAILED 상태 전환
 *   <li>GENERATING이 아닌 attempt에 콜백 수신 시 ATTEMPT_NOT_IN_GENERATING_STATUS(409)
 *   <li>attempt 없을 때 ATTEMPT_NOT_FOUND(404)
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExamAttemptGenerationCallbackServiceTest {

  @Mock private ExamAttemptRepository attemptRepository;
  @Mock private ExamAttemptQuestionRepository questionRepository;
  @Mock private ExamGradingPersistenceMapper persistenceMapper;

  @InjectMocks private ExamAttemptGenerationCallbackService callbackService;

  private static final String ATTEMPT_ID = "eat_test_001";
  private static final String EXAM_ID = "exam_test_001";
  private static final String ANSWER_KEY_SEAL = "sealed_answer_key_abc";

  /** GENERATING 상태의 ExamAttempt 엔티티. 실제 상태 전환 메서드를 호출하려고 spy 대신 직접 만든다 */
  private ExamAttempt generatingAttempt;

  @BeforeEach
  void setUp() {
    generatingAttempt =
        ExamAttempt.builder()
            .attemptId(ATTEMPT_ID)
            .examId("pending_" + ATTEMPT_ID)
            .passPercentage(BigDecimal.valueOf(60))
            .status(AttemptStatus.GENERATING)
            .build();
  }

  // 생성 완료 콜백 정상 흐름

  @Test
  @DisplayName("생성 완료 콜백 정상 처리 — attempt READY 전환 + exam_attempt_question 영속")
  void handleGenerationCompleted_success() {
    // given: GENERATING attempt 반환 준비
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(generatingAttempt));

    // mapper가 toExamAttemptQuestionEntity를 호출할 때 더미 엔티티 반환
    ExamAttemptQuestion dummyQuestion =
        ExamAttemptQuestion.builder()
            .attempt(generatingAttempt)
            .questionId("q-01")
            .templateId("ko_multiple_choice")
            .displayOrder(0)
            .stem("첫 번째 문항")
            .points(BigDecimal.TEN)
            .build();
    when(persistenceMapper.toExamAttemptQuestionEntity(any(), any())).thenReturn(dummyQuestion);
    when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    // 시뮬레이션 콜백 페이로드 구성
    GenerationCompletedCallbackRequest request = buildCompletedCallbackRequest();

    // when
    callbackService.handleGenerationCompleted(ATTEMPT_ID, request);

    // then: READY 상태 전환 확인
    assertThat(generatingAttempt.getStatus()).isEqualTo(AttemptStatus.READY);
    // answer_key_seal 저장 확인
    assertThat(generatingAttempt.getAnswerKeySeal()).isEqualTo(ANSWER_KEY_SEAL);
    // examId 갱신 확인 (placeholder → 실제 examId)
    assertThat(generatingAttempt.getExamId()).isEqualTo(EXAM_ID);
    // exam_attempt_question saveAll 호출 확인
    verify(questionRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("생성 완료 콜백 — questions 비어 있으면 INVALID_GENERATION_CALLBACK(400)")
  void handleGenerationCompleted_emptyQuestions_throwsInvalidCallback() {
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(generatingAttempt));

    GenerationCompletedCallbackRequest emptyRequest =
        new GenerationCompletedCallbackRequest(EXAM_ID, ANSWER_KEY_SEAL, List.of());

    assertThatThrownBy(() -> callbackService.handleGenerationCompleted(ATTEMPT_ID, emptyRequest))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.INVALID_GENERATION_CALLBACK));
    // exam_attempt_question은 저장되지 않아야 한다
    verify(questionRepository, never()).saveAll(anyList());
  }

  // 생성 실패 콜백 정상 흐름

  @Test
  @DisplayName("생성 실패 콜백 정상 처리 — attempt FAILED 전환")
  void handleGenerationFailed_success() {
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(generatingAttempt));

    GenerationFailedCallbackRequest failRequest =
        new GenerationFailedCallbackRequest("LLM timeout");

    callbackService.handleGenerationFailed(ATTEMPT_ID, failRequest);

    assertThat(generatingAttempt.getStatus()).isEqualTo(AttemptStatus.FAILED);
    // generation-failed 경로는 실제 실패만 다루며 GENERATION_ERROR 사유 코드를 저장한다
    assertThat(generatingAttempt.getFailureReason())
        .isEqualTo(GenerationFailureReason.GENERATION_ERROR);
    // 문항 저장 없어야 한다
    verify(questionRepository, never()).saveAll(anyList());
  }

  // generationOutcome 값별 처리: partial_success / needs_more_source / failed

  @Test
  @DisplayName("partial_success — 문항 수가 요청보다 적어도 READY 전환 + 요청/생성 문항 수 보존")
  void handleGenerationCompleted_partialSuccess_readyWithCounts() {
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(generatingAttempt));
    ExamAttemptQuestion dummyQuestion =
        ExamAttemptQuestion.builder()
            .attempt(generatingAttempt)
            .questionId("q-01")
            .templateId("ko_multiple_choice")
            .displayOrder(0)
            .stem("부분 출고 문항")
            .points(BigDecimal.TEN)
            .build();
    when(persistenceMapper.toExamAttemptQuestionEntity(any(), any())).thenReturn(dummyQuestion);
    when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    // 요청 20 / 생성 1 (부분 출고)
    GenerationCompletedCallbackRequest request =
        new GenerationCompletedCallbackRequest(
            EXAM_ID,
            ANSWER_KEY_SEAL,
            List.of(buildQuestion()),
            "partial_success",
            20,
            1);

    callbackService.handleGenerationCompleted(ATTEMPT_ID, request);

    // 부분 출고도 정상 READY
    assertThat(generatingAttempt.getStatus()).isEqualTo(AttemptStatus.READY);
    assertThat(generatingAttempt.getAnswerKeySeal()).isEqualTo(ANSWER_KEY_SEAL);
    assertThat(generatingAttempt.getExamId()).isEqualTo(EXAM_ID);
    // 요청/생성 문항 수 보존 (프론트 "요청 N / 생성 M" 표기용)
    assertThat(generatingAttempt.getRequestedQuestionCount()).isEqualTo(20);
    assertThat(generatingAttempt.getActualQuestionCount()).isEqualTo(1);
    assertThat(generatingAttempt.getFailureReason()).isNull();
    verify(questionRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("needs_more_source — questions 비어 있어도 FAILED + NEEDS_MORE_SOURCE 사유 코드 저장 (진짜 실패와 구분)")
  void handleGenerationCompleted_needsMoreSource_failedWithReason() {
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(generatingAttempt));

    GenerationCompletedCallbackRequest request =
        new GenerationCompletedCallbackRequest(
            EXAM_ID, ANSWER_KEY_SEAL, List.of(), "needs_more_source", 20, 3);

    callbackService.handleGenerationCompleted(ATTEMPT_ID, request);

    // FAILED 상태지만 사유 코드로 자료 부족을 구분
    assertThat(generatingAttempt.getStatus()).isEqualTo(AttemptStatus.FAILED);
    assertThat(generatingAttempt.getFailureReason())
        .isEqualTo(GenerationFailureReason.NEEDS_MORE_SOURCE);
    // 요청/생성 문항 수 보존 (프론트 안내 "20문항 요청, 3문항만 가능" 등)
    assertThat(generatingAttempt.getRequestedQuestionCount()).isEqualTo(20);
    assertThat(generatingAttempt.getActualQuestionCount()).isEqualTo(3);
    // 문항·봉인값 저장하지 않는다
    verify(questionRepository, never()).saveAll(anyList());
    assertThat(generatingAttempt.getAnswerKeySeal()).isNull();
  }

  @Test
  @DisplayName("outcome=failed — 진짜 실패로 FAILED + GENERATION_ERROR 사유 코드 저장")
  void handleGenerationCompleted_explicitFailed_failedWithReason() {
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(generatingAttempt));

    GenerationCompletedCallbackRequest request =
        new GenerationCompletedCallbackRequest(
            EXAM_ID, ANSWER_KEY_SEAL, List.of(), "failed", 20, 0);

    callbackService.handleGenerationCompleted(ATTEMPT_ID, request);

    assertThat(generatingAttempt.getStatus()).isEqualTo(AttemptStatus.FAILED);
    assertThat(generatingAttempt.getFailureReason())
        .isEqualTo(GenerationFailureReason.GENERATION_ERROR);
    verify(questionRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("passed outcome — actualQuestionCount 미전송 시 questions 길이로 대체하여 READY")
  void handleGenerationCompleted_passedOutcome_actualCountFallback() {
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(generatingAttempt));
    ExamAttemptQuestion dummyQuestion =
        ExamAttemptQuestion.builder()
            .attempt(generatingAttempt)
            .questionId("q-01")
            .templateId("ko_multiple_choice")
            .displayOrder(0)
            .stem("정상 문항")
            .points(BigDecimal.TEN)
            .build();
    when(persistenceMapper.toExamAttemptQuestionEntity(any(), any())).thenReturn(dummyQuestion);
    when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    // actualQuestionCount를 null로 보내면 questions 길이(1)로 대체돼야 한다
    GenerationCompletedCallbackRequest request =
        new GenerationCompletedCallbackRequest(
            EXAM_ID, ANSWER_KEY_SEAL, List.of(buildQuestion()), "passed", 20, null);

    callbackService.handleGenerationCompleted(ATTEMPT_ID, request);

    assertThat(generatingAttempt.getStatus()).isEqualTo(AttemptStatus.READY);
    assertThat(generatingAttempt.getActualQuestionCount()).isEqualTo(1);
    assertThat(generatingAttempt.getRequestedQuestionCount()).isEqualTo(20);
  }

  // 비정상 상태 및 not-found 케이스

  @Test
  @DisplayName("attempt가 없으면 ATTEMPT_NOT_FOUND(404)")
  void handleGenerationCompleted_attemptNotFound() {
    when(attemptRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                callbackService.handleGenerationCompleted(
                    "nonexistent", buildCompletedCallbackRequest()))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));
  }

  @Test
  @DisplayName("GENERATING이 아닌 attempt에 생성 완료 콜백 수신 시 ATTEMPT_NOT_IN_GENERATING_STATUS(409)")
  void handleGenerationCompleted_notGeneratingStatus_throwsConflict() {
    // READY 상태인 attempt 시뮬레이션
    ExamAttempt readyAttempt =
        ExamAttempt.builder()
            .attemptId(ATTEMPT_ID)
            .examId(EXAM_ID)
            .passPercentage(BigDecimal.valueOf(60))
            .status(AttemptStatus.READY)
            .build();
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(readyAttempt));

    assertThatThrownBy(
            () ->
                callbackService.handleGenerationCompleted(
                    ATTEMPT_ID, buildCompletedCallbackRequest()))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_IN_GENERATING_STATUS));
  }

  @Test
  @DisplayName("GENERATING이 아닌 attempt에 생성 실패 콜백 수신 시 ATTEMPT_NOT_IN_GENERATING_STATUS(409)")
  void handleGenerationFailed_notGeneratingStatus_throwsConflict() {
    ExamAttempt failedAttempt =
        ExamAttempt.builder()
            .attemptId(ATTEMPT_ID)
            .examId(EXAM_ID)
            .passPercentage(BigDecimal.valueOf(60))
            .status(AttemptStatus.FAILED)
            .build();
    when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(failedAttempt));

    assertThatThrownBy(
            () ->
                callbackService.handleGenerationFailed(
                    ATTEMPT_ID, new GenerationFailedCallbackRequest("duplicate")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_IN_GENERATING_STATUS));
  }

  // 헬퍼: 시뮬레이션 콜백 페이로드 생성

  /**
   * 시뮬레이션 생성 완료 콜백 페이로드를 구성한다.
   *
   * <p>공유 계약에 정의된 questions[] 구조를 그대로 재현하며 민감 필드(correct_answer/explanation/grading_metadata)도
   * 포함한다.
   */
  private GenerationCompletedCallbackRequest buildCompletedCallbackRequest() {
    return new GenerationCompletedCallbackRequest(
        EXAM_ID, ANSWER_KEY_SEAL, List.of(buildQuestion()));
  }

  /** 민감 필드(correct_answer/explanation/grading_metadata)를 포함한 시뮬레이션 단일 문항. */
  private GenerationCallbackQuestionRequest buildQuestion() {
    List<GenerationCallbackOptionRequest> options =
        List.of(
            new GenerationCallbackOptionRequest("A", "프로세스 우선순위 기반", false),
            new GenerationCallbackOptionRequest("B", "도착 순서 기반", false),
            new GenerationCallbackOptionRequest("C", "CPU 점유 시간 기반", true),
            new GenerationCallbackOptionRequest("D", "메모리 크기 기반", false));

    return new GenerationCallbackQuestionRequest(
        "q-01",
        "ko_multiple_choice",
        0,
        "다음 중 SJF 스케줄링 알고리즘의 핵심 기준은?",
        options,
        BigDecimal.TEN,
        "C", // correct_answer: 서버 전용 민감 필드
        "SJF(Shortest Job First)는 CPU 버스트 시간이 가장 짧은 프로세스를 먼저 실행한다.", // explanation: 서버 전용
        "운영체제론 5장 CPU 스케줄링",
        null // gradingMetadata: 객관식은 null
        );
  }
}
