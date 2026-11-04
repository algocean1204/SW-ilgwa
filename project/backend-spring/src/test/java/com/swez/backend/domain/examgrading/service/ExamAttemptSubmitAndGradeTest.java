/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.examgrading.dto.request.AttemptSubmitRequest;
import com.swez.backend.domain.examgrading.dto.request.SubmittedAnswerRequest;
import com.swez.backend.domain.examgrading.dto.response.ExamSubmissionGradeResponse;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion;
import com.swez.backend.domain.examgrading.enums.AttemptStatus;
import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.mapper.ExamGradingMapper;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.GradingStatus;
import com.swez.backend.domain.examgrading.model.QuestionOption;
import com.swez.backend.domain.examgrading.model.QuestionReviewData;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;
import com.swez.backend.domain.examgrading.port.ExamAttemptRepositoryPort;
import com.swez.backend.domain.examgrading.port.ExamForgeRubricGradingPort;
import com.swez.backend.domain.examgrading.support.ReviewAnswerFormatter;
import com.swez.backend.global.exception.CustomException;

/**
 * 응시 제출 + 채점 파이프라인 단위 테스트 (detail → submit → grade → result).
 *
 * <p>시드 attempt(객관식 MCQ 2문항)를 사용하여 FastAPI 없이 결정론 채점으로 전체 lifecycle을 검증한다:
 *
 * <ul>
 *   <li>submit → SUBMITTED 전환 + 답안 저장 확인
 *   <li>grade(결정론 MCQ) → 점수·합격 여부 검증
 *   <li>GradingLeakGuard: 정답 누출 차단 확인
 *   <li>소유권 불일치 → CustomException
 * </ul>
 */
class ExamAttemptSubmitAndGradeTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  @DisplayName("MCQ 시드 — 제출 + 결정론 채점 → 총점 4/4, 합격")
  void submitAndGradeAllCorrectMcqSeed() {
    FakeSubmitRepository repo = new FakeSubmitRepository(seedAttempt(), seedSnapshot());
    ExamAttemptSubmitService service = submitService(repo);

    // 두 문항 모두 정답("B", "A") 제출
    AttemptSubmitRequest request =
        new AttemptSubmitRequest(
            List.of(
                new SubmittedAnswerRequest("q-1", textNode("B")),
                new SubmittedAnswerRequest("q-2", textNode("A"))));

    ExamSubmissionGradeResponse response =
        service.submitAndGrade("attempt-seed", request, "user-1");

    // 채점 결과: 전부 정답 4/4점
    assertThat(response.totalScore()).isEqualByComparingTo("4.0000");
    assertThat(response.maxScore()).isEqualByComparingTo("4.0000");
    assertThat(response.percentage()).isEqualByComparingTo("100.00");
    assertThat(response.passed()).isTrue();
    // 두 문항 모두 채점됨
    assertThat(response.results()).hasSize(2);
    assertThat(response.results()).allMatch(r -> r.correct());
    // 제출 답안 저장됐는지 확인
    assertThat(repo.savedAnswers).hasSize(2);
  }

  @Test
  @DisplayName("MCQ 시드 — 하나만 정답 제출 → 총점 2/4, 불합격 (기준 60%)")
  void submitAndGradeHalfCorrectMcqSeed() {
    FakeSubmitRepository repo = new FakeSubmitRepository(seedAttempt(), seedSnapshot());
    ExamAttemptSubmitService service = submitService(repo);

    // q-1 정답("B"), q-2 오답("C") 제출
    AttemptSubmitRequest request =
        new AttemptSubmitRequest(
            List.of(
                new SubmittedAnswerRequest("q-1", textNode("B")),
                new SubmittedAnswerRequest("q-2", textNode("C"))));

    ExamSubmissionGradeResponse response =
        service.submitAndGrade("attempt-seed", request, "user-1");

    assertThat(response.totalScore()).isEqualByComparingTo("2.0000");
    assertThat(response.maxScore()).isEqualByComparingTo("4.0000");
    assertThat(response.percentage()).isEqualByComparingTo("50.00");
    assertThat(response.passed()).isFalse(); // 50% < 60% 합격 기준
    assertThat(repo.savedAnswers).hasSize(2);
    // 채점 결과 저장됐는지 확인
    assertThat(repo.savedResult).isNotNull();
  }

  @Test
  @DisplayName("빈 제출 — 전 문항 0점, 불합격")
  void submitEmptyAnswersGivesZeroScore() {
    FakeSubmitRepository repo = new FakeSubmitRepository(seedAttempt(), seedSnapshot());
    ExamAttemptSubmitService service = submitService(repo);

    AttemptSubmitRequest request = new AttemptSubmitRequest(List.of());
    ExamSubmissionGradeResponse response =
        service.submitAndGrade("attempt-seed", request, "user-1");

    assertThat(response.totalScore()).isEqualByComparingTo("0.0000");
    assertThat(response.passed()).isFalse();
  }

  @Test
  @DisplayName("이미 GRADED 상태인 attempt 재제출 — 기존 결과 반환, 중복 저장 없음 (멱등성)")
  void submitAndGradeAlreadyGradedAttemptReturnsCachedResult() {
    // given: 이미 GRADED 상태인 attempt에 재제출
    ExamAttempt gradedAttempt =
        ExamAttempt.builder()
            .attemptId("attempt-graded")
            .examId("exam-seed")
            .user(User.builder().id("user-1").email("user1@test.com").build())
            .status(AttemptStatus.GRADED) // 이미 채점 완료
            .passPercentage(BigDecimal.valueOf(60))
            .answerKeySeal("sealed-key")
            .createdAt(OffsetDateTime.now())
            .build();

    // 기존 채점 결과 시드. findGradeResult가 반환할 도메인 record
    ExamGradeResult existingGradeResult =
        new ExamGradeResult(
            "attempt-graded",
            "exam-seed",
            BigDecimal.valueOf(4.0000),
            BigDecimal.valueOf(4.0000),
            BigDecimal.valueOf(100.00),
            true,
            GradingStatus.GRADED,
            OffsetDateTime.now(),
            List.of());

    FakeIdempotentRepository repo =
        new FakeIdempotentRepository(gradedAttempt, seedSnapshot(), existingGradeResult);
    ExamAttemptSubmitService service = submitService(repo);

    AttemptSubmitRequest request =
        new AttemptSubmitRequest(
            List.of(
                new SubmittedAnswerRequest("q-1", textNode("B")),
                new SubmittedAnswerRequest("q-2", textNode("A"))));

    // when: 이미 GRADED인 attempt에 재제출
    ExamSubmissionGradeResponse response =
        service.submitAndGrade("attempt-graded", request, "user-1");

    // then: 기존 결과가 반환되어야 하며 답안 재저장이 발생하지 않아야 한다
    assertThat(response.totalScore()).isEqualByComparingTo("4.0000");
    assertThat(response.maxScore()).isEqualByComparingTo("4.0000");
    assertThat(response.passed()).isTrue();
    // 멱등성 단언: 재제출 시 저장이 발생하지 않아야 한다
    assertThat(repo.savedAnswersCount).as("GRADED attempt 재제출 시 답안 저장이 발생해서는 안 된다").isZero();
    assertThat(repo.savedResultCount).as("GRADED attempt 재제출 시 채점 결과 저장이 발생해서는 안 된다").isZero();
  }

  @Test
  @DisplayName("이미 SUBMITTED 상태인 attempt 재제출 — 기존 결과 반환, 중복 저장 없음 (멱등성)")
  void submitAndGradeAlreadySubmittedAttemptReturnsCachedResult() {
    // given: 제출 후 채점 전(SUBMITTED) 상태에서 재제출
    ExamAttempt submittedAttempt =
        ExamAttempt.builder()
            .attemptId("attempt-submitted")
            .examId("exam-seed")
            .user(User.builder().id("user-1").email("user1@test.com").build())
            .status(AttemptStatus.SUBMITTED)
            .passPercentage(BigDecimal.valueOf(60))
            .answerKeySeal("sealed-key")
            .createdAt(OffsetDateTime.now())
            .build();

    ExamGradeResult existingGradeResult =
        new ExamGradeResult(
            "attempt-submitted",
            "exam-seed",
            BigDecimal.valueOf(2.0000),
            BigDecimal.valueOf(4.0000),
            BigDecimal.valueOf(50.00),
            false,
            GradingStatus.GRADED,
            OffsetDateTime.now(),
            List.of());

    FakeIdempotentRepository repo =
        new FakeIdempotentRepository(submittedAttempt, seedSnapshot(), existingGradeResult);
    ExamAttemptSubmitService service = submitService(repo);

    AttemptSubmitRequest request =
        new AttemptSubmitRequest(List.of(new SubmittedAnswerRequest("q-1", textNode("B"))));

    // when
    ExamSubmissionGradeResponse response =
        service.submitAndGrade("attempt-submitted", request, "user-1");

    // then: 기존 결과 반환, 재저장 없음
    assertThat(response.totalScore()).isEqualByComparingTo("2.0000");
    assertThat(repo.savedAnswersCount).as("SUBMITTED attempt 재제출 시 답안 저장 없음").isZero();
    assertThat(repo.savedResultCount).as("SUBMITTED attempt 재제출 시 채점 결과 저장 없음").isZero();
  }

  // 제출 가능 상태 확인: 생성중/실패/미시작/봉인누락 차단

  @Test
  @DisplayName("GENERATING 상태 제출 접근 — ATTEMPT_NOT_SUBMITTABLE(409), 답안 저장 없음")
  void submitWhileGeneratingIsBlocked() {
    ExamAttempt generating = attemptWithStatus("attempt-gen", AttemptStatus.GENERATING, "seal");
    FakeSubmitRepository repo = new FakeSubmitRepository(generating, seedSnapshot());
    ExamAttemptSubmitService service = submitService(repo);

    AttemptSubmitRequest request =
        new AttemptSubmitRequest(List.of(new SubmittedAnswerRequest("q-1", textNode("B"))));

    assertThatThrownBy(() -> service.submitAndGrade("attempt-gen", request, "user-1"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_SUBMITTABLE));
    // 생성 중에는 채점·저장이 전혀 일어나면 안 된다
    assertThat(repo.savedAnswers).isEmpty();
    assertThat(repo.savedResult).isNull();
  }

  @Test
  @DisplayName("FAILED 상태 제출 — ATTEMPT_NOT_SUBMITTABLE(409)")
  void submitWhenGenerationFailedIsBlocked() {
    ExamAttempt failed = attemptWithStatus("attempt-failed", AttemptStatus.FAILED, null);
    FakeSubmitRepository repo = new FakeSubmitRepository(failed, seedSnapshot());
    ExamAttemptSubmitService service = submitService(repo);

    AttemptSubmitRequest request =
        new AttemptSubmitRequest(List.of(new SubmittedAnswerRequest("q-1", textNode("B"))));

    assertThatThrownBy(() -> service.submitAndGrade("attempt-failed", request, "user-1"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_SUBMITTABLE));
    assertThat(repo.savedAnswers).isEmpty();
  }

  @Test
  @DisplayName("CREATED(미시작) 상태 제출 — ATTEMPT_NOT_SUBMITTABLE(409)")
  void submitWhenCreatedIsBlocked() {
    ExamAttempt created = attemptWithStatus("attempt-created", AttemptStatus.CREATED, null);
    FakeSubmitRepository repo = new FakeSubmitRepository(created, seedSnapshot());
    ExamAttemptSubmitService service = submitService(repo);

    AttemptSubmitRequest request = new AttemptSubmitRequest(List.of());

    assertThatThrownBy(() -> service.submitAndGrade("attempt-created", request, "user-1"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_SUBMITTABLE));
  }

  @Test
  @DisplayName("READY인데 정답지 봉인 누락 — ATTEMPT_NOT_SUBMITTABLE(409) (비정상 콜백 방어)")
  void submitWhenReadyWithoutSealIsBlocked() {
    // status=READY 이지만 answerKeySeal이 비어 있는 비정상 상태
    ExamAttempt readyNoSeal = attemptWithStatus("attempt-noseal", AttemptStatus.READY, "  ");
    FakeSubmitRepository repo = new FakeSubmitRepository(readyNoSeal, seedSnapshot());
    ExamAttemptSubmitService service = submitService(repo);

    AttemptSubmitRequest request =
        new AttemptSubmitRequest(List.of(new SubmittedAnswerRequest("q-1", textNode("B"))));

    assertThatThrownBy(() -> service.submitAndGrade("attempt-noseal", request, "user-1"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_SUBMITTABLE));
    assertThat(repo.savedAnswers).isEmpty();
  }

  @Test
  @DisplayName("READY + 정답지 봉인 존재 — 정상 제출·채점 진행 (제출 가능 조건 충족)")
  void submitWhenReadyWithSealSucceeds() {
    ExamAttempt readyWithSeal = attemptWithStatus("attempt-ready", AttemptStatus.READY, "sealed-key");
    // 스냅샷 attemptId도 attempt-ready로 맞춘다(채점 경로 진입을 위해)
    ExamAttemptSnapshot snapshot = seedSnapshotFor("attempt-ready");
    FakeSubmitRepository repo = new FakeSubmitRepository(readyWithSeal, snapshot);
    ExamAttemptSubmitService service = submitService(repo);

    AttemptSubmitRequest request =
        new AttemptSubmitRequest(
            List.of(
                new SubmittedAnswerRequest("q-1", textNode("B")),
                new SubmittedAnswerRequest("q-2", textNode("A"))));

    ExamSubmissionGradeResponse response =
        service.submitAndGrade("attempt-ready", request, "user-1");

    // READY + seal이면 정상 채점된다 (제출 가능 조건의 정상 경로)
    assertThat(response.totalScore()).isEqualByComparingTo("4.0000");
    assertThat(response.passed()).isTrue();
    assertThat(repo.savedAnswers).hasSize(2);
  }

  @Test
  @DisplayName("소유권 불일치 — CustomException 발생, 답안 저장 없음")
  void submitThrowsOnOwnerMismatch() {
    FakeSubmitRepository repo = new FakeSubmitRepository(seedAttempt(), seedSnapshot());
    ExamAttemptSubmitService service = submitService(repo);

    AttemptSubmitRequest request =
        new AttemptSubmitRequest(List.of(new SubmittedAnswerRequest("q-1", textNode("B"))));

    // user-2로 요청 → attempt는 user-1 소유
    assertThatThrownBy(() -> service.submitAndGrade("attempt-seed", request, "user-2"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));
    // 소유권 불일치이므로 답안 저장이 발생하지 않아야 함
    assertThat(repo.savedAnswers).isEmpty();
  }

  @Test
  @DisplayName("신규 제출 — 채점 완료 이벤트(ExamAttemptGradedEvent)가 정확히 1회 발행된다 (3단계)")
  void submitPublishesGradedEventOnce() {
    FakeSubmitRepository repo = new FakeSubmitRepository(seedAttempt(), seedSnapshot());
    // 발행 이벤트 캡처. 3단계(persistAnalysisAndPublishGraded)에서 발행되는지 확인한다
    List<Object> publishedEvents = new ArrayList<>();
    ExamAttemptSubmitService service = submitService(repo, publishedEvents::add);

    AttemptSubmitRequest request =
        new AttemptSubmitRequest(
            List.of(
                new SubmittedAnswerRequest("q-1", textNode("B")),
                new SubmittedAnswerRequest("q-2", textNode("A"))));

    ExamSubmissionGradeResponse response =
        service.submitAndGrade("attempt-seed", request, "user-1");

    // 채점 응답은 정상이고(fastApiClient=null이라 analysis는 null),
    // mockexam 복제용 이벤트가 정확히 1회 발행되어야 한다
    assertThat(response.totalScore()).isEqualByComparingTo("4.0000");
    assertThat(publishedEvents)
        .hasSize(1)
        .first()
        .isInstanceOf(com.swez.backend.domain.examgrading.event.ExamAttemptGradedEvent.class);
    com.swez.backend.domain.examgrading.event.ExamAttemptGradedEvent event =
        (com.swez.backend.domain.examgrading.event.ExamAttemptGradedEvent) publishedEvents.get(0);
    assertThat(event.attemptId()).isEqualTo("attempt-seed");
    assertThat(event.userId()).isEqualTo("user-1");
  }

  @Test
  @DisplayName("멱등 경로(이미 GRADED) — 채점 완료 이벤트가 발행되지 않는다")
  void idempotentResubmitDoesNotPublishGradedEvent() {
    ExamAttempt gradedAttempt =
        ExamAttempt.builder()
            .attemptId("attempt-graded")
            .examId("exam-seed")
            .user(User.builder().id("user-1").email("user1@test.com").build())
            .status(AttemptStatus.GRADED)
            .passPercentage(BigDecimal.valueOf(60))
            .answerKeySeal("sealed-key")
            .createdAt(OffsetDateTime.now())
            .build();
    ExamGradeResult existingGradeResult =
        new ExamGradeResult(
            "attempt-graded",
            "exam-seed",
            BigDecimal.valueOf(4.0000),
            BigDecimal.valueOf(4.0000),
            BigDecimal.valueOf(100.00),
            true,
            GradingStatus.GRADED,
            OffsetDateTime.now(),
            List.of());
    FakeIdempotentRepository repo =
        new FakeIdempotentRepository(gradedAttempt, seedSnapshot(), existingGradeResult);

    List<Object> publishedEvents = new ArrayList<>();
    ExamAttemptSubmitService service = submitService(repo, publishedEvents::add);

    AttemptSubmitRequest request =
        new AttemptSubmitRequest(List.of(new SubmittedAnswerRequest("q-1", textNode("B"))));
    service.submitAndGrade("attempt-graded", request, "user-1");

    // 멱등 경로는 mockexam 복제 이벤트를 재발행하지 않아야 한다 (중복 복제 방지)
    assertThat(publishedEvents).as("멱등 재제출 시 이벤트 발행 없음").isEmpty();
  }

  // 시드 데이터

  /** 시드 attempt: 객관식 2문항, 소유자 user-1 */
  private ExamAttempt seedAttempt() {
    return ExamAttempt.builder()
        .attemptId("attempt-seed")
        .examId("exam-seed")
        .user(User.builder().id("user-1").email("user1@test.com").build())
        .status(AttemptStatus.IN_PROGRESS)
        .passPercentage(BigDecimal.valueOf(60))
        .answerKeySeal("sealed-key")
        .createdAt(OffsetDateTime.now())
        .build();
  }

  /**
   * 제출 가능 상태 테스트용으로 지정 상태·봉인값을 가진 attempt를 만든다.
   *
   * @param seal 정답지 봉인값 (null/공백이면 봉인 누락 시뮬레이션)
   */
  private ExamAttempt attemptWithStatus(String attemptId, AttemptStatus status, String seal) {
    return ExamAttempt.builder()
        .attemptId(attemptId)
        .examId("exam-seed")
        .user(User.builder().id("user-1").email("user1@test.com").build())
        .status(status)
        .passPercentage(BigDecimal.valueOf(60))
        .answerKeySeal(seal)
        .createdAt(OffsetDateTime.now())
        .build();
  }

  /** 시드 채점 스냅샷: 객관식 2문항 (정답 B, A), attemptId=attempt-seed */
  private ExamAttemptSnapshot seedSnapshot() {
    return seedSnapshotFor("attempt-seed");
  }

  /** attemptId를 지정한 시드 채점 스냅샷: 객관식 2문항 (정답 B, A). */
  private ExamAttemptSnapshot seedSnapshotFor(String attemptId) {
    ExamQuestion q1 =
        new ExamQuestion(
            "q-1",
            "draft-1",
            "ko_multiple_choice_4",
            "HTTP",
            2,
            "remember",
            "HTTP 200의 의미는?",
            List.of(
                new QuestionOption("A", "인증 실패", false),
                new QuestionOption("B", "요청 성공", true),
                new QuestionOption("C", "서버 오류", false),
                new QuestionOption("D", "리다이렉트", false)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            "B",
            "서버 전용 해설1",
            "server-only-source1",
            BigDecimal.valueOf(2),
            null);
    ExamQuestion q2 =
        new ExamQuestion(
            "q-2",
            "draft-2",
            "ko_multiple_choice_4",
            "HTTP",
            2,
            "remember",
            "HTTP 404의 의미는?",
            List.of(
                new QuestionOption("A", "리소스 없음", true),
                new QuestionOption("B", "요청 성공", false),
                new QuestionOption("C", "서버 오류", false),
                new QuestionOption("D", "인증 필요", false)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            "A",
            "서버 전용 해설2",
            "server-only-source2",
            BigDecimal.valueOf(2),
            null);
    return new ExamAttemptSnapshot(
        attemptId, "exam-seed", "sealed-key", List.of(q1, q2), BigDecimal.valueOf(60));
  }

  private ExamAttemptSubmitService submitService(ExamAttemptRepositoryPort repo) {
    return submitService(repo, event -> {});
  }

  /**
   * eventPublisher를 받는 조립 헬퍼. 이벤트 발행 테스트에서 캡처용 람다를 넘긴다.
   *
   * <p>3단계 트랜잭션 구조에 맞춰 영속 서비스(ExamAttemptSubmitPersistenceService)를 먼저 조립해 ExamAttemptSubmitService에 넣는다.
   */
  private ExamAttemptSubmitService submitService(
      ExamAttemptRepositoryPort repo,
      org.springframework.context.ApplicationEventPublisher eventPublisher) {
    ExamGradingMapper mapper = new ExamGradingMapper();
    GradingLeakGuard leakGuard = new GradingLeakGuard(objectMapper);
    DeterministicQuestionScorer scorer = new DeterministicQuestionScorer();
    // FakeRubricPort: MCQ 시드라 루브릭 경로를 타지 않는다
    ExamForgeRubricGradingPort rubricPort = (snapshot, answers) -> List.of();
    ExamSubmissionGradingService gradingService =
        new ExamSubmissionGradingService(repo, rubricPort, scorer, mapper, leakGuard);
    // 1·3단계 트랜잭션 구간의 영속 서비스. 단위 테스트라 프록시 없이 직접 호출된다.
    ExamAttemptSubmitPersistenceService persistenceService =
        new ExamAttemptSubmitPersistenceService(
            repo, gradingService, mapper, leakGuard, objectMapper, eventPublisher);
    // fastApiClient=null이면 requestAnalysis가 NPE를 잡고 총평을 null로 둔다.
    return new ExamAttemptSubmitService(
        persistenceService, mapper, null, new ReviewAnswerFormatter(objectMapper), objectMapper);
  }

  private JsonNode textNode(String value) {
    return objectMapper.valueToTree(value);
  }

  // Fake 저장소 구현체

  private static final class FakeSubmitRepository implements ExamAttemptRepositoryPort {
    // AI 총평 포트 메서드 (테스트용 인메모리 구현)
    private String analysisJson;

    @Override
    public void saveAnalysisJson(String attemptId, String json) {
      this.analysisJson = json;
    }

    @Override
    public java.util.Optional<String> findAnalysisJson(String attemptId) {
      return java.util.Optional.ofNullable(analysisJson);
    }

    private final ExamAttempt attempt;
    private final ExamAttemptSnapshot snapshot;
    final List<SubmittedAnswer> savedAnswers = new ArrayList<>();
    ExamGradeResult savedResult;

    FakeSubmitRepository(ExamAttempt attempt, ExamAttemptSnapshot snapshot) {
      this.attempt = attempt;
      this.snapshot = snapshot;
    }

    @Override
    public Optional<ExamAttemptSnapshot> findGradingSnapshot(String attemptId) {
      return snapshot.attemptId().equals(attemptId) ? Optional.of(snapshot) : Optional.empty();
    }

    @Override
    public void saveGradingResult(ExamGradeResult result) {
      this.savedResult = result;
    }

    @Override
    public Optional<ExamAttempt> findAttemptByOwner(String attemptId, String userId) {
      if (attempt.getAttemptId().equals(attemptId)
          && attempt.getUser() != null
          && userId.equals(attempt.getUser().getId())) {
        return Optional.of(attempt);
      }
      return Optional.empty();
    }

    @Override
    public List<ExamAttemptQuestion> findQuestionsByAttemptId(String attemptId) {
      return List.of();
    }

    @Override
    public void saveSubmittedAnswers(String attemptId, List<SubmittedAnswer> answers) {
      savedAnswers.addAll(answers);
    }

    @Override
    public Optional<ExamGradeResult> findGradeResult(String attemptId) {
      return Optional.empty();
    }

    @Override
    public List<QuestionReviewData> findReviewData(String attemptId) {
      // 제출·채점 경로 테스트에서는 복습 데이터를 사용하지 않으므로 빈 목록 반환
      return List.of();
    }
  }

  /**
   * 멱등성 검증용 Fake 저장소. 이미 채점이 끝난 attempt를 흉내 낸다.
   *
   * <p>savedAnswersCount·savedResultCount 카운터를 통해 재채점·재저장이 발생하지 않음을 단언한다.
   */
  private static final class FakeIdempotentRepository implements ExamAttemptRepositoryPort {
    // AI 총평 포트 메서드 (테스트용 인메모리 구현)
    private String analysisJson;

    @Override
    public void saveAnalysisJson(String attemptId, String json) {
      this.analysisJson = json;
    }

    @Override
    public java.util.Optional<String> findAnalysisJson(String attemptId) {
      return java.util.Optional.ofNullable(analysisJson);
    }

    private final ExamAttempt attempt;
    private final ExamAttemptSnapshot snapshot;
    private final ExamGradeResult existingResult;

    /** 재제출 시 saveSubmittedAnswers 호출 횟수. 멱등하면 0이어야 한다 */
    int savedAnswersCount = 0;

    /** 재제출 시 saveGradingResult 호출 횟수. 멱등하면 0이어야 한다 */
    int savedResultCount = 0;

    FakeIdempotentRepository(
        ExamAttempt attempt, ExamAttemptSnapshot snapshot, ExamGradeResult existingResult) {
      this.attempt = attempt;
      this.snapshot = snapshot;
      this.existingResult = existingResult;
    }

    @Override
    public Optional<ExamAttemptSnapshot> findGradingSnapshot(String attemptId) {
      return snapshot.attemptId().equals(attemptId) ? Optional.of(snapshot) : Optional.empty();
    }

    @Override
    public void saveGradingResult(ExamGradeResult result) {
      // 재제출 시 호출되면 안 되므로 호출 횟수를 센다
      savedResultCount++;
    }

    @Override
    public Optional<ExamAttempt> findAttemptByOwner(String attemptId, String userId) {
      if (attempt.getAttemptId().equals(attemptId)
          && attempt.getUser() != null
          && userId.equals(attempt.getUser().getId())) {
        return Optional.of(attempt);
      }
      return Optional.empty();
    }

    @Override
    public List<ExamAttemptQuestion> findQuestionsByAttemptId(String attemptId) {
      return List.of();
    }

    @Override
    public void saveSubmittedAnswers(String attemptId, List<SubmittedAnswer> answers) {
      // 재제출 시 호출되면 안 되므로 호출 횟수를 센다
      savedAnswersCount += answers.size();
    }

    @Override
    public Optional<ExamGradeResult> findGradeResult(String attemptId) {
      // 이미 채점된 결과. 멱등 경로는 이 값을 응답으로 돌려준다
      if (attempt.getAttemptId().equals(attemptId)) {
        return Optional.of(existingResult);
      }
      return Optional.empty();
    }

    @Override
    public List<QuestionReviewData> findReviewData(String attemptId) {
      // 멱등성 검증 테스트에서는 복습 데이터를 사용하지 않으므로 빈 목록 반환
      return List.of();
    }
  }
}
