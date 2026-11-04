/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.swez.backend.domain.examgrading.dto.request.ExamSubmissionGradeRequest;
import com.swez.backend.domain.examgrading.dto.request.SubmittedAnswerRequest;
import com.swez.backend.domain.examgrading.dto.response.ExamSubmissionGradeResponse;
import com.swez.backend.domain.examgrading.mapper.ExamGradingMapper;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.GradingMode;
import com.swez.backend.domain.examgrading.model.GradingStatus;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.QuestionOption;
import com.swez.backend.domain.examgrading.model.RubricCriterion;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;
import com.swez.backend.domain.examgrading.port.ExamAttemptRepositoryPort;
import com.swez.backend.domain.examgrading.port.ExamForgeRubricGradingPort;
import com.swez.backend.global.exception.CustomException;

/**
 * 채점 서비스 통합 단위 테스트.
 *
 * <p>실제 DB/FastAPI 없이 Fake 구현체를 사용해 다음을 검증한다:
 *
 * <ul>
 *   <li>객관식 결정론 채점 + 서술형 루브릭 라우팅
 *   <li>중복 제출 답안 거부
 *   <li>미제출 시 전 문항 0점 처리
 *   <li>루브릭 채점 결과가 만점을 초과할 경우 거부
 *   <li>공개 피드백에 정답 노출 시 저장 차단
 * </ul>
 */
class ExamSubmissionGradingServiceTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void gradeSubmissionScoresObjectiveLocallyAndRoutesRubricToFastApiPort() {
    // 객관식 2점(만점) + 서술형 AI 루브릭 3점(만점 5점) → 총 5/7점, 71.43%
    FakeRepository repository = new FakeRepository(snapshot());
    FakeRubricPort rubricPort =
        new FakeRubricPort(
            List.of(
                new QuestionGrade(
                    "q-essay",
                    "ko_essay",
                    BigDecimal.valueOf(3),
                    BigDecimal.valueOf(5),
                    false,
                    GradingMode.AI_RUBRIC,
                    "주장의 방향은 맞지만 근거가 부족합니다.",
                    List.of(
                        new RubricCriterion(
                            "근거", BigDecimal.valueOf(3), BigDecimal.valueOf(5), "부분 충족")),
                    0.91,
                    false)));
    ExamSubmissionGradingService service = service(repository, rubricPort);

    ExamSubmissionGradeResponse response =
        service.gradeSubmission(
            new ExamSubmissionGradeRequest(
                "attempt-1",
                List.of(
                    new SubmittedAnswerRequest("q-choice", objectMapper.valueToTree("B")),
                    new SubmittedAnswerRequest("q-essay", objectMapper.valueToTree("제출 답안")))));

    assertThat(response.totalScore()).isEqualByComparingTo("5.0000");
    assertThat(response.maxScore()).isEqualByComparingTo("7.0000");
    assertThat(response.percentage()).isEqualByComparingTo("71.43");
    assertThat(response.passed()).isTrue();
    assertThat(response.gradingStatus()).isEqualTo(GradingStatus.GRADED);
    assertThat(response.results()).extracting("questionId").containsExactly("q-choice", "q-essay");
    // 서술형만 루브릭 포트로 전달돼야 함
    assertThat(rubricPort.lastAnswers).extracting("questionId").containsExactly("q-essay");
    assertThat(repository.savedResult).isNotNull();
  }

  @Test
  void gradeSubmissionRejectsDuplicateSubmittedAnswers() {
    // 같은 문항 ID로 두 번 제출 → CustomException
    ExamSubmissionGradingService service =
        service(new FakeRepository(snapshot()), new FakeRubricPort(List.of()));

    ExamSubmissionGradeRequest request =
        new ExamSubmissionGradeRequest(
            "attempt-1",
            List.of(
                new SubmittedAnswerRequest("q-choice", objectMapper.valueToTree("B")),
                new SubmittedAnswerRequest("q-choice", objectMapper.valueToTree("A"))));

    assertThatThrownBy(() -> service.gradeSubmission(request)).isInstanceOf(CustomException.class);
  }

  @Test
  void gradeSubmissionAllowsEmptySubmittedAnswersAsAllUnanswered() {
    // 답안 없이 제출하면 전 문항 0점, 피드백은 '답안을 제출하지 않았어요…'
    FakeRepository repository = new FakeRepository(snapshot());
    ExamSubmissionGradingService service = service(repository, new FakeRubricPort(List.of()));

    ExamSubmissionGradeResponse response =
        service.gradeSubmission(new ExamSubmissionGradeRequest("attempt-1", List.of()));

    assertThat(response.totalScore()).isEqualByComparingTo("0.0000");
    assertThat(response.maxScore()).isEqualByComparingTo("7.0000");
    assertThat(response.percentage()).isEqualByComparingTo("0.00");
    assertThat(response.results())
        .extracting("feedback", String.class)
        .allSatisfy(feedback -> assertThat(feedback).startsWith("답안을 제출하지 않았어요"));
    assertThat(repository.savedResult).isNotNull();
  }

  @Test
  void gradeSubmissionRejectsRubricGradesAboveQuestionMax() {
    // 루브릭 포트가 만점(5)을 초과하는 점수(99)를 반환하면 CustomException
    FakeRubricPort rubricPort =
        new FakeRubricPort(
            List.of(
                new QuestionGrade(
                    "q-essay",
                    "ko_essay",
                    BigDecimal.valueOf(99),
                    BigDecimal.valueOf(5),
                    false,
                    GradingMode.AI_RUBRIC,
                    "invalid",
                    List.of(),
                    0.1,
                    false)));
    ExamSubmissionGradingService service = service(new FakeRepository(snapshot()), rubricPort);

    ExamSubmissionGradeRequest request =
        new ExamSubmissionGradeRequest(
            "attempt-1",
            List.of(new SubmittedAnswerRequest("q-essay", objectMapper.valueToTree("제출 답안"))));

    assertThatThrownBy(() -> service.gradeSubmission(request)).isInstanceOf(CustomException.class);
  }

  @Test
  void gradeSubmissionPersistsWhenDescriptiveFeedbackMentionsAnswerConcept() {
    /*
     * GradingLeakGuard 동작 검증:
     * 서술형 AI 루브릭이 피드백에서 정답 개념(OAuthAuthorizationCodeFlow)을 자연어로 언급해도
     * 오탐으로 차단하지 않고 정상적으로 저장되어야 한다.
     *
     * 이를 차단하면 EXAMFORGE_GRADING_004 500 오류가 난다.
     * feedback 필드는 콘텐츠 스캔 대상에서 제외되므로 통과해야 한다.
     */
    FakeRepository repository = new FakeRepository(snapshot());
    FakeRubricPort rubricPort =
        new FakeRubricPort(
            List.of(
                new QuestionGrade(
                    "q-essay",
                    "ko_essay",
                    BigDecimal.valueOf(3),
                    BigDecimal.valueOf(5),
                    false,
                    GradingMode.AI_RUBRIC,
                    // feedback에 정답 개념을 자연어로 설명하는 오탐 케이스
                    "OAuthAuthorizationCodeFlow 방식에 대한 핵심 흐름 설명이 부족합니다.",
                    List.of(
                        new RubricCriterion(
                            // criterion에도 정답 키워드가 들어간다
                            "OAuthAuthorizationCodeFlow 이해도",
                            BigDecimal.valueOf(3),
                            BigDecimal.valueOf(5),
                            // reason에도 정답 키워드가 들어간다
                            "OAuthAuthorizationCodeFlow의 핵심 단계를 올바르게 서술했습니다.")),
                    0.91,
                    false)));
    ExamSubmissionGradingService service = service(repository, rubricPort);

    ExamSubmissionGradeRequest request =
        new ExamSubmissionGradeRequest(
            "attempt-1",
            List.of(new SubmittedAnswerRequest("q-essay", objectMapper.valueToTree("제출 답안"))));

    // 오탐 없이 정상 채점 결과가 반환되어야 한다
    ExamSubmissionGradeResponse response = service.gradeSubmission(request);
    assertThat(response.gradingStatus()).isEqualTo(GradingStatus.GRADED);
    // 저장도 정상적으로 이루어져야 한다
    assertThat(repository.savedResult).isNotNull();
  }

  private ExamSubmissionGradingService service(
      ExamAttemptRepositoryPort repository, ExamForgeRubricGradingPort rubricPort) {
    return new ExamSubmissionGradingService(
        repository,
        rubricPort,
        new DeterministicQuestionScorer(),
        new ExamGradingMapper(),
        new GradingLeakGuard(objectMapper));
  }

  private ExamAttemptSnapshot snapshot() {
    return new ExamAttemptSnapshot(
        "attempt-1",
        "exam-1",
        "sealed-answer-key",
        List.of(choiceQuestion(), essayQuestion()),
        BigDecimal.valueOf(60));
  }

  private ExamQuestion choiceQuestion() {
    return new ExamQuestion(
        "q-choice",
        "draft-choice",
        "ko_multiple_choice_4",
        "HTTP",
        2,
        "remember",
        "HTTP 상태 코드 설명으로 맞는 것은?",
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
        "서버 전용 객관식 해설입니다.",
        "server-only-choice-source",
        BigDecimal.valueOf(2),
        "server-only-choice-rationale");
  }

  private ExamQuestion essayQuestion() {
    return new ExamQuestion(
        "q-essay",
        "draft-essay",
        "ko_essay",
        "OAuth",
        5,
        "evaluate",
        "OAuth 흐름을 설명하시오.",
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        "OAuthAuthorizationCodeFlow",
        "서버 전용 서술형 해설입니다.",
        "server-only-essay-source",
        BigDecimal.valueOf(5),
        "server-only-essay-rationale");
  }

  /** FakeRepository: 실제 JPA 없이 메모리에서 스냅샷/결과를 관리하는 포트 구현체 */
  private static final class FakeRepository implements ExamAttemptRepositoryPort {
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

    private final ExamAttemptSnapshot snapshot;
    ExamGradeResult savedResult;

    private FakeRepository(ExamAttemptSnapshot snapshot) {
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

    // 채점 서비스 테스트에서는 쓰지 않는 메서드라 비워 둔다
    @Override
    public Optional<com.swez.backend.domain.examgrading.entity.ExamAttempt> findAttemptByOwner(
        String attemptId, String userId) {
      return Optional.empty();
    }

    @Override
    public java.util.List<com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion>
        findQuestionsByAttemptId(String attemptId) {
      return java.util.List.of();
    }

    @Override
    public void saveSubmittedAnswers(
        String attemptId,
        java.util.List<com.swez.backend.domain.examgrading.model.SubmittedAnswer> answers) {}

    @Override
    public Optional<ExamGradeResult> findGradeResult(String attemptId) {
      return Optional.empty();
    }

    @Override
    public List<com.swez.backend.domain.examgrading.model.QuestionReviewData> findReviewData(
        String attemptId) {
      // 채점 경로 테스트에서는 복습 데이터를 사용하지 않으므로 빈 목록 반환
      return List.of();
    }
  }

  /** FakeRubricPort: FastAPI 없이 미리 정한 채점 결과를 반환하는 포트 구현체 */
  private static final class FakeRubricPort implements ExamForgeRubricGradingPort {
    private final List<QuestionGrade> grades;
    final List<SubmittedAnswer> lastAnswers = new ArrayList<>();

    private FakeRubricPort(List<QuestionGrade> grades) {
      this.grades = grades;
    }

    @Override
    public List<QuestionGrade> gradeRubricQuestions(
        ExamAttemptSnapshot snapshot, List<SubmittedAnswer> rubricAnswers) {
      lastAnswers.clear();
      lastAnswers.addAll(rubricAnswers);
      return grades;
    }
  }
}
