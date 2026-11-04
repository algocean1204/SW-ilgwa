/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.assignment.dto.request.SubmitAnswerRequest;
import com.swez.backend.domain.assignment.dto.response.AssignmentDetailResponse;
import com.swez.backend.domain.assignment.dto.response.AssignmentListResponse;
import com.swez.backend.domain.assignment.dto.response.GradeResponse;
import com.swez.backend.domain.assignment.dto.response.SubmissionResponse;
import com.swez.backend.domain.assignment.exception.AssignmentErrorCode;
import com.swez.backend.domain.assignment.mapper.AssignmentMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.calendar.entity.Assignment;
import com.swez.backend.domain.calendar.entity.Submission;
import com.swez.backend.domain.calendar.repository.AssignmentRepository;
import com.swez.backend.domain.calendar.repository.SubmissionRepository;
import com.swez.backend.domain.calendar.service.AssignmentGradingTriggerService;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.exception.CourseErrorCode;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.global.exception.CustomException;

@DisplayName("AssignmentService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssignmentServiceTest {

  @Mock private CourseRepository courseRepository;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private SubmissionRepository submissionRepository;
  @Mock private AssignmentMapper assignmentMapper;
  @Mock private ObjectMapper objectMapper;
  @Mock private AssignmentGradingTriggerService gradingTriggerService;

  @InjectMocks private AssignmentService assignmentService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String OTHER_ID = "usr_OTHERUSERIDIDIDIDIDIDIDID";
  private static final String COURSE_ID = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String ASSIGNMENT_ID = "asg_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String SUBMISSION_ID = "sbm_01ARZ3NDEKTSV4RRFFQ69G5FAV";

  @BeforeEach
  void setUp() {
    ObjectMapper realObjectMapper = new ObjectMapper();
    ReflectionTestUtils.setField(
        assignmentService, "assignmentMapper", new AssignmentMapper(realObjectMapper));
    ReflectionTestUtils.setField(assignmentService, "objectMapper", realObjectMapper);
  }

  private User makeUser(String id) {
    return User.builder()
        .id(id)
        .email("test@example.com")
        .nickname("테스터")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  private Course makeCourse(User owner) {
    return Course.builder()
        .id(COURSE_ID)
        .user(owner)
        .courseName("수학 기초")
        .subject("수학")
        .status(CourseStatus.ACTIVE)
        .totalChapters(3)
        .completedSlides(0)
        .progressPercent(BigDecimal.valueOf(30))
        .build();
  }

  private Assignment makeAssignment(Course course) {
    return Assignment.builder()
        .id(ASSIGNMENT_ID)
        .course(course)
        .user(course.getUser())
        .title("1단원 과제")
        .description("기본 연산 문제")
        .status("active")
        .dueDate(LocalDateTime.now().plusDays(7))
        .totalQuestions(5)
        .maxScore(100)
        .questions(
            "[{\"questionId\":1,\"content\":\"1+1=\"},{\"questionId\":2,\"content\":\"2+2=\"}]")
        .build();
  }

  private Assignment makeExpiredAssignment(Course course) {
    return Assignment.builder()
        .id(ASSIGNMENT_ID)
        .course(course)
        .user(course.getUser())
        .title("마감된 과제")
        .status("active")
        .dueDate(LocalDateTime.now().minusDays(1))
        .totalQuestions(3)
        .build();
  }

  private Submission makeSubmission(Assignment assignment, User user, String status) {
    return Submission.builder()
        .id(SUBMISSION_ID)
        .assignment(assignment)
        .user(user)
        .status(status)
        .officialScore(85)
        .officialFeedback("잘했습니다.")
        .officialAiConfidence(BigDecimal.valueOf(0.95))
        .ocrFailedPages(0)
        .totalPages(3)
        .gradedAt(LocalDateTime.now())
        .build();
  }

  // getAssignments()

  @Nested
  @DisplayName("getAssignments()")
  class GetAssignments {

    @Test
    @DisplayName("과제 목록을 반환한다")
    void getAssignments_returnsList() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Assignment assignment = makeAssignment(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(assignmentRepository.findByCourseIdAndOptionalStatus(eq(COURSE_ID), eq(null)))
          .willReturn(List.of(assignment));

      // when
      List<AssignmentListResponse> result =
          assignmentService.getAssignments(USER_ID, COURSE_ID, null);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).id()).isEqualTo(ASSIGNMENT_ID);
      assertThat(result.get(0).title()).isEqualTo("1단원 과제");
    }

    @Test
    @DisplayName("타인 과외 접근 시 CRS_001 예외를 던진다")
    void getAssignments_otherUser_throwsCrs001() {
      // given
      User owner = makeUser(USER_ID);
      Course course = makeCourse(owner);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

      // when & then
      assertThatThrownBy(() -> assignmentService.getAssignments(OTHER_ID, COURSE_ID, null))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_001));
    }
  }

  // getAssignment()

  @Nested
  @DisplayName("getAssignment()")
  class GetAssignment {

    @Test
    @DisplayName("과제 상세를 반환한다 (questions JSON 파싱 포함)")
    void getAssignment_returnsDetail() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Assignment assignment = makeAssignment(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(assignment));
      given(
              submissionRepository.findTopByAssignmentIdAndUserIdOrderByCreatedAtDesc(
                  ASSIGNMENT_ID, USER_ID))
          .willReturn(Optional.empty());

      // when
      AssignmentDetailResponse result =
          assignmentService.getAssignment(USER_ID, COURSE_ID, ASSIGNMENT_ID);

      // then
      assertThat(result.id()).isEqualTo(ASSIGNMENT_ID);
      assertThat(result.title()).isEqualTo("1단원 과제");
      assertThat(result.questions()).hasSize(2);
      assertThat(result.previousAnswer()).isNull();
    }

    @Test
    @DisplayName("텍스트 제출이 있으면 previousAnswer로 제출 답안 텍스트를 반환한다")
    void getAssignment_withTextSubmission_returnsSubmittedAnswer() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Assignment assignment = makeAssignment(course);
      Submission submission =
          Submission.builder()
              .id(SUBMISSION_ID)
              .assignment(assignment)
              .user(user)
              .status("queued")
              .submittedAnswers("[{\"questionId\":1,\"answer\":\"내가 쓴 답안\"}]")
              .build();
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(assignment));
      given(
              submissionRepository.findTopByAssignmentIdAndUserIdOrderByCreatedAtDesc(
                  ASSIGNMENT_ID, USER_ID))
          .willReturn(Optional.of(submission));

      // when
      AssignmentDetailResponse result =
          assignmentService.getAssignment(USER_ID, COURSE_ID, ASSIGNMENT_ID);

      // then: officialOcrText가 null이어도 submittedAnswers에서 이전 답안을 가져온다
      assertThat(result.previousAnswer()).isEqualTo("내가 쓴 답안");
    }

    @Test
    @DisplayName("존재하지 않는 과제 조회 시 ASG_001 예외를 던진다")
    void getAssignment_notFound_throwsAsg001() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> assignmentService.getAssignment(USER_ID, COURSE_ID, ASSIGNMENT_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AssignmentErrorCode.ASG_001));
    }
  }

  // submitAssignment()

  @Nested
  @DisplayName("submitAssignment()")
  class SubmitAssignment {

    @Test
    @DisplayName("정상 제출 시 SubmissionResponse를 반환한다")
    void submitAssignment_success() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Assignment assignment = makeAssignment(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(assignment));
      given(submissionRepository.existsByAssignmentIdAndUserId(ASSIGNMENT_ID, USER_ID))
          .willReturn(false);
      given(submissionRepository.save(any(Submission.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      SubmitAnswerRequest request =
          new SubmitAnswerRequest(List.of(new SubmitAnswerRequest.AnswerItem(1, "2")));

      // when
      SubmissionResponse result =
          assignmentService.submitAssignment(USER_ID, COURSE_ID, ASSIGNMENT_ID, request);

      // then
      assertThat(result.assignmentId()).isEqualTo(ASSIGNMENT_ID);
      assertThat(result.status()).isEqualTo("queued");
      // 웹 제출도 FastAPI 텍스트 채점을 호출해야 한다
      verify(gradingTriggerService)
          .triggerTextGrading(any(Submission.class), eq(assignment), eq("2"));
    }

    @Test
    @DisplayName("마감일 지난 과제 제출 시 ASG_002 예외를 던진다")
    void submitAssignment_expired_throwsAsg002() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Assignment expired = makeExpiredAssignment(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(expired));

      SubmitAnswerRequest request =
          new SubmitAnswerRequest(List.of(new SubmitAnswerRequest.AnswerItem(1, "2")));

      // when & then
      assertThatThrownBy(
              () -> assignmentService.submitAssignment(USER_ID, COURSE_ID, ASSIGNMENT_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AssignmentErrorCode.ASG_002));
    }

    @Test
    @DisplayName("이미 제출된 과제 재제출 시 ASG_003 예외를 던진다")
    void submitAssignment_duplicate_throwsAsg003() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Assignment assignment = makeAssignment(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(assignment));
      given(submissionRepository.existsByAssignmentIdAndUserId(ASSIGNMENT_ID, USER_ID))
          .willReturn(true);

      SubmitAnswerRequest request =
          new SubmitAnswerRequest(List.of(new SubmitAnswerRequest.AnswerItem(1, "2")));

      // when & then
      assertThatThrownBy(
              () -> assignmentService.submitAssignment(USER_ID, COURSE_ID, ASSIGNMENT_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AssignmentErrorCode.ASG_003));
    }
  }

  // getGrades()

  @Nested
  @DisplayName("getGrades()")
  class GetGrades {

    @Test
    @DisplayName("채점 완료된 결과를 반환한다")
    void getGrades_done_returnsGrade() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Assignment assignment = makeAssignment(course);
      Submission submission = makeSubmission(assignment, user, "done");
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(assignment));
      given(
              submissionRepository.findTopByAssignmentIdAndUserIdOrderByCreatedAtDesc(
                  ASSIGNMENT_ID, USER_ID))
          .willReturn(Optional.of(submission));

      // when
      GradeResponse result = assignmentService.getGrades(USER_ID, COURSE_ID, ASSIGNMENT_ID);

      // then
      assertThat(result.submissionId()).isEqualTo(SUBMISSION_ID);
      assertThat(result.score()).isEqualTo(85);
      assertThat(result.maxScore()).isEqualTo(100);
      assertThat(result.feedback()).isEqualTo("잘했습니다.");
    }

    @Test
    @DisplayName("제출 기록이 없으면 ASG_005 예외를 던진다")
    void getGrades_noSubmission_throwsAsg005() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Assignment assignment = makeAssignment(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(assignment));
      given(
              submissionRepository.findTopByAssignmentIdAndUserIdOrderByCreatedAtDesc(
                  ASSIGNMENT_ID, USER_ID))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> assignmentService.getGrades(USER_ID, COURSE_ID, ASSIGNMENT_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AssignmentErrorCode.ASG_005));
    }

    @Test
    @DisplayName("미채점 상태이면 ASG_004 예외를 던진다")
    void getGrades_notDone_throwsAsg004() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Assignment assignment = makeAssignment(course);
      Submission submission = makeSubmission(assignment, user, "grading");
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(assignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(assignment));
      given(
              submissionRepository.findTopByAssignmentIdAndUserIdOrderByCreatedAtDesc(
                  ASSIGNMENT_ID, USER_ID))
          .willReturn(Optional.of(submission));

      // when & then
      assertThatThrownBy(() -> assignmentService.getGrades(USER_ID, COURSE_ID, ASSIGNMENT_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(AssignmentErrorCode.ASG_004));
    }
  }
}
