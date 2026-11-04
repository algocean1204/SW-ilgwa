/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.coin.dto.request.CoinUseExamRequest;
import com.swez.backend.domain.coin.service.CoinService;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.exception.CourseErrorCode;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.examgrading.dto.request.AttemptSubmitRequest;
import com.swez.backend.domain.examgrading.dto.request.ExamGenerateRequest;
import com.swez.backend.domain.examgrading.dto.request.SubmittedAnswerRequest;
import com.swez.backend.domain.examgrading.dto.response.AttemptDetailResponse;
import com.swez.backend.domain.examgrading.dto.response.ExamSubmissionGradeResponse;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;
import com.swez.backend.domain.examgrading.service.ExamAttemptDetailService;
import com.swez.backend.domain.examgrading.service.ExamAttemptGenerationService;
import com.swez.backend.domain.examgrading.service.ExamAttemptSubmitService;
import com.swez.backend.domain.mockexam.dto.request.ExamAnalysisRequest;
import com.swez.backend.domain.mockexam.dto.request.GenerateExamRequest;
import com.swez.backend.domain.mockexam.dto.request.SubmitExamRequest;
import com.swez.backend.domain.mockexam.dto.response.ExamAnalysisResponse;
import com.swez.backend.domain.mockexam.dto.response.ExamResultResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamDetailResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamListResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamQuestionResponse;
import com.swez.backend.domain.mockexam.entity.MockExam;
import com.swez.backend.domain.mockexam.entity.MockExamQuestion;
import com.swez.backend.domain.mockexam.entity.MockExamSubmission;
import com.swez.backend.domain.mockexam.enums.MockExamStatus;
import com.swez.backend.domain.mockexam.exception.MockExamErrorCode;
import com.swez.backend.domain.mockexam.mapper.MockExamMapper;
import com.swez.backend.domain.mockexam.repository.MockExamQuestionRepository;
import com.swez.backend.domain.mockexam.repository.MockExamRepository;
import com.swez.backend.domain.mockexam.repository.MockExamSubmissionRepository;
import com.swez.backend.global.config.FastApiAuthInterceptor;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.logging.TraceIdRestTemplateInterceptor;
import com.swez.backend.global.util.IdGenerator;

import lombok.extern.slf4j.Slf4j;

/** 모의고사 생성·조회·제출·결과를 처리한다. */
@Slf4j
@Service
@Transactional
public class MockExamService {

  private final MockExamRepository mockExamRepository;
  private final MockExamQuestionRepository mockExamQuestionRepository;
  private final MockExamSubmissionRepository mockExamSubmissionRepository;
  private final CourseRepository courseRepository;
  private final MockExamMapper mockExamMapper;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  /** 모의고사 생성권(코인)을 서버에서 직접 차감해 클라이언트가 우회하지 못하게 한다. */
  private final CoinService coinService;

  /** FastAPI 베이스 URL (application.yml 의 app.fastapi.base-url) */
  @Value("${app.fastapi.base-url}")
  private String fastapiBaseUrl;

  /** FastAPI 인증용 API 키. 빈 값이면 전용 인터셉터가 헤더를 붙이지 않는다. */
  @Value("${app.fastapi.api-key:}")
  private String fastapiApiKey;

  // cutover 관련 의존성은 @Value/@Autowired 필드 주입을 쓴다.
  // enabled=false(기본값)이면 아래 서비스를 호출하지 않으므로 null 이어도 된다.

  /**
   * Cutover 기능 플래그 (app.examgrading.cutover.enabled).
   *
   * <p>true이면 generateExam/submitExam/getExamDetail을 examgrading 경로로 위임한다. false(기본값)이면 레거시 MCQ 경로를
   * 유지한다.
   */
  @Value("${app.examgrading.cutover.enabled:false}")
  private boolean cutoverEnabled;

  /** examgrading 생성 서비스. cutover=true 경로에서만 호출하며 FastAPI 미설정 시 null */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private ExamAttemptGenerationService examAttemptGenerationService;

  /** examgrading 제출·채점 서비스. cutover=true 경로에서만 호출하며 FastAPI 미설정 시 null */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private ExamAttemptSubmitService examAttemptSubmitService;

  /** examgrading 상세 조회 서비스. cutover=true 경로에서만 호출하며 FastAPI 미설정 시 null */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private ExamAttemptDetailService examAttemptDetailService;

  /** mockExamId 로 응시를 조회하는 ExamAttempt 리포지토리. FastAPI 미설정 시 null */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private ExamAttemptRepository examAttemptRepository;

  public MockExamService(
      MockExamRepository mockExamRepository,
      MockExamQuestionRepository mockExamQuestionRepository,
      MockExamSubmissionRepository mockExamSubmissionRepository,
      CourseRepository courseRepository,
      MockExamMapper mockExamMapper,
      @Qualifier("fastapiRestTemplate") RestTemplate restTemplate,
      ObjectMapper objectMapper,
      CoinService coinService) {
    this.mockExamRepository = mockExamRepository;
    this.mockExamQuestionRepository = mockExamQuestionRepository;
    this.mockExamSubmissionRepository = mockExamSubmissionRepository;
    this.courseRepository = courseRepository;
    this.mockExamMapper = mockExamMapper;
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
    this.coinService = coinService;
  }

  /** AI 총평(약점 진단) 분석 호출의 연결 타임아웃. LLM 생성이라 응답이 느려 넉넉히 잡는다. */
  private static final Duration ANALYSIS_CONNECT_TIMEOUT = Duration.ofSeconds(5);

  /** AI 총평(약점 진단) 분석 호출의 읽기 타임아웃. 생성에 10~30초 걸려 60초까지 허용한다. */
  private static final Duration ANALYSIS_READ_TIMEOUT = Duration.ofSeconds(60);

  /**
   * '객관식 모의고사'(examType=multiple_choice) 전용 문항 템플릿 ID 목록.
   *
   * <p>사용자가 객관식만 고른 경우 FastAPI에 이 목록을 그대로 전달한다. '최종 점검 모의고사'(examType=final)
   * 등 그 외 유형은 빈 목록을 보내 FastAPI plan_exam_node 의 기본 혼합 유형(객관식·단답·빈칸·OX·서술) 가중치
   * 분배를 따른다. {@link #resolveQuestionTypes} 참조.
   */
  private static final List<String> MC_ONLY_QUESTION_TYPES =
      List.of(
          "ko_multiple_choice_5", // 5지 선다 객관식
          "ko_multiple_choice_4"); // 4지 선다 객관식

  /** 객관식 전용 examType 값. 프론트 EXAM_TYPE_OPTIONS 의 'multiple_choice'와 1:1 매핑된다. */
  private static final String EXAM_TYPE_MULTIPLE_CHOICE = "multiple_choice";

  /**
   * examType에 따라 FastAPI에 전달할 문항 유형 목록을 결정한다.
   *
   * @param examType 프론트 시험 유형 (multiple_choice/final)
   * @return 객관식 선택 시 객관식 템플릿 목록, 그 외(최종모의고사 포함)는 빈 목록(=FastAPI 자동 분배)
   */
  private static List<String> resolveQuestionTypes(String examType) {
    if (examType != null && EXAM_TYPE_MULTIPLE_CHOICE.equalsIgnoreCase(examType.trim())) {
      return MC_ONLY_QUESTION_TYPES;
    }
    return List.of();
  }

  /**
   * FastAPI generate-async에 전달할 과목명을 결정한다.
   *
   * <p>FastAPI MockGenerateAsyncRequest.subject는 min_length=1이라 빈 문자열을 422로 거부한다. 과목 미설정
   * 코스(subject 컬럼 nullable)는 과외명 → "모의고사" 순으로 폴백해 생성 트리거가 실패하지 않게 한다.
   *
   * @return 비어 있지 않은 과목명
   */
  private static String resolveSubjectForGeneration(Course course) {
    if (course != null) {
      if (course.getSubject() != null && !course.getSubject().isBlank()) {
        return course.getSubject();
      }
      if (course.getCourseName() != null && !course.getCourseName().isBlank()) {
        return course.getCourseName();
      }
    }
    return "모의고사";
  }

  /**
   * 분석 호출 전용 RestTemplate. fastapiRestTemplate 빈(readTimeout=200s)의 스레드 풀을 오래 잡지 않게 분석
   * 경로는 readTimeout 60s 인스턴스로 분리한다. 분석은 느린 동기 호출이지만 채팅·채점보다 AI 생성 길이가 짧아
   * 60s 안에 끝난다. 첫 호출 때 한 번만 만든다.
   */
  private RestTemplate analysisRestTemplate;

  /** 과외에 속한 모의고사 목록을 조회한다. */
  @Transactional
  public List<MockExamListResponse> getExamList(String userId, String courseId) {
    log.debug(
        "[MockExamService] getExamList() - START | userId: {}, courseId: {}", userId, courseId);

    findCourseByIdAndUser(courseId, userId);

    List<MockExam> exams = mockExamRepository.findByCourseIdOrderByCreatedAtDesc(courseId);

    List<MockExamListResponse> result;

    if (cutoverEnabled && examAttemptRepository != null) {
      // cutover 경로: exam_attempt 의 최신 attempt 로 attemptId·status 를 맞춘다
      result =
          exams.stream()
              .map(
                  exam -> {
                    com.swez.backend.domain.examgrading.entity.ExamAttempt latestAttempt =
                        examAttemptRepository.findLatestByMockExamId(exam.getId()).orElse(null);

                    if (latestAttempt == null) {
                      // attempt 가 없으면 레거시 응답(attemptId=null)을 반환한다
                      return mockExamMapper.toListResponse(exam);
                    }

                    // 오래된 mock_exam.status 를 attempt 상태에 맞춘다. 채점 완료
                    // (GRADED/SUBMITTED/MANUAL_REVIEW_REQUIRED)면 COMPLETED, READY 면 READY 로 바꾼다.
                    com.swez.backend.domain.examgrading.enums.AttemptStatus attemptStatus =
                        latestAttempt.getStatus();

                    // GRADED / SUBMITTED / MANUAL_REVIEW_REQUIRED 는 모두 COMPLETED 로 본다
                    boolean isGradedTerminal =
                        attemptStatus
                                == com.swez.backend.domain.examgrading.enums.AttemptStatus.GRADED
                            || attemptStatus
                                == com.swez.backend.domain.examgrading.enums.AttemptStatus.SUBMITTED
                            || attemptStatus
                                == com.swez.backend.domain.examgrading.enums.AttemptStatus
                                    .MANUAL_REVIEW_REQUIRED;

                    if (isGradedTerminal
                        && exam.getStatus()
                            != com.swez.backend.domain.mockexam.enums.MockExamStatus.COMPLETED) {
                      // 채점이 끝났는데 mock_exam 이 READY/GENERATING 으로 남아 있으면 COMPLETED 로
                      // 바꿔 프론트가 "결과 보기" 경로로 이동하게 한다
                      exam.updateStatus(
                          com.swez.backend.domain.mockexam.enums.MockExamStatus.COMPLETED);
                      log.info(
                          "[MockExamService] getExamList() - mock_exam.status COMPLETED 동기화"
                              + " | examId: {}, attemptStatus: {}",
                          exam.getId(),
                          attemptStatus);
                    } else if (!isGradedTerminal
                        && exam.getStatus()
                            == com.swez.backend.domain.mockexam.enums.MockExamStatus.GENERATING
                        && (attemptStatus
                                == com.swez.backend.domain.examgrading.enums.AttemptStatus.READY
                            || attemptStatus
                                == com.swez.backend.domain.examgrading.enums.AttemptStatus
                                    .FAILED)) {
                      // GENERATING 상태인데 attempt 가 READY/FAILED 인 경우
                      com.swez.backend.domain.mockexam.enums.MockExamStatus syncedStatus =
                          (attemptStatus
                                  == com.swez.backend.domain.examgrading.enums.AttemptStatus.READY)
                              ? com.swez.backend.domain.mockexam.enums.MockExamStatus.READY
                              : com.swez.backend.domain.mockexam.enums.MockExamStatus.GENERATING;
                      // mock_exam.status enum 에 FAILED 가 없어 FAILED attempt 는 GENERATING 으로 둔다.
                      // 대신 아래에서 failureReason 을 응답에 실어, 프론트가 '생성 실패'로 표시하고
                      // 폴링을 멈춰 '출제 중'에 계속 머물지 않게 한다.
                      exam.updateStatus(syncedStatus);
                      log.info(
                          "[MockExamService] getExamList() - mock_exam.status 동기화 | examId: {},"
                              + " attemptStatus: {}, syncedMockExamStatus: {}",
                          exam.getId(),
                          attemptStatus,
                          syncedStatus);
                    }

                    // attemptId 와 (FAILED 일 때) failureReason 을 담아 반환한다
                    String failureReason =
                        (attemptStatus
                                    == com.swez.backend.domain.examgrading.enums.AttemptStatus.FAILED
                                && latestAttempt.getFailureReason() != null)
                            ? latestAttempt.getFailureReason().name()
                            : null;
                    return mockExamMapper.toListResponseWithAttempt(
                        exam, latestAttempt.getAttemptId(), failureReason);
                  })
              .toList();
    } else {
      // 레거시 경로는 attemptId 없이 반환한다
      result = exams.stream().map(mockExamMapper::toListResponse).toList();
    }

    log.debug("[MockExamService] getExamList() - END | count: {}", result.size());
    return result;
  }

  /**
   * AI 모의고사 생성을 요청한다. MockExam 엔티티를 GENERATING 상태로 저장하고, FastAPI에 비동기 생성 요청을 보낸 뒤 즉시 반환한다.
   *
   * @return 생성된 모의고사 목록 아이템 (GENERATING 상태)
   */
  @Transactional
  public MockExamListResponse generateExam(
      String userId, String courseId, GenerateExamRequest request) {
    log.debug(
        "[MockExamService] generateExam() - START | userId: {}, courseId: {}", userId, courseId);

    Course course = findCourseByIdAndUser(courseId, userId);

    /*
        생성권(코인)은 클라이언트가 우회하지 못하게 서버에서 직접 차감한다.
        CoinService.useExam()은 비관적 잠금으로 잔액을 원자적으로 차감하고,
        잔액이 부족하면(CON_001) 예외를 던져 생성을 막는다.
    */
    coinService.useExam(userId, new CoinUseExamRequest(courseId));
    log.info(
        "[MockExamService] generateExam() - 생성권 차감 완료 | userId: {}, courseId: {}",
        userId,
        courseId);

    MockExam exam =
        MockExam.builder()
            .id(IdGenerator.mockExam())
            .course(course)
            .user(course.getUser())
            .title(course.getCourseName() + " - " + request.examType() + " 모의고사")
            .examType(request.examType())
            .questionCount(request.questionCount())
            .difficulty(request.difficulty())
            .timeLimit(request.timeLimit())
            .status(MockExamStatus.GENERATING)
            .build();

    MockExam saved = mockExamRepository.save(exam);

    /*
        FastAPI 비동기 모의고사 생성 요청은 cutover 플래그에 따라 갈린다.
    */
    if (cutoverEnabled) {
      // cutover 경로: examgrading lifecycle 로 생성을 넘긴다.
      // ExamAttemptGenerationService 가 null 이면 설정 오류라 MEX_003 으로 처리한다.
      if (examAttemptGenerationService == null) {
        log.error(
            "[MockExamService] generateExam() - cutover=true이나 ExamAttemptGenerationService 빈이 없음"
                + " | examId: {}",
            saved.getId());
        throw new CustomException(MockExamErrorCode.MEX_003);
      }

      // mockExamId 를 함께 넘겨 mockexam 과 attempt 를 양쪽에서 추적할 수 있게 한다.
      // examType 이 'multiple_choice'(객관식 모의고사)면 사용자가 객관식만 고른 것이라 객관식 템플릿만
      // 보낸다. 'final'(최종 점검 모의고사) 등 그 외에는 빈 목록을 보내 FastAPI plan_exam_node 가 기본
      // 혼합 유형(객관식·단답·빈칸·OX·서술)으로 고르게 나누게 한다. difficulty(easy/medium/hard)도
      // 그대로 넘겨 FastAPI 난이도 분포에 반영한다.
      ExamGenerateRequest examGenerateRequest =
          new ExamGenerateRequest(
              courseId,
              // FastAPI generate-async 스키마는 subject 빈 문자열을 422로 거부한다(min_length=1).
              // 과목이 없는 코스는 과외명 → "모의고사" 순으로 폴백해 트리거가 실패하지 않게 한다
              resolveSubjectForGeneration(saved.getCourse()),
              null, // topic: 레거시 MCQ에는 topic 개념이 없다
              request.questionCount(),
              resolveQuestionTypes(request.examType()),
              request.difficulty(), // 프론트 난이도(easy/medium/hard). FastAPI 가 난이도 분포로 바꾼다
              BigDecimal.valueOf(60), // passPercentage 기본값 60
              saved.getId()); // mockExamId: mockexam ↔ attempt 추적 키

      // 생성된 attemptId 를 응답에 넣으려고 반환값을 받아 둔다
      com.swez.backend.domain.examgrading.dto.response.ExamGenerateStartResponse generationStart;
      try {
        generationStart = examAttemptGenerationService.startGeneration(examGenerateRequest, userId);
      } catch (CustomException e) {
        // ExamAttemptGenerationService 가 FASTAPI_GENERATION_FAILED 를 던지므로 MEX_003 으로 감싼다
        log.error(
            "[MockExamService] generateExam() - cutover 경로 생성 실패 | examId: {}, error: {}",
            saved.getId(),
            e.getMessage());
        throw new CustomException(MockExamErrorCode.MEX_003);
      }

      log.info(
          "[MockExamService] generateExam() - cutover 경로 생성 완료 | examId: {}, attemptId: {}",
          saved.getId(),
          generationStart.attemptId());

      // 응답에 attemptId 를 넣어 프론트가 examgrading 경로를 쓰게 한다
      MockExamListResponse cutoverResult =
          mockExamMapper.toListResponseWithAttempt(saved, generationStart.attemptId());
      log.debug("[MockExamService] generateExam() - END (cutover) | examId: {}", saved.getId());
      return cutoverResult;
    } else {
      // 레거시 경로: FastAPI 를 직접 호출한다
      String url = fastapiBaseUrl + "/api/mock-exams/generate";
      try {
        Map<String, Object> body = new HashMap<>();
        body.put("examId", saved.getId());
        body.put("courseId", courseId);
        body.put("examType", request.examType());
        body.put("questionCount", request.questionCount());
        body.put("difficulty", request.difficulty());
        body.put("focusTopics", request.focusTopics());
        body.put("timeLimit", request.timeLimit());

        restTemplate.postForEntity(url, body, Void.class);
      } catch (RestClientException e) {
        log.error(
            "[MockExamService] generateExam() - FastAPI 호출 실패 | examId: {}, error: {}",
            saved.getId(),
            e.getMessage());
        throw new CustomException(MockExamErrorCode.MEX_003);
      }
    }

    MockExamListResponse result = mockExamMapper.toListResponse(saved);

    log.debug("[MockExamService] generateExam() - END | examId: {}", saved.getId());
    return result;
  }

  /** 모의고사 문제를 조회한다. GENERATING 상태이면 에러를 반환한다. 정답(correctOption)은 포함하지 않는다. */
  @Transactional(readOnly = true)
  public MockExamDetailResponse getExamDetail(String userId, String courseId, String examId) {
    log.debug(
        "[MockExamService] getExamDetail() - START | userId: {}, courseId: {}, examId: {}",
        userId,
        courseId,
        examId);

    findCourseByIdAndUser(courseId, userId);
    MockExam exam = findExamByIdAndCourse(examId, courseId);

    if (exam.getStatus() == MockExamStatus.GENERATING) {
      throw new CustomException(MockExamErrorCode.MEX_002);
    }

    // cutover 경로: examgrading detail 서비스로 넘긴다
    if (cutoverEnabled) {
      if (examAttemptDetailService == null || examAttemptRepository == null) {
        // cutover=true 인데 서비스 빈이 없으면 레거시 경로로 폴백한다
        log.warn(
            "[MockExamService] getExamDetail() - cutover=true이나 examgrading 빈 미등록, 레거시 경로로 폴백"
                + " | examId: {}",
            examId);
      } else {
        ExamAttempt attempt =
            examAttemptRepository
                .findLatestByMockExamId(examId)
                .orElse(null); // attempt 가 없으면 레거시 경로로 폴백

        if (attempt != null) {
          // attempt 소유권은 ExamAttemptDetailService 가 userId 로 검증한다
          AttemptDetailResponse attemptDetail =
              examAttemptDetailService.getAttemptDetail(attempt.getAttemptId(), userId);

          MockExamDetailResponse result = toMockExamDetailFromAttempt(exam, attemptDetail);
          log.debug(
              "[MockExamService] getExamDetail() - cutover 경로 완료 | examId: {}, attemptId: {}",
              examId,
              attempt.getAttemptId());
          return result;
        }

        // attempt가 없으면 레거시 경로로 폴백
        log.debug(
            "[MockExamService] getExamDetail() - attempt 없음, 레거시 경로로 폴백 | examId: {}", examId);
      }
    }

    /*
        레거시 경로 또는 cutover 폴백에서 문항을 조회한다.
    */
    List<MockExamQuestion> questions =
        mockExamQuestionRepository.findByMockExamIdOrderByQuestionIdxAsc(examId);

    MockExamDetailResponse result = mockExamMapper.toDetailResponse(exam, questions);

    log.debug("[MockExamService] getExamDetail() - END | examId: {}", examId);
    return result;
  }

  /** 모의고사 답안을 제출하고 채점 결과를 반환한다. */
  @Transactional
  public ExamResultResponse submitExam(
      String userId, String courseId, String examId, SubmitExamRequest request) {
    log.debug(
        "[MockExamService] submitExam() - START | userId: {}, courseId: {}, examId: {}",
        userId,
        courseId,
        examId);

    findCourseByIdAndUser(courseId, userId);
    MockExam exam = findExamByIdAndCourse(examId, courseId);

    /*
        READY 상태에서만 제출할 수 있다.
    */
    if (exam.getStatus() != MockExamStatus.READY) {
      throw new CustomException(MockExamErrorCode.MEX_002);
    }

    mockExamSubmissionRepository
        .findByMockExamIdAndUserId(examId, userId)
        .ifPresent(
            s -> {
              throw new CustomException(MockExamErrorCode.MEX_004);
            });

    // cutover 경로: examgrading 제출·채점 서비스로 넘긴다
    if (cutoverEnabled) {
      if (examAttemptSubmitService == null || examAttemptRepository == null) {
        log.warn(
            "[MockExamService] submitExam() - cutover=true이나 examgrading 빈 미등록, 레거시 경로로 폴백"
                + " | examId: {}",
            examId);
      } else {
        ExamAttempt attempt = examAttemptRepository.findLatestByMockExamId(examId).orElse(null);

        if (attempt != null) {
          // SubmitExamRequest.AnswerItem → SubmittedAnswerRequest.
          // 레거시 selectedOption(문자열)을 JsonNode 텍스트 노드로 감싼다
          List<SubmittedAnswerRequest> submittedAnswers =
              request.answers().stream()
                  .map(
                      item ->
                          new SubmittedAnswerRequest(
                              item.questionId(),
                              objectMapper.getNodeFactory().textNode(item.selectedOption())))
                  .toList();

          AttemptSubmitRequest submitRequest = new AttemptSubmitRequest(submittedAnswers);

          ExamSubmissionGradeResponse gradeResponse =
              examAttemptSubmitService.submitAndGrade(
                  attempt.getAttemptId(), submitRequest, userId);

          // examgrading 채점이 끝났으므로 mockexam 상태도 COMPLETED 로 맞춘다
          exam.updateStatus(MockExamStatus.COMPLETED);

          ExamResultResponse result = toExamResultFromGradeResponse(exam, gradeResponse);
          log.debug(
              "[MockExamService] submitExam() - cutover 경로 완료 | examId: {}, attemptId: {}",
              examId,
              attempt.getAttemptId());
          return result;
        }

        // attempt가 없으면 레거시 경로로 폴백
        log.debug("[MockExamService] submitExam() - attempt 없음, 레거시 경로로 폴백 | examId: {}", examId);
      }
    }

    /*
        레거시 경로 또는 cutover 폴백에서 직접 채점한다.
    */
    List<MockExamQuestion> questions =
        mockExamQuestionRepository.findByMockExamIdOrderByQuestionIdxAsc(examId);

    if (questions.isEmpty()) {
      throw new CustomException(MockExamErrorCode.MEX_006);
    }

    /* 문항 ID → 제출 답안 */
    Map<String, String> answerMap = new HashMap<>();
    for (SubmitExamRequest.AnswerItem item : request.answers()) {
      answerMap.put(item.questionId(), item.selectedOption());
    }

    int score = 0;
    int totalPoints = 0;
    List<ExamResultResponse.QuestionResultItem> questionResults = new java.util.ArrayList<>();
    /* AI 총평 분석에 보낼 문항별 결과. 채점하면서 함께 쌓는다. */
    List<ExamAnalysisRequest.ResultItem> analysisResults = new java.util.ArrayList<>();

    for (MockExamQuestion question : questions) {
      totalPoints += question.getPoints();
      String selected = answerMap.getOrDefault(question.getId(), "");
      boolean correct =
          question.getCorrectOption() != null && question.getCorrectOption().equals(selected);
      if (correct) {
        score += question.getPoints();
      }

      questionResults.add(
          ExamResultResponse.QuestionResultItem.builder()
              .questionId(question.getId())
              .questionIdx(question.getQuestionIdx())
              .questionText(question.getQuestionText())
              .options(question.getOptions())
              .selectedOption(selected)
              .correctOption(question.getCorrectOption())
              .correct(correct)
              .explanation(question.getExplanation())
              .points(question.getPoints())
              .build());

      /* FastAPI 분석 계약 항목. options 는 엔티티 원문 문자열을 그대로 전달한다. */
      analysisResults.add(
          ExamAnalysisRequest.ResultItem.builder()
              .questionIdx(question.getQuestionIdx())
              .questionText(question.getQuestionText())
              .correct(correct)
              .options(question.getOptions())
              .selectedOption(selected)
              .correctOption(question.getCorrectOption())
              .explanation(question.getExplanation())
              .build());
    }

    String grade = calculateGrade(score, totalPoints);

    /*
        AI 총평(약점 진단)은 FastAPI 를 동기 호출한다.
        실패·타임아웃이면 analysis=null 로 두고 채점 실패로 전파하지 않는다.
    */
    String subject = resolveSubject(exam);
    String analysis = requestAnalysis(examId, subject, score, totalPoints, grade, analysisResults);

    /*
        analysis 까지 담아 한 번에 INSERT 한다.
    */
    MockExamSubmission submission =
        MockExamSubmission.builder()
            .id(IdGenerator.mockExamSubmission())
            .mockExam(exam)
            .user(exam.getUser())
            .answers(convertAnswersToJson(request.answers()))
            .score(score)
            .totalPoints(totalPoints)
            .timeTaken(request.timeTaken())
            .grade(grade)
            .analysis(analysis)
            .build();

    mockExamSubmissionRepository.save(submission);

    exam.updateStatus(MockExamStatus.COMPLETED);

    ExamResultResponse result = mockExamMapper.toResultResponse(exam, submission, questionResults);

    log.debug(
        "[MockExamService] submitExam() - END | examId: {}, score: {}/{}, grade: {}",
        examId,
        score,
        totalPoints,
        grade);
    return result;
  }

  /** 모의고사 제출 결과를 상세 조회한다. */
  @Transactional(readOnly = true)
  public ExamResultResponse getExamResult(String userId, String courseId, String examId) {
    log.debug(
        "[MockExamService] getExamResult() - START | userId: {}, courseId: {}, examId: {}",
        userId,
        courseId,
        examId);

    findCourseByIdAndUser(courseId, userId);
    MockExam exam = findExamByIdAndCourse(examId, courseId);

    MockExamSubmission submission =
        mockExamSubmissionRepository
            .findByMockExamIdAndUserId(examId, userId)
            .orElseThrow(() -> new CustomException(MockExamErrorCode.MEX_005));

    List<MockExamQuestion> questions =
        mockExamQuestionRepository.findByMockExamIdOrderByQuestionIdxAsc(examId);

    Map<String, String> answerMap = parseAnswersFromJson(submission.getAnswers());

    List<ExamResultResponse.QuestionResultItem> questionResults =
        questions.stream()
            .map(
                q -> {
                  String selected = answerMap.getOrDefault(q.getId(), "");
                  boolean correct =
                      q.getCorrectOption() != null && q.getCorrectOption().equals(selected);
                  return ExamResultResponse.QuestionResultItem.builder()
                      .questionId(q.getId())
                      .questionIdx(q.getQuestionIdx())
                      .questionText(q.getQuestionText())
                      .options(q.getOptions())
                      .selectedOption(selected)
                      .correctOption(q.getCorrectOption())
                      .correct(correct)
                      .explanation(q.getExplanation())
                      .points(q.getPoints())
                      .build();
                })
            .toList();

    ExamResultResponse result = mockExamMapper.toResultResponse(exam, submission, questionResults);

    log.debug("[MockExamService] getExamResult() - END | examId: {}", examId);
    return result;
  }

  // Private Helpers

  /**
   * 과외를 조회하고 소유권을 검증한다. 없거나 소유자가 달라도 같은 CRS_001 을 던져 존재 여부를 드러내지
   * 않는다.
   */
  private Course findCourseByIdAndUser(String courseId, String userId) {
    Course course =
        courseRepository
            .findById(courseId)
            .orElseThrow(() -> new CustomException(CourseErrorCode.CRS_001));
    if (!course.getUser().getId().equals(userId)) {
      throw new CustomException(CourseErrorCode.CRS_001);
    }
    return course;
  }

  /** 모의고사를 조회하고 과외 소속을 검증한다. 없거나 소속 과외가 다르면 MEX_001 을 던진다. */
  private MockExam findExamByIdAndCourse(String examId, String courseId) {
    MockExam exam =
        mockExamRepository
            .findById(examId)
            .orElseThrow(() -> new CustomException(MockExamErrorCode.MEX_001));
    if (!exam.getCourse().getId().equals(courseId)) {
      throw new CustomException(MockExamErrorCode.MEX_001);
    }
    return exam;
  }

  /** 점수 비율에 따라 등급을 계산한다. 총 배점이 0이면 F 다. */
  private String calculateGrade(int score, int totalPoints) {
    if (totalPoints == 0) {
      return "F";
    }
    double percent = (double) score / totalPoints * 100;
    if (percent >= 90) return "A+";
    if (percent >= 85) return "A";
    if (percent >= 80) return "B+";
    if (percent >= 75) return "B";
    if (percent >= 70) return "C+";
    if (percent >= 65) return "C";
    if (percent >= 60) return "D";
    return "F";
  }

  /** 분석 요청에 사용할 과목명을 결정한다. 과외의 subject를 우선 사용하고, 비어 있으면 모의고사 제목으로 대체한다. */
  private String resolveSubject(MockExam exam) {
    String subject = exam.getCourse() != null ? exam.getCourse().getSubject() : null;
    if (subject != null && !subject.isBlank()) {
      return subject;
    }
    return exam.getTitle();
  }

  /**
   * FastAPI 약점 진단(총평) 분석을 동기 호출하여 총평 문자열을 반환한다.
   *
   * <p>이 호출은 채점 흐름의 부가 기능이다. 실패·타임아웃·4xx·5xx 어느 경우에도 채점은 성공해야 하므로 모든
   * 예외를 잡아 로그를 남기고 {@code null}을 반환한다. 예외를 위로 던지지 않고, 로그 없이 삼키지도 않는다.
   *
   * @return 총평 문자열, 실패 시 {@code null}
   */
  private String requestAnalysis(
      String examId,
      String subject,
      int score,
      int totalPoints,
      String grade,
      List<ExamAnalysisRequest.ResultItem> results) {
    String url = fastapiBaseUrl + "/api/mock-exams/analyze";
    ExamAnalysisRequest body =
        ExamAnalysisRequest.builder()
            .examId(examId)
            .subject(subject)
            .score(score)
            .totalPoints(totalPoints)
            .grade(grade)
            .results(results)
            .build();

    try {
      ExamAnalysisResponse response =
          getAnalysisRestTemplate().postForObject(url, body, ExamAnalysisResponse.class);

      if (response == null || response.analysis() == null || response.analysis().isBlank()) {
        /* 응답은 왔지만 총평이 비어 있으면 채점은 그대로 하고 analysis 만 비운다. */
        log.warn("[MockExamService] requestAnalysis() - 분석 응답이 비어 있음 | examId: {}", examId);
        return null;
      }

      log.debug(
          "[MockExamService] requestAnalysis() - 분석 수신 | examId: {}, length: {}",
          examId,
          response.analysis().length());
      return response.analysis();
    } catch (RestClientException e) {
      /* 연결 실패·타임아웃·4xx·5xx 를 여기서 받아 로그만 남기고 채점 실패로 전파하지 않는다. */
      log.error(
          "[MockExamService] requestAnalysis() - FastAPI 분석 호출 실패, analysis=null 로 진행"
              + " | examId: {}, error: {}",
          examId,
          e.getMessage());
      return null;
    } catch (RuntimeException e) {
      /* 직렬화 등 예기치 못한 런타임 오류도 채점은 막지 않는다. */
      log.error(
          "[MockExamService] requestAnalysis() - 분석 처리 중 예기치 못한 오류, analysis=null 로 진행"
              + " | examId: {}, error: {}",
          examId,
          e.getMessage());
      return null;
    }
  }

  /** 분석 호출 전용 RestTemplate을 반환한다. 공유 빈과 분리해 별도 타임아웃을 쓰며, 첫 호출 때 한 번만 만든다. */
  private RestTemplate getAnalysisRestTemplate() {
    if (analysisRestTemplate == null) {
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout((int) ANALYSIS_CONNECT_TIMEOUT.toMillis());
      factory.setReadTimeout((int) ANALYSIS_READ_TIMEOUT.toMillis());
      analysisRestTemplate = new RestTemplate(factory);
      analysisRestTemplate.getInterceptors().add(new TraceIdRestTemplateInterceptor());
      analysisRestTemplate.getInterceptors().add(new FastApiAuthInterceptor(fastapiApiKey));
    }
    return analysisRestTemplate;
  }

  /** 답안 목록을 ObjectMapper 로 JSON 문자열로 직렬화한다. */
  private String convertAnswersToJson(List<SubmitExamRequest.AnswerItem> answers) {
    try {
      return objectMapper.writeValueAsString(answers);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("답안 직렬화 실패", e);
    }
  }

  /**
   * JSON 답안 문자열을 문항 ID → 선택 답안 Map 으로 변환한다. TypeReference 로 제네릭 타입을 명시해 unchecked
   * 캐스트가 필요 없다.
   */
  private Map<String, String> parseAnswersFromJson(String json) {
    Map<String, String> result = new HashMap<>();
    try {
      List<Map<String, String>> list =
          objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
      for (Map<String, String> item : list) {
        result.put(item.get("questionId"), item.get("selectedOption"));
      }
    } catch (Exception e) {
      log.warn("[MockExamService] parseAnswersFromJson() - 답안 JSON 파싱 실패: {}", e.getMessage());
    }
    return result;
  }

  // Cutover 브리지 변환 헬퍼

  /**
   * AttemptDetailResponse(examgrading DTO)를 MockExamDetailResponse(mockexam DTO)로 변환한다.
   *
   * <p>examgrading 상세에는 정답·해설이 없으므로 correctOption/explanation 은 null 로 둔다. 민감 필드는 여기서도
   * 다시 넣지 않는다.
   *
   * @param exam 제목·유형·난이도 등 메타 정보를 줄 mockexam 엔티티
   */
  private MockExamDetailResponse toMockExamDetailFromAttempt(
      MockExam exam, AttemptDetailResponse attemptDetail) {
    // AttemptQuestionResponse → MockExamQuestionResponse (민감 필드는 계속 제외)
    List<MockExamQuestionResponse> questions;
    if (attemptDetail.questions() != null) {
      questions =
          attemptDetail.questions().stream()
              .map(
                  q ->
                      MockExamQuestionResponse.builder()
                          .id(q.questionId())
                          .questionIdx(q.displayOrder() != null ? q.displayOrder() : 0)
                          .questionText(q.stem())
                          .questionType(q.templateId()) // templateId → questionType
                          .options(
                              null) // examgrading SafeOptionResponse 는 JSON 문자열이 아닌 구조체라 null
                          .points(q.points() != null ? q.points().intValue() : 0)
                          .build())
              .toList();
    } else {
      questions = List.of();
    }

    return MockExamDetailResponse.builder()
        .id(exam.getId())
        .title(exam.getTitle())
        .examType(exam.getExamType())
        .questionCount(exam.getQuestionCount())
        .difficulty(exam.getDifficulty())
        .timeLimit(exam.getTimeLimit())
        .status(exam.getStatus())
        .totalPoints(exam.getTotalPoints())
        .createdAt(exam.getCreatedAt())
        .questions(questions)
        .build();
  }

  /**
   * ExamSubmissionGradeResponse(examgrading DTO)를 ExamResultResponse(mockexam DTO)로 변환한다.
   *
   * <p>examgrading 채점 결과는 BigDecimal 기반이라 int score/totalPoints로 변환한다. 문항별 결과는 examgrading 이
   * 주는 피드백을 explanation 필드에 넣는다.
   */
  private ExamResultResponse toExamResultFromGradeResponse(
      MockExam exam, ExamSubmissionGradeResponse gradeResponse) {
    // BigDecimal → int (intValue 라 소수점 이하는 버린다)
    int score = gradeResponse.totalScore() != null ? gradeResponse.totalScore().intValue() : 0;
    int totalPoints = gradeResponse.maxScore() != null ? gradeResponse.maxScore().intValue() : 0;

    // examgrading 은 등급 필드를 주지 않아 MockExamService 등급 정책으로 다시 계산한다
    String grade = calculateGrade(score, totalPoints);

    List<ExamResultResponse.QuestionResultItem> questionResults =
        gradeResponse.results() != null
            ? gradeResponse.results().stream()
                .map(
                    qg ->
                        ExamResultResponse.QuestionResultItem.builder()
                            .questionId(qg.questionId())
                            .questionIdx(0) // examgrading 채점 결과에는 displayOrder 가 없다
                            .questionText(null) // 보안 정책상 채점 결과에 문제 텍스트를 넣지 않는다
                            .options(null)
                            .selectedOption(null) // 채점 결과 DTO 에 제출 답안이 없다
                            .correctOption(null) // 정답 노출 금지 (examgrading GradingLeakGuard 규칙)
                            .correct(qg.correct())
                            .explanation(qg.feedback()) // AI 피드백을 explanation 으로 보여 준다
                            .points(qg.maxScore() != null ? qg.maxScore().intValue() : 0)
                            .build())
                .toList()
            : List.of();

    return ExamResultResponse.builder()
        .examId(exam.getId())
        .title(exam.getTitle())
        .score(score)
        .totalPoints(totalPoints)
        .grade(grade)
        .timeTaken(null) // examgrading 채점 결과에 소요 시간이 없다
        .submittedAt(
            gradeResponse.gradedAt() != null
                ? gradeResponse.gradedAt().toLocalDateTime()
                : LocalDateTime.now())
        .analysis(null) // examgrading 채점 결과에는 AI 총평 필드가 없다
        .questionResults(questionResults)
        .build();
  }
}
