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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.coin.service.CoinService;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.examgrading.dto.request.AttemptSubmitRequest;
import com.swez.backend.domain.examgrading.dto.request.ExamGenerateRequest;
import com.swez.backend.domain.examgrading.dto.response.AttemptDetailResponse;
import com.swez.backend.domain.examgrading.dto.response.AttemptQuestionResponse;
import com.swez.backend.domain.examgrading.dto.response.ExamGenerateStartResponse;
import com.swez.backend.domain.examgrading.dto.response.ExamSubmissionGradeResponse;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.enums.AttemptStatus;
import com.swez.backend.domain.examgrading.model.GradingStatus;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;
import com.swez.backend.domain.examgrading.service.ExamAttemptDetailService;
import com.swez.backend.domain.examgrading.service.ExamAttemptGenerationService;
import com.swez.backend.domain.examgrading.service.ExamAttemptSubmitService;
import com.swez.backend.domain.mockexam.dto.request.GenerateExamRequest;
import com.swez.backend.domain.mockexam.dto.request.SubmitExamRequest;
import com.swez.backend.domain.mockexam.dto.response.ExamResultResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamDetailResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamListResponse;
import com.swez.backend.domain.mockexam.entity.MockExam;
import com.swez.backend.domain.mockexam.enums.MockExamStatus;
import com.swez.backend.domain.mockexam.mapper.MockExamMapper;
import com.swez.backend.domain.mockexam.repository.MockExamQuestionRepository;
import com.swez.backend.domain.mockexam.repository.MockExamRepository;
import com.swez.backend.domain.mockexam.repository.MockExamSubmissionRepository;

/**
 * MockExamService cutover 분기(flag=true) 단위 테스트.
 *
 * <p>flag=false 레거시 경로 회귀는 {@link MockExamServiceTest}에서 검증하므로 여기서는 flag=true 경로만 다룬다:
 *
 * <ul>
 *   <li>generateExam: ExamAttemptGenerationService 위임 호출 검증
 *   <li>submitExam: ExamAttemptSubmitService 위임 호출 및 ExamResultResponse 변환 검증
 *   <li>getExamDetail: ExamAttemptDetailService 위임 호출 및 MockExamDetailResponse 변환 검증
 * </ul>
 */
@DisplayName("MockExamService 커트오버(cutover=true) 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MockExamServiceCutoverTest {

  @Mock private MockExamRepository mockExamRepository;
  @Mock private MockExamQuestionRepository mockExamQuestionRepository;
  @Mock private MockExamSubmissionRepository mockExamSubmissionRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private MockExamMapper mockExamMapper;
  @Mock private RestTemplate restTemplate;
  @Mock private CoinService coinService;
  @Mock private ExamAttemptGenerationService examAttemptGenerationService;
  @Mock private ExamAttemptSubmitService examAttemptSubmitService;
  @Mock private ExamAttemptDetailService examAttemptDetailService;
  @Mock private ExamAttemptRepository examAttemptRepository;

  @InjectMocks private MockExamService mockExamService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String COURSE_ID = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String EXAM_ID = "mex_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String ATTEMPT_ID = "eat_01ARZ3NDEKTSV4RRFFQ69G5FAV";

  @BeforeEach
  void setUp() {
    // 실제 매퍼·ObjectMapper 주입
    ReflectionTestUtils.setField(mockExamService, "mockExamMapper", new MockExamMapper());
    ReflectionTestUtils.setField(mockExamService, "objectMapper", new ObjectMapper());
    ReflectionTestUtils.setField(mockExamService, "fastapiBaseUrl", "http://localhost:8000");
    // cutover=true. @Value 필드라 ReflectionTestUtils로 직접 넣는다
    ReflectionTestUtils.setField(mockExamService, "cutoverEnabled", true);
    // 의존성 주입 (MockitoExtension이 @Mock으로 생성한 mock을 @InjectMocks로 주입)
    ReflectionTestUtils.setField(
        mockExamService, "examAttemptGenerationService", examAttemptGenerationService);
    ReflectionTestUtils.setField(
        mockExamService, "examAttemptSubmitService", examAttemptSubmitService);
    ReflectionTestUtils.setField(
        mockExamService, "examAttemptDetailService", examAttemptDetailService);
    ReflectionTestUtils.setField(mockExamService, "examAttemptRepository", examAttemptRepository);
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

  private ExamAttempt makeAttempt() {
    return ExamAttempt.builder()
        .attemptId(ATTEMPT_ID)
        .examId("exam_abc123")
        .mockExamId(EXAM_ID)
        .status(AttemptStatus.READY)
        .passPercentage(BigDecimal.valueOf(60))
        .build();
  }

  // getExamList() cutover

  @Nested
  @DisplayName("getExamList() — cutover=true")
  class GetExamListCutover {

    @Test
    @DisplayName("cutover=true이면 목록 응답에 attemptId가 포함된다")
    void getExamList_cutoverTrue_responseContainsAttemptId() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.GENERATING);
      ExamAttempt attempt = makeAttempt(); // AttemptStatus.READY, ATTEMPT_ID

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID))
          .willReturn(List.of(exam));
      given(examAttemptRepository.findLatestByMockExamId(EXAM_ID)).willReturn(Optional.of(attempt));

      // when
      List<MockExamListResponse> result = mockExamService.getExamList(USER_ID, COURSE_ID);

      // then: cutover 경로에서 attemptId가 포함되어야 한다
      assertThat(result).hasSize(1);
      assertThat(result.get(0).attemptId())
          .as("cutover=true 경로에서 목록 응답의 attemptId는 null이 아니어야 한다")
          .isEqualTo(ATTEMPT_ID);
    }

    @Test
    @DisplayName("attempt.status=READY이고 mock_exam.status=GENERATING이면 목록에서 READY로 동기화된다")
    void getExamList_cutoverTrue_syncsMockExamStatusFromGeneratingToReady() {
      // given: mock_exam은 GENERATING, attempt는 READY인 stale 상태
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.GENERATING);
      ExamAttempt readyAttempt = makeAttempt(); // AttemptStatus.READY

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID))
          .willReturn(List.of(exam));
      given(examAttemptRepository.findLatestByMockExamId(EXAM_ID))
          .willReturn(Optional.of(readyAttempt));

      // when
      List<MockExamListResponse> result = mockExamService.getExamList(USER_ID, COURSE_ID);

      // then: mock_exam.status가 READY로 동기화되어 응답에 반영되어야 한다
      assertThat(result).hasSize(1);
      assertThat(result.get(0).status())
          .as("attempt=READY이면 목록 응답의 status는 READY이어야 한다")
          .isEqualTo(MockExamStatus.READY);
    }

    @Test
    @DisplayName("attempt가 없으면 attemptId=null인 레거시 응답을 반환한다")
    void getExamList_cutoverTrue_noAttempt_returnsNullAttemptId() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.GENERATING);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID))
          .willReturn(List.of(exam));
      given(examAttemptRepository.findLatestByMockExamId(EXAM_ID)).willReturn(Optional.empty());

      // when
      List<MockExamListResponse> result = mockExamService.getExamList(USER_ID, COURSE_ID);

      // then: attempt 없으면 attemptId=null
      assertThat(result).hasSize(1);
      assertThat(result.get(0).attemptId()).as("attempt가 없으면 attemptId는 null이어야 한다").isNull();
    }

    @Test
    @DisplayName("attempt.status=GRADED이면 mock_exam.status가 COMPLETED로 동기화된다 (엣지버그 수정)")
    void getExamList_cutoverTrue_gradedAttempt_syncsMockExamStatusToCompleted() {
      // given: mock_exam은 READY(stale), attempt는 GRADED라 재제출이 일어날 수 있는 경우
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);
      ExamAttempt gradedAttempt =
          ExamAttempt.builder()
              .attemptId(ATTEMPT_ID)
              .examId("exam_abc123")
              .mockExamId(EXAM_ID)
              .status(AttemptStatus.GRADED)
              .passPercentage(BigDecimal.valueOf(60))
              .build();

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID))
          .willReturn(List.of(exam));
      given(examAttemptRepository.findLatestByMockExamId(EXAM_ID))
          .willReturn(Optional.of(gradedAttempt));

      // when
      List<MockExamListResponse> result = mockExamService.getExamList(USER_ID, COURSE_ID);

      // then: GRADED attempt이면 mock_exam.status가 COMPLETED로 동기화되어야 한다
      // 프론트는 COMPLETED 상태를 보고 "결과 보기"로 라우팅하므로 재제출 시도가 없어진다
      assertThat(result).hasSize(1);
      assertThat(result.get(0).status())
          .as("attempt=GRADED이면 목록 응답의 status는 COMPLETED이어야 한다")
          .isEqualTo(MockExamStatus.COMPLETED);
    }

    @Test
    @DisplayName("attempt.status=MANUAL_REVIEW_REQUIRED이면 mock_exam.status가 COMPLETED로 동기화된다")
    void getExamList_cutoverTrue_manualReviewAttempt_syncsMockExamStatusToCompleted() {
      // given: mock_exam은 READY(stale), attempt는 MANUAL_REVIEW_REQUIRED
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);
      ExamAttempt manualAttempt =
          ExamAttempt.builder()
              .attemptId(ATTEMPT_ID)
              .examId("exam_abc123")
              .mockExamId(EXAM_ID)
              .status(AttemptStatus.MANUAL_REVIEW_REQUIRED)
              .passPercentage(BigDecimal.valueOf(60))
              .build();

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID))
          .willReturn(List.of(exam));
      given(examAttemptRepository.findLatestByMockExamId(EXAM_ID))
          .willReturn(Optional.of(manualAttempt));

      // when
      List<MockExamListResponse> result = mockExamService.getExamList(USER_ID, COURSE_ID);

      // then: MANUAL_REVIEW_REQUIRED도 채점 완료 범주이므로 COMPLETED로 동기화된다
      assertThat(result).hasSize(1);
      assertThat(result.get(0).status())
          .as("attempt=MANUAL_REVIEW_REQUIRED이면 목록 응답의 status는 COMPLETED이어야 한다")
          .isEqualTo(MockExamStatus.COMPLETED);
    }

    @Test
    @DisplayName("attempt.status=READY이면 mock_exam.status가 COMPLETED로 변경되지 않는다 — READY 불변식")
    void getExamList_cutoverTrue_readyAttempt_doesNotSyncToCompleted() {
      // given: mock_exam은 GENERATING, attempt는 READY인 시험 준비 단계
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.GENERATING);
      ExamAttempt readyAttempt = makeAttempt(); // AttemptStatus.READY

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findByCourseIdOrderByCreatedAtDesc(COURSE_ID))
          .willReturn(List.of(exam));
      given(examAttemptRepository.findLatestByMockExamId(EXAM_ID))
          .willReturn(Optional.of(readyAttempt));

      // when
      List<MockExamListResponse> result = mockExamService.getExamList(USER_ID, COURSE_ID);

      // then: READY attempt는 COMPLETED가 아닌 READY로 동기화되어야 한다
      assertThat(result).hasSize(1);
      assertThat(result.get(0).status())
          .as("attempt=READY이면 목록 응답의 status는 READY이어야 하며 COMPLETED가 되어서는 안 된다")
          .isEqualTo(MockExamStatus.READY);
    }
  }

  // generateExam() cutover

  @Nested
  @DisplayName("generateExam() — cutover=true")
  class GenerateExamCutover {

    @Test
    @DisplayName("cutover=true이면 ExamAttemptGenerationService.startGeneration()을 호출한다")
    void generateExam_cutoverTrue_delegatesToGenerationService() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.save(any(MockExam.class))).willAnswer(inv -> inv.getArgument(0));
      given(
              examAttemptGenerationService.startGeneration(
                  any(ExamGenerateRequest.class), eq(USER_ID)))
          .willReturn(new ExamGenerateStartResponse(ATTEMPT_ID, AttemptStatus.GENERATING));

      GenerateExamRequest request =
          new GenerateExamRequest("midterm", 10, "medium", List.of("집합"), 30);

      // when
      MockExamListResponse result = mockExamService.generateExam(USER_ID, COURSE_ID, request);

      // then: ExamAttemptGenerationService가 호출되어야 한다
      verify(examAttemptGenerationService)
          .startGeneration(any(ExamGenerateRequest.class), eq(USER_ID));
      // 레거시 FastAPI 직접 호출은 발생하지 않아야 한다
      verify(restTemplate, never()).postForEntity(anyString(), any(), any());
      assertThat(result.status()).isEqualTo(MockExamStatus.GENERATING);
    }

    @Test
    @DisplayName("cutover=true이면 응답에 attemptId가 포함되어 프론트가 examgrading 경로를 사용할 수 있다")
    void generateExam_cutoverTrue_responseContainsAttemptId() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.save(any(MockExam.class))).willAnswer(inv -> inv.getArgument(0));
      given(
              examAttemptGenerationService.startGeneration(
                  any(ExamGenerateRequest.class), eq(USER_ID)))
          .willReturn(new ExamGenerateStartResponse(ATTEMPT_ID, AttemptStatus.GENERATING));

      GenerateExamRequest request =
          new GenerateExamRequest("midterm", 10, "medium", List.of("집합"), 30);

      // when
      MockExamListResponse result = mockExamService.generateExam(USER_ID, COURSE_ID, request);

      // then: cutover 경로에서 attemptId가 응답에 포함되어야 한다
      // 프론트는 attemptId != null이면 examgrading 경로(detail·submit·result)를 사용한다
      assertThat(result.attemptId()).isEqualTo(ATTEMPT_ID);
    }

    @Test
    @DisplayName("cutover=true이고 생성 위임 시 mockExamId가 exam.getId()로 설정된다")
    void generateExam_cutoverTrue_passesMockExamIdToRequest() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.save(any(MockExam.class))).willAnswer(inv -> inv.getArgument(0));
      given(
              examAttemptGenerationService.startGeneration(
                  any(ExamGenerateRequest.class), anyString()))
          .willReturn(new ExamGenerateStartResponse(ATTEMPT_ID, AttemptStatus.GENERATING));

      GenerateExamRequest request = new GenerateExamRequest("midterm", 10, "medium", null, 30);

      // when
      mockExamService.generateExam(USER_ID, COURSE_ID, request);

      // then: mockExamId가 mex_ 접두사로 시작해야 한다
      org.mockito.ArgumentCaptor<ExamGenerateRequest> captor =
          org.mockito.ArgumentCaptor.forClass(ExamGenerateRequest.class);
      verify(examAttemptGenerationService).startGeneration(captor.capture(), anyString());
      assertThat(captor.getValue().mockExamId()).startsWith("mex_");
    }
  }

  // submitExam() cutover

  @Nested
  @DisplayName("submitExam() — cutover=true")
  class SubmitExamCutover {

    @Test
    @DisplayName("cutover=true이면 ExamAttemptSubmitService.submitAndGrade()를 호출하고 결과를 반환한다")
    void submitExam_cutoverTrue_delegatesToSubmitService() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);
      ExamAttempt attempt = makeAttempt();

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(mockExamSubmissionRepository.findByMockExamIdAndUserId(EXAM_ID, USER_ID))
          .willReturn(Optional.empty());
      given(examAttemptRepository.findLatestByMockExamId(EXAM_ID)).willReturn(Optional.of(attempt));

      ExamSubmissionGradeResponse gradeResponse =
          ExamSubmissionGradeResponse.builder()
              .attemptId(ATTEMPT_ID)
              .examId("exam_abc123")
              .totalScore(BigDecimal.valueOf(40))
              .maxScore(BigDecimal.valueOf(50))
              .percentage(BigDecimal.valueOf(80))
              .passed(true)
              .gradingStatus(GradingStatus.GRADED)
              .gradedAt(OffsetDateTime.now())
              .results(List.of())
              .build();

      given(
              examAttemptSubmitService.submitAndGrade(
                  eq(ATTEMPT_ID), any(AttemptSubmitRequest.class), eq(USER_ID)))
          .willReturn(gradeResponse);

      SubmitExamRequest request =
          new SubmitExamRequest(List.of(new SubmitExamRequest.AnswerItem("mq_001", "A")), 600);

      // when
      ExamResultResponse result = mockExamService.submitExam(USER_ID, COURSE_ID, EXAM_ID, request);

      // then
      verify(examAttemptSubmitService)
          .submitAndGrade(eq(ATTEMPT_ID), any(AttemptSubmitRequest.class), eq(USER_ID));
      // 레거시 문항 조회는 발생하지 않아야 한다
      verify(mockExamQuestionRepository, never())
          .findByMockExamIdOrderByQuestionIdxAsc(anyString());
      assertThat(result.examId()).isEqualTo(EXAM_ID);
      assertThat(result.score()).isEqualTo(40);
      assertThat(result.totalPoints()).isEqualTo(50);
      assertThat(result.grade()).isEqualTo("B+"); // 80% → B+
    }

    @Test
    @DisplayName("cutover=true이나 attempt가 없으면 레거시 경로로 폴백한다")
    void submitExam_cutoverTrue_noAttempt_fallsBackToLegacy() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(mockExamSubmissionRepository.findByMockExamIdAndUserId(EXAM_ID, USER_ID))
          .willReturn(Optional.empty());
      given(examAttemptRepository.findLatestByMockExamId(EXAM_ID)).willReturn(Optional.empty());
      // 레거시 경로 필요한 mock
      given(mockExamQuestionRepository.findByMockExamIdOrderByQuestionIdxAsc(EXAM_ID))
          .willReturn(List.of());

      SubmitExamRequest request =
          new SubmitExamRequest(List.of(new SubmitExamRequest.AnswerItem("mq_001", "A")), 600);

      // when & then: 레거시 경로에서 문항 없음(MEX_006) 예외
      assertThatThrownBy(() -> mockExamService.submitExam(USER_ID, COURSE_ID, EXAM_ID, request))
          .isInstanceOf(com.swez.backend.global.exception.CustomException.class)
          .satisfies(
              ex ->
                  assertThat(
                          ((com.swez.backend.global.exception.CustomException) ex).getErrorCode())
                      .isEqualTo(
                          com.swez.backend.domain.mockexam.exception.MockExamErrorCode.MEX_006));
      // examAttemptSubmitService는 호출되지 않아야 한다
      verify(examAttemptSubmitService, never()).submitAndGrade(anyString(), any(), anyString());
    }
  }

  // getExamDetail() cutover

  @Nested
  @DisplayName("getExamDetail() — cutover=true")
  class GetExamDetailCutover {

    @Test
    @DisplayName("cutover=true이면 ExamAttemptDetailService.getAttemptDetail()을 호출하고 결과를 반환한다")
    void getExamDetail_cutoverTrue_delegatesToDetailService() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);
      ExamAttempt attempt = makeAttempt();

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(examAttemptRepository.findLatestByMockExamId(EXAM_ID)).willReturn(Optional.of(attempt));

      AttemptDetailResponse attemptDetail =
          AttemptDetailResponse.builder()
              .attemptId(ATTEMPT_ID)
              .examId("exam_abc123")
              .status(AttemptStatus.READY)
              .passPercentage(BigDecimal.valueOf(60))
              .createdAt(OffsetDateTime.now())
              .questions(
                  List.of(
                      AttemptQuestionResponse.builder()
                          .questionId("q_001")
                          .templateId("multiple_choice")
                          .displayOrder(1)
                          .stem("1 + 1 = ?")
                          .points(BigDecimal.valueOf(5))
                          .options(List.of())
                          .build()))
              .build();

      given(examAttemptDetailService.getAttemptDetail(ATTEMPT_ID, USER_ID))
          .willReturn(attemptDetail);

      // when
      MockExamDetailResponse result = mockExamService.getExamDetail(USER_ID, COURSE_ID, EXAM_ID);

      // then
      verify(examAttemptDetailService).getAttemptDetail(ATTEMPT_ID, USER_ID);
      // 레거시 문항 조회는 발생하지 않아야 한다
      verify(mockExamQuestionRepository, never())
          .findByMockExamIdOrderByQuestionIdxAsc(anyString());
      assertThat(result.id()).isEqualTo(EXAM_ID);
      assertThat(result.questions()).hasSize(1);
      assertThat(result.questions().get(0).questionText()).isEqualTo("1 + 1 = ?");
    }

    @Test
    @DisplayName("cutover=true이나 attempt가 없으면 레거시 경로로 폴백한다")
    void getExamDetail_cutoverTrue_noAttempt_fallsBackToLegacy() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      MockExam exam = makeExam(course, MockExamStatus.READY);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(mockExamRepository.findById(EXAM_ID)).willReturn(Optional.of(exam));
      given(examAttemptRepository.findLatestByMockExamId(EXAM_ID)).willReturn(Optional.empty());
      // 레거시 경로 mock
      given(mockExamQuestionRepository.findByMockExamIdOrderByQuestionIdxAsc(EXAM_ID))
          .willReturn(List.of());

      // when
      MockExamDetailResponse result = mockExamService.getExamDetail(USER_ID, COURSE_ID, EXAM_ID);

      // then: 레거시 경로로 폴백하여 결과가 반환된다
      verify(examAttemptDetailService, never()).getAttemptDetail(anyString(), anyString());
      assertThat(result.id()).isEqualTo(EXAM_ID);
    }
  }
}
