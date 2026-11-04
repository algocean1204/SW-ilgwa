/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.examgrading.dto.response.AttemptResultResponse;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion;
import com.swez.backend.domain.examgrading.enums.AttemptStatus;
import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.GradingMode;
import com.swez.backend.domain.examgrading.model.GradingStatus;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.QuestionReviewData;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;
import com.swez.backend.domain.examgrading.port.ExamAttemptRepositoryPort;
import com.swez.backend.domain.examgrading.support.ReviewAnswerFormatter;
import com.swez.backend.global.exception.CustomException;

/**
 * 응시 결과 조회 서비스 단위 테스트.
 *
 * <p>시드 attempt(MCQ 2문항 채점 완료)로 다음을 검증한다:
 *
 * <ul>
 *   <li>정상 조회: 총점·합격·문항별 결과 확인
 *   <li>소유권 불일치 → CustomException
 *   <li>채점 전 조회 → CustomException
 * </ul>
 */
class ExamAttemptResultServiceTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  @DisplayName("정상 조회 — 채점 완료 결과 반환 (총점 4/4, 합격)")
  void getAttemptResultReturnsScoredResult() {
    FakeResultRepository repo = new FakeResultRepository(seedAttempt(), seedGradeResult());
    ExamAttemptResultService service = resultService(repo);

    AttemptResultResponse response = service.getAttemptResult("attempt-seed", "user-1");

    assertThat(response.attemptId()).isEqualTo("attempt-seed");
    assertThat(response.totalScore()).isEqualByComparingTo("4.0000");
    assertThat(response.maxScore()).isEqualByComparingTo("4.0000");
    assertThat(response.percentage()).isEqualByComparingTo("100.00");
    assertThat(response.passed()).isTrue();
    assertThat(response.gradingStatus()).isEqualTo(GradingStatus.GRADED);
    assertThat(response.results()).hasSize(2);
    // questionId non-null 단언
    assertThat(response.results()).allSatisfy(r -> assertThat(r.questionId()).isNotNull());
    // templateId non-null 단언. null이면 프론트 ExamResultQuestionCard.tsx가 깨진다
    assertThat(response.results())
        .allSatisfy(
            r ->
                assertThat(r.templateId())
                    .as("templateId가 null이면 프론트 item.templateId.includes() 크래시 발생")
                    .isNotNull());
  }

  @Test
  @DisplayName("templateId 값 일치 — 각 문항 templateId가 seedGradeResult 값과 일치")
  void getAttemptResultTemplateIdMatchesSeedGradeResult() {
    FakeResultRepository repo = new FakeResultRepository(seedAttempt(), seedGradeResult());
    ExamAttemptResultService service = resultService(repo);

    AttemptResultResponse response = service.getAttemptResult("attempt-seed", "user-1");

    assertThat(response.results()).hasSize(2);
    // q-1 → ko_multiple_choice_5
    assertThat(
            response.results().stream()
                .filter(r -> "q-1".equals(r.questionId()))
                .findFirst()
                .map(r -> r.templateId())
                .orElse(null))
        .isEqualTo("ko_multiple_choice_5");
    // q-2 → ko_descriptive (서술형 포함 케이스)
    assertThat(
            response.results().stream()
                .filter(r -> "q-2".equals(r.questionId()))
                .findFirst()
                .map(r -> r.templateId())
                .orElse(null))
        .isEqualTo("ko_descriptive");
  }

  @Test
  @DisplayName("소유권 불일치 → CustomException")
  void getAttemptResultThrowsOnOwnerMismatch() {
    FakeResultRepository repo = new FakeResultRepository(seedAttempt(), seedGradeResult());
    ExamAttemptResultService service = resultService(repo);

    assertThatThrownBy(() -> service.getAttemptResult("attempt-seed", "user-2"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));
  }

  @Test
  @DisplayName("채점 결과 없음 — CustomException (채점 전 조회 시)")
  void getAttemptResultThrowsWhenNoGradeResult() {
    // 채점 결과를 empty로 반환하는 저장소
    FakeResultRepository repo = new FakeResultRepository(seedAttempt(), null);
    ExamAttemptResultService service = resultService(repo);

    assertThatThrownBy(() -> service.getAttemptResult("attempt-seed", "user-1"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));
  }

  // 시드 데이터

  private ExamAttempt seedAttempt() {
    return ExamAttempt.builder()
        .attemptId("attempt-seed")
        .examId("exam-seed")
        .user(User.builder().id("user-1").email("user1@test.com").build())
        .status(AttemptStatus.GRADED)
        .passPercentage(BigDecimal.valueOf(60))
        .answerKeySeal("sealed-key")
        .createdAt(OffsetDateTime.now())
        .build();
  }

  /** 시드 채점 결과: 객관식 + 서술형 2문항, 총점 4/4, 합격. templateId를 명시해 non-null 단언을 통과하게 한다. */
  private ExamGradeResult seedGradeResult() {
    // q-1: 객관식(ko_multiple_choice_5), templateId 명시
    QuestionGrade grade1 =
        new QuestionGrade(
            "q-1",
            "ko_multiple_choice_5",
            BigDecimal.valueOf(2),
            BigDecimal.valueOf(2),
            true,
            GradingMode.DETERMINISTIC,
            "정답입니다.",
            List.of(),
            1.0,
            false);
    // q-2: 서술형(ko_descriptive), templateId 명시 (null이면 프론트가 깨진다)
    QuestionGrade grade2 =
        new QuestionGrade(
            "q-2",
            "ko_descriptive",
            BigDecimal.valueOf(2),
            BigDecimal.valueOf(2),
            true,
            GradingMode.DETERMINISTIC,
            "잘 작성했습니다.",
            List.of(),
            1.0,
            false);
    return new ExamGradeResult(
        "attempt-seed",
        "exam-seed",
        BigDecimal.valueOf(4).setScale(4),
        BigDecimal.valueOf(4).setScale(4),
        new BigDecimal("100.00"),
        true,
        GradingStatus.GRADED,
        OffsetDateTime.now(),
        List.of(grade1, grade2));
  }

  private ExamAttemptResultService resultService(ExamAttemptRepositoryPort repo) {
    return new ExamAttemptResultService(
        repo,
        new GradingLeakGuard(objectMapper),
        new ReviewAnswerFormatter(objectMapper),
        objectMapper);
  }

  // Fake 저장소 구현체

  private static final class FakeResultRepository implements ExamAttemptRepositoryPort {
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
    private final ExamGradeResult gradeResult;

    FakeResultRepository(ExamAttempt attempt, ExamGradeResult gradeResult) {
      this.attempt = attempt;
      this.gradeResult = gradeResult;
    }

    @Override
    public Optional<ExamAttemptSnapshot> findGradingSnapshot(String attemptId) {
      return Optional.empty();
    }

    @Override
    public void saveGradingResult(ExamGradeResult result) {}

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
    public void saveSubmittedAnswers(String attemptId, List<SubmittedAnswer> answers) {}

    @Override
    public Optional<ExamGradeResult> findGradeResult(String attemptId) {
      return Optional.ofNullable(gradeResult);
    }

    @Override
    public List<QuestionReviewData> findReviewData(String attemptId) {
      // 결과 조회 테스트에서는 복습 데이터 자체를 검증하지 않으므로 빈 목록 반환
      return List.of();
    }
  }
}
