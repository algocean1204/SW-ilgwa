/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.mockexam.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.event.ExamAttemptGradedEvent;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.GradingStatus;
import com.swez.backend.domain.examgrading.port.ExamAttemptRepositoryPort;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;
import com.swez.backend.domain.mockexam.entity.MockExam;
import com.swez.backend.domain.mockexam.entity.MockExamSubmission;
import com.swez.backend.domain.mockexam.enums.MockExamStatus;
import com.swez.backend.domain.mockexam.repository.MockExamRepository;
import com.swez.backend.domain.mockexam.repository.MockExamSubmissionRepository;

/**
 * 채점 완료 이벤트 복제 리스너 단위 테스트.
 *
 * <p>같은 이벤트가 중복 수신돼도(콜백 중복/재제출) mock_exam_submission을 두 번 저장하지 않는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MockExamAttemptGradedListener 단위 테스트")
class MockExamAttemptGradedListenerTest {

  private static final String ATTEMPT_ID = "att_01TEST";
  private static final String MOCK_EXAM_ID = "mex_01TEST";
  private static final String USER_ID = "user-1";

  @Mock private ExamAttemptRepository examAttemptRepository;
  @Mock private ExamAttemptRepositoryPort examAttemptRepositoryPort;
  @Mock private MockExamRepository mockExamRepository;
  @Mock private MockExamSubmissionRepository mockExamSubmissionRepository;

  private MockExamAttemptGradedListener listener;

  @Mock private ExamAttempt attempt;
  @Mock private MockExam exam;
  @Mock private User user;

  @BeforeEach
  void setUp() {
    listener =
        new MockExamAttemptGradedListener(
            examAttemptRepository,
            examAttemptRepositoryPort,
            mockExamRepository,
            mockExamSubmissionRepository,
            new ObjectMapper());

    given(attempt.getMockExamId()).willReturn(MOCK_EXAM_ID);
    given(attempt.getUser()).willReturn(user);
    given(exam.getId()).willReturn(MOCK_EXAM_ID);
    given(examAttemptRepository.findById(ATTEMPT_ID)).willReturn(Optional.of(attempt));
    given(mockExamRepository.findById(MOCK_EXAM_ID)).willReturn(Optional.of(exam));
    given(examAttemptRepositoryPort.findGradeResult(ATTEMPT_ID))
        .willReturn(Optional.of(seedGradeResult()));
    given(examAttemptRepositoryPort.findAnalysisJson(ATTEMPT_ID)).willReturn(Optional.empty());
    given(examAttemptRepositoryPort.findReviewData(ATTEMPT_ID)).willReturn(List.of());
  }

  @Test
  @DisplayName("기존 제출 기록이 있으면(중복 이벤트) 복제 저장을 건너뛴다 — 멱등 가드")
  void handleGraded_duplicateEvent_skipsReplication() {
    given(mockExamSubmissionRepository.findByMockExamIdAndUserId(MOCK_EXAM_ID, USER_ID))
        .willReturn(Optional.of(mock(MockExamSubmission.class)));

    listener.handleGraded(new ExamAttemptGradedEvent(ATTEMPT_ID, USER_ID));

    then(mockExamSubmissionRepository).should(never()).save(any());
    then(exam).should(never()).updateStatus(any());
  }

  @Test
  @DisplayName("제출 기록이 없으면 1회만 복제 저장하고 모의고사 상태를 COMPLETED로 동기화한다")
  void handleGraded_firstEvent_replicatesOnce() {
    given(mockExamSubmissionRepository.findByMockExamIdAndUserId(MOCK_EXAM_ID, USER_ID))
        .willReturn(Optional.empty());

    listener.handleGraded(new ExamAttemptGradedEvent(ATTEMPT_ID, USER_ID));

    then(mockExamSubmissionRepository).should(times(1)).save(any(MockExamSubmission.class));
    then(exam).should(times(1)).updateStatus(MockExamStatus.COMPLETED);
  }

  @Test
  @DisplayName("mockExamId가 없는 독립 응시는 복제하지 않는다")
  void handleGraded_attemptWithoutMockExamId_skipsReplication() {
    given(attempt.getMockExamId()).willReturn(null);

    listener.handleGraded(new ExamAttemptGradedEvent(ATTEMPT_ID, USER_ID));

    then(mockExamSubmissionRepository).should(never()).save(any());
  }

  /** 채점 완료(2점/4점) 시드 결과. */
  private ExamGradeResult seedGradeResult() {
    return new ExamGradeResult(
        ATTEMPT_ID,
        "exam-1",
        new BigDecimal("2"),
        new BigDecimal("4"),
        new BigDecimal("50.00"),
        false,
        GradingStatus.GRADED,
        OffsetDateTime.now(),
        List.of());
  }
}
