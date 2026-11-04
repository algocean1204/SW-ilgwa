/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.calendar.dto.request.AiResultCallbackRequest;
import com.swez.backend.domain.calendar.dto.request.CreateSubmissionRequest;
import com.swez.backend.domain.calendar.dto.response.AiResultAckResponse;
import com.swez.backend.domain.calendar.dto.response.SubmissionResponse;
import com.swez.backend.domain.calendar.entity.Assignment;
import com.swez.backend.domain.calendar.entity.Submission;
import com.swez.backend.domain.calendar.exception.SubmissionErrorCode;
import com.swez.backend.domain.calendar.mapper.CalendarMapper;
import com.swez.backend.domain.calendar.repository.AssignmentRepository;
import com.swez.backend.domain.calendar.repository.SubmissionRepository;
import com.swez.backend.global.exception.CustomException;

@DisplayName("InternalService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalServiceTest {

  @Mock private SubmissionRepository submissionRepository;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private CalendarMapper calendarMapper;
  // receiveAiResult() 채점 완료 경로의 결과 DM 발송에 쓴다. 선언하지 않으면 @InjectMocks가 null을 넣어 NPE가 난다
  @Mock private SubmissionResultDmService resultDmService;

  @InjectMocks private InternalService internalService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String ASSIGNMENT_ID = "asg_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String SUBMISSION_ID = "sbm_01ARZ3NDEKTSV4RRFFQ69G5FAV";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(internalService, "calendarMapper", new CalendarMapper());
  }

  private User makeUser() {
    return User.builder()
        .id(USER_ID)
        .email("t@t.com")
        .nickname("테스터")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  private Assignment makeAssignment() {
    return Assignment.builder().id(ASSIGNMENT_ID).user(makeUser()).status("active").build();
  }

  private Submission makeSubmission(String status) {
    return makeSubmission(status, makeAssignment());
  }

  private Submission makeSubmission(String status, Assignment assignment) {
    return Submission.builder()
        .id(SUBMISSION_ID)
        .assignment(assignment)
        .user(makeUser())
        .status(status)
        .build();
  }

  @Nested
  @DisplayName("createSubmission()")
  class CreateSubmission {

    @Test
    @DisplayName("제출물 row를 생성하고 queued 상태로 반환한다")
    void createSubmission_success() {
      // given
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(makeAssignment()));
      given(submissionRepository.save(any(Submission.class))).willAnswer(inv -> inv.getArgument(0));

      CreateSubmissionRequest request =
          new CreateSubmissionRequest(ASSIGNMENT_ID, USER_ID, null, false);

      // when
      SubmissionResponse result = internalService.createSubmission(request);

      // then
      assertThat(result.status()).isEqualTo("queued");
      assertThat(result.assignmentId()).isEqualTo(ASSIGNMENT_ID);
    }

    @Test
    @DisplayName("존재하지 않는 과제 ID면 SBM_001 예외를 던진다")
    void createSubmission_notFound_throwsSbm001() {
      // given
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
              () ->
                  internalService.createSubmission(
                      new CreateSubmissionRequest(ASSIGNMENT_ID, USER_ID, null, false)))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(SubmissionErrorCode.SBM_001));
    }
  }

  @Nested
  @DisplayName("receiveAiResult()")
  class ReceiveAiResult {

    @Test
    @DisplayName("queued 상태 제출물에 결과를 반영하고 과제를 completed 로 전이한다")
    void receiveAiResult_queued_success() {
      // given
      Assignment assignment = makeAssignment();
      Submission submission = makeSubmission("queued", assignment);
      given(submissionRepository.findById(SUBMISSION_ID)).willReturn(Optional.of(submission));
      given(submissionRepository.save(any(Submission.class))).willReturn(submission);
      given(assignmentRepository.save(any(Assignment.class))).willAnswer(inv -> inv.getArgument(0));

      AiResultCallbackRequest request =
          new AiResultCallbackRequest(
              "ocr text", 85, "잘했어요", BigDecimal.valueOf(0.95), 0, 10, false, "done");

      // when
      AiResultAckResponse result = internalService.receiveAiResult(SUBMISSION_ID, request);

      // then
      assertThat(result.ack()).isTrue();
      assertThat(result.nextAction()).isEqualTo("notify_user");
      assertThat(submission.getStatus()).isEqualTo("done");
      assertThat(assignment.getStatus()).isEqualTo("completed");
    }

    @Test
    @DisplayName("이미 done 상태 제출물의 done 콜백 재시도는 과제 completed 전이를 보장하고 ack 를 반환한다")
    void receiveAiResult_alreadyDoneDoneCallback_idempotentAck() {
      // given
      Assignment assignment = makeAssignment();
      Submission submission = makeSubmission("done", assignment);
      given(submissionRepository.findById(SUBMISSION_ID)).willReturn(Optional.of(submission));
      given(assignmentRepository.save(any(Assignment.class))).willAnswer(inv -> inv.getArgument(0));

      AiResultCallbackRequest request =
          new AiResultCallbackRequest(
              null, null, null, BigDecimal.valueOf(0.5), 0, null, false, "done");

      // when
      AiResultAckResponse result = internalService.receiveAiResult(SUBMISSION_ID, request);

      // then
      assertThat(result.ack()).isTrue();
      assertThat(assignment.getStatus()).isEqualTo("completed");
    }

    @Test
    @DisplayName("이미 failed 상태 제출물 재처리 시 SBM_002 예외를 던진다")
    void receiveAiResult_alreadyFailed_throwsSbm002() {
      // given
      given(submissionRepository.findById(SUBMISSION_ID))
          .willReturn(Optional.of(makeSubmission("failed")));

      AiResultCallbackRequest request =
          new AiResultCallbackRequest(
              null, null, null, BigDecimal.valueOf(0.5), 0, null, false, "failed");

      // when & then
      assertThatThrownBy(() -> internalService.receiveAiResult(SUBMISSION_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(SubmissionErrorCode.SBM_002));
    }
  }
}
