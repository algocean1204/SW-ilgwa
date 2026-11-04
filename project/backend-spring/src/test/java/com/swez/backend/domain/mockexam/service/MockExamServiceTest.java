/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.coin.service.CoinService;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.exception.CourseErrorCode;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.mockexam.dto.request.GenerateExamRequest;
import com.swez.backend.domain.mockexam.dto.request.SubmitExamRequest;
import com.swez.backend.domain.mockexam.dto.response.ExamResultResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamDetailResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamListResponse;
import com.swez.backend.domain.mockexam.entity.MockExam;
import com.swez.backend.domain.mockexam.entity.MockExamQuestion;
import com.swez.backend.domain.mockexam.entity.MockExamSubmission;
import com.swez.backend.domain.mockexam.enums.MockExamStatus;
import com.swez.backend.domain.mockexam.exception.MockExamErrorCode;
import com.swez.backend.domain.mockexam.mapper.MockExamMapper;
import com.swez.backend.domain.mockexam.repository.MockExamQuestionRepository;
import com.swez.backend.domain.mockexam.repository.MockExamRepository;
import com.swez.backend.domain.mockexam.repository.MockExamSubmissionRepository;
import com.swez.backend.global.exception.CustomException;

@DisplayName("MockExamService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MockExamServiceTest {

  @Mock private MockExamRepository mockExamRepository;
  @Mock private MockExamQuestionRepository mockExamQuestionRepository;
  @Mock private MockExamSubmissionRepository mockExamSubmissionRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private MockExamMapper mockExamMapper;
  @Mock private RestTemplate restTemplate;
  /* 생성자 주입에서 NPE가 나지 않게 coinService도 mock으로 둔다 */
  @Mock private CoinService coinService;

  @InjectMocks private MockExamService mockExamService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String OTHER_ID = "usr_OTHERUSERIDIDIDIDIDIDIDID";
  private static final String COURSE_ID = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String EXAM_ID = "mex_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String QUESTION_ID = "mq_01ARZ3NDEKTSV4RRFFQ69G5FAVV";
  private static final String SUBMISSION_ID = "ms_01ARZ3NDEKTSV4RRFFQ69G5FAVV";

  @BeforeEach
  void setUp() {
    /* 실제 MockExamMapper 를 주입하여 매핑 로직도 검증 */
    ReflectionTestUtils.setField(mockExamService, "mockExamMapper", new MockExamMapper());
    ReflectionTestUtils.setField(mockExamService, "objectMapper", new ObjectMapper());
    ReflectionTestUtils.setField(mockExamService, "fastapiBaseUrl", "http://localhost:8000");
    /* 레거시 MCQ 경로를 검증하는 테스트라 cutover=false로 고정한다 */
    ReflectionTestUtils.setField(mockExamService, "cutoverEnabled", false);
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

  private MockExam makeExam(Course course, MockExamStatus status) {
    return MockExam.builder()
        .id(EXAM_ID)
        .course(course)
        .user(course.getUser())
        .title("수학 기초 - midterm 모의고사")
        .examType("midterm")
        .questionCount(10)
        .difficulty("medium")
        .timeLimit(30)
        .status(status)
        .totalPoints(50)
        .createdAt(LocalDateTime.now())
        .build();
  }

  private MockExamQuestion makeQuestion(MockExam exam) {
    return MockExamQuestion.builder()
        .id(QUESTION_ID)
        .mockExam(exam)
        .questionIdx(1)
        .questionText("1 + 1 = ?")
        .questionType("multiple_choice")
        .options("[\"1\",\"2\",\"3\",\"4\"]")
        .correctOption("B")
        .explanation("1 + 1은 2입니다.")
        .points(5)
        .build();
  }

  private MockExamSubmission makeSubmission(MockExam exam, User user) {
    return MockExamSubmission.builder()
        .id(SUBMISSION_ID)
        .mockExam(exam)
        .user(user)
        .answers("[{\"questionId\":\"" + QUESTION_ID + "\",\"selectedOption\":\"B\"}]")
        .score(5)
        .totalPoints(5)
        .timeTaken(600)
        .grade("A+")
        .submittedAt(LocalDateTime.now())
        .build();
  }

  // getExamList()

  @Nested
  @DisplayName("getExamList()")
  class GetExamList {

    @Test
    @DisplayName("모의고사 목록을 반환한다")
    void getExamList_returnsList() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID))
          .willReturn(List.of(exam));

      // when
      List<MockExamListResponse> result = mockExamService.getExamList(USER_ID, COURSE_ID);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).id()).isEqualTo(EXAM_ID);
      assertThat(result.get(0).title()).isEqualTo("수학 기초 - midterm 모의고사");
      assertThat(result.get(0).status()).isEqualTo(MockExamStatus.READY);
    }

    @Test
    @DisplayName("타인 과외 접근 시 CRS_001 예외를 던진다")
    void getExamList_otherUser_throwsCrs001() {
      // given
      User owner = makeUser(USER_ID);
      Course course = makeCourse(owner);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

      // when & then
      assertThatThrownBy(() -> mockExamService.getExamList(OTHER_ID, COURSE_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_001));
    }

    @Test
    @DisplayName("존재하지 않는 과외 조회 시 CRS_001 예외를 던진다")
    void getExamList_courseNotFound_throwsCrs001() {
      // given
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> mockExamService.getExamList(USER_ID, COURSE_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_001));
    }
  }

  // generateExam()

  @Nested
  @DisplayName("generateExam()")
  class GenerateExam {

    @Test
    @DisplayName("모의고사를 GENERATING 상태로 생성하고 FastAPI에 요청한다")
    void generateExam_createsGenerating() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.save(any(MockExam.class))).willAnswer(inv -> inv.getArgument(0));
      given(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
          .willReturn(ResponseEntity.accepted().build());

      GenerateExamRequest request =
          new GenerateExamRequest("midterm", 10, "medium", List.of("집합"), 30);

      // when
      MockExamListResponse result = mockExamService.generateExam(USER_ID, COURSE_ID, request);

      // then
      assertThat(result.status()).isEqualTo(MockExamStatus.GENERATING);
      assertThat(result.examType()).isEqualTo("midterm");
      assertThat(result.questionCount()).isEqualTo(10);
      verify(mockExamRepository).save(any(MockExam.class));
    }

    @Test
    @DisplayName("FastAPI 호출 실패 시 MEX_003 예외를 던진다")
    void generateExam_fastapiFail_throwsMex003() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.save(any(MockExam.class))).willAnswer(inv -> inv.getArgument(0));
      given(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
          .willThrow(new RestClientException("Connection refused"));

      GenerateExamRequest request = new GenerateExamRequest("midterm", 10, "medium", null, 30);

      // when & then
      assertThatThrownBy(() -> mockExamService.generateExam(USER_ID, COURSE_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(MockExamErrorCode.MEX_003));
    }
  }

  // getExamDetail()

  @Nested
  @DisplayName("getExamDetail()")
  class GetExamDetail {

    @Test
    @DisplayName("READY 상태일 때 문제를 반환한다")
    void getExamDetail_ready_returnsDetail() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);
      MockExamQuestion question = makeQuestion(exam);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(mockExamQuestionRepository.findByMockExamIdOrderByQuestionIdxAsc(EXAM_ID))
          .willReturn(List.of(question));

      // when
      MockExamDetailResponse result = mockExamService.getExamDetail(USER_ID, COURSE_ID, EXAM_ID);

      // then
      assertThat(result.id()).isEqualTo(EXAM_ID);
      assertThat(result.status()).isEqualTo(MockExamStatus.READY);
      assertThat(result.questions()).hasSize(1);
      assertThat(result.questions().get(0).questionText()).isEqualTo("1 + 1 = ?");
    }

    @Test
    @DisplayName("GENERATING 상태일 때 MEX_002 예외를 던진다")
    void getExamDetail_generating_throwsMex002() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.GENERATING);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));

      // when & then
      assertThatThrownBy(() -> mockExamService.getExamDetail(USER_ID, COURSE_ID, EXAM_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(MockExamErrorCode.MEX_002));
    }

    @Test
    @DisplayName("존재하지 않는 모의고사 조회 시 MEX_001 예외를 던진다")
    void getExamDetail_notFound_throwsMex001() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> mockExamService.getExamDetail(USER_ID, COURSE_ID, EXAM_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(MockExamErrorCode.MEX_001));
    }

    @Test
    @DisplayName("다른 과외의 모의고사 조회 시 MEX_001 예외를 던진다")
    void getExamDetail_wrongCourse_throwsMex001() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      /* 다른 과외에 속한 모의고사 */
      Course otherCourse =
          Course.builder()
              .id("crs_OTHER00000000000000000000")
              .user(user)
              .courseName("영어")
              .subject("영어")
              .status(CourseStatus.ACTIVE)
              .totalChapters(1)
              .completedSlides(0)
              .progressPercent(BigDecimal.ZERO)
              .build();
      MockExam exam = makeExam(otherCourse, MockExamStatus.READY);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));

      // when & then
      assertThatThrownBy(() -> mockExamService.getExamDetail(USER_ID, COURSE_ID, EXAM_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(MockExamErrorCode.MEX_001));
    }
  }

  // submitExam()

  @Nested
  @DisplayName("submitExam()")
  class SubmitExam {

    @Test
    @DisplayName("답안을 제출하고 채점 결과를 반환한다")
    void submitExam_success() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);
      MockExamQuestion question = makeQuestion(exam);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(mockExamSubmissionRepository.findByMockExamIdAndUserId(EXAM_ID, USER_ID))
          .willReturn(Optional.empty());
      given(mockExamQuestionRepository.findByMockExamIdOrderByQuestionIdxAsc(EXAM_ID))
          .willReturn(List.of(question));
      given(mockExamSubmissionRepository.save(any(MockExamSubmission.class)))
          .willAnswer(inv -> inv.getArgument(0));

      SubmitExamRequest request =
          new SubmitExamRequest(List.of(new SubmitExamRequest.AnswerItem(QUESTION_ID, "B")), 600);

      // when
      ExamResultResponse result = mockExamService.submitExam(USER_ID, COURSE_ID, EXAM_ID, request);

      // then
      assertThat(result.examId()).isEqualTo(EXAM_ID);
      assertThat(result.score()).isEqualTo(5);
      assertThat(result.totalPoints()).isEqualTo(5);
      assertThat(result.grade()).isEqualTo("A+");
      assertThat(result.questionResults()).hasSize(1);
      assertThat(result.questionResults().get(0).correct()).isTrue();
      verify(mockExamSubmissionRepository).save(any(MockExamSubmission.class));
    }

    @Test
    @DisplayName("오답 제출 시 점수가 0이다")
    void submitExam_wrongAnswer() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);
      MockExamQuestion question = makeQuestion(exam);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(mockExamSubmissionRepository.findByMockExamIdAndUserId(EXAM_ID, USER_ID))
          .willReturn(Optional.empty());
      given(mockExamQuestionRepository.findByMockExamIdOrderByQuestionIdxAsc(EXAM_ID))
          .willReturn(List.of(question));
      given(mockExamSubmissionRepository.save(any(MockExamSubmission.class)))
          .willAnswer(inv -> inv.getArgument(0));

      SubmitExamRequest request =
          new SubmitExamRequest(List.of(new SubmitExamRequest.AnswerItem(QUESTION_ID, "A")), 600);

      // when
      ExamResultResponse result = mockExamService.submitExam(USER_ID, COURSE_ID, EXAM_ID, request);

      // then
      assertThat(result.score()).isZero();
      assertThat(result.grade()).isEqualTo("F");
      assertThat(result.questionResults().get(0).correct()).isFalse();
    }

    @Test
    @DisplayName("GENERATING 상태에서 제출 시 MEX_002 예외를 던진다")
    void submitExam_generating_throwsMex002() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.GENERATING);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));

      SubmitExamRequest request =
          new SubmitExamRequest(List.of(new SubmitExamRequest.AnswerItem(QUESTION_ID, "B")), 600);

      // when & then
      assertThatThrownBy(() -> mockExamService.submitExam(USER_ID, COURSE_ID, EXAM_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(MockExamErrorCode.MEX_002));
    }

    @Test
    @DisplayName("문항이 없으면 MEX_006 예외를 던진다")
    void submitExam_emptyQuestions_throwsMex006() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(mockExamSubmissionRepository.findByMockExamIdAndUserId(EXAM_ID, USER_ID))
          .willReturn(Optional.empty());
      given(mockExamQuestionRepository.findByMockExamIdOrderByQuestionIdxAsc(EXAM_ID))
          .willReturn(List.of());

      SubmitExamRequest request =
          new SubmitExamRequest(List.of(new SubmitExamRequest.AnswerItem(QUESTION_ID, "B")), 600);

      // when & then
      assertThatThrownBy(() -> mockExamService.submitExam(USER_ID, COURSE_ID, EXAM_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(MockExamErrorCode.MEX_006));
    }

    @Test
    @DisplayName("이미 제출한 모의고사에 재제출 시 MEX_004 예외를 던진다")
    void submitExam_alreadySubmitted_throwsMex004() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);
      MockExamSubmission existing = makeSubmission(exam, user);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(mockExamSubmissionRepository.findByMockExamIdAndUserId(EXAM_ID, USER_ID))
          .willReturn(Optional.of(existing));

      SubmitExamRequest request =
          new SubmitExamRequest(List.of(new SubmitExamRequest.AnswerItem(QUESTION_ID, "B")), 600);

      // when & then
      assertThatThrownBy(() -> mockExamService.submitExam(USER_ID, COURSE_ID, EXAM_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(MockExamErrorCode.MEX_004));
    }
  }

  // getExamResult()

  @Nested
  @DisplayName("getExamResult()")
  class GetExamResult {

    @Test
    @DisplayName("제출 결과를 상세 반환한다")
    void getExamResult_returnsResult() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.COMPLETED);
      MockExamSubmission submission = makeSubmission(exam, user);
      MockExamQuestion question = makeQuestion(exam);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(mockExamSubmissionRepository.findByMockExamIdAndUserId(EXAM_ID, USER_ID))
          .willReturn(Optional.of(submission));
      given(mockExamQuestionRepository.findByMockExamIdOrderByQuestionIdxAsc(EXAM_ID))
          .willReturn(List.of(question));

      // when
      ExamResultResponse result = mockExamService.getExamResult(USER_ID, COURSE_ID, EXAM_ID);

      // then
      assertThat(result.examId()).isEqualTo(EXAM_ID);
      assertThat(result.score()).isEqualTo(5);
      assertThat(result.grade()).isEqualTo("A+");
      assertThat(result.questionResults()).hasSize(1);
    }

    @Test
    @DisplayName("제출 결과가 없으면 MEX_005 예외를 던진다")
    void getExamResult_noSubmission_throwsMex005() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(mockExamSubmissionRepository.findByMockExamIdAndUserId(EXAM_ID, USER_ID))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> mockExamService.getExamResult(USER_ID, COURSE_ID, EXAM_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(MockExamErrorCode.MEX_005));
    }
  }
}
