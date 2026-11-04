/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.swez.backend.domain.examgrading.dto.response.ExamSubmissionGradeResponse;
import com.swez.backend.domain.examgrading.dto.response.QuestionGradeResponse;
import com.swez.backend.domain.examgrading.dto.response.RubricCriterionResponse;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.GradingMode;
import com.swez.backend.domain.examgrading.model.GradingStatus;
import com.swez.backend.domain.examgrading.model.QuestionOption;
import com.swez.backend.global.exception.CustomException;

/**
 * GradingLeakGuard 단위 테스트.
 *
 * <h3>검증 범위</h3>
 *
 * <ul>
 *   <li>필드명 차단: 원시 정답 키(correctAnswer·answerKeySeal 등)가 JSON 키로 존재하면 차단
 *   <li>피드백 텍스트 허용: AI 루브릭이 생성하는 feedback·reason·criterion은 콘텐츠 스캔 대상이 아니라서 정답 개념을 자연어로 설명해도 통과해야 함
 *       (오탐 방지)
 *   <li>콘텐츠 스캔 차단: message 필드에 원시 정답키가 그대로 포함되면 차단
 * </ul>
 */
class GradingLeakGuardTest {

  private final GradingLeakGuard leakGuard =
      new GradingLeakGuard(JsonMapper.builder().findAndAddModules().build());

  // 정상 통과 케이스: 서술형 루브릭 피드백은 차단하지 않아야 함

  @Test
  @DisplayName("서술형 루브릭 — 정답 언급 없는 일반 피드백은 통과")
  void allowsDescriptiveFeedbackWithoutAnswerMention() {
    // feedback에 정답이 전혀 없으면 통과한다
    ExamSubmissionGradeResponse response =
        response("풀이 방향은 부분적으로 맞지만 핵심 조건 설명이 부족합니다.", "개념 정확성", "핵심 용어를 정확히 사용했습니다.");

    assertThatCode(() -> leakGuard.assertPublicResponseSafe(response, essaySnapshot()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("서술형 루브릭 — 피드백이 정답 개념을 자연어로 설명해도 통과 (오탐 방지 핵심)")
  void allowsDescriptiveFeedbackMentioningAnswerConcept() {
    /*
     * AI 루브릭 채점에서 FastAPI는 피드백에 정답 개념을 자연어로 설명할 수 있다.
     * 예: "OAuthAuthorizationCodeFlow 방식에 대한 설명이 핵심 요소를 갖추었습니다."
     * 정상적인 채점 피드백이므로 차단하면 안 된다.
     */
    ExamSubmissionGradeResponse response =
        response(
            "OAuthAuthorizationCodeFlow 방식에 대한 설명이 핵심 요소를 갖추었습니다.",
            "개념 적용",
            "OAuthAuthorizationCodeFlow의 흐름을 올바르게 서술했습니다.");

    assertThatCode(() -> leakGuard.assertPublicResponseSafe(response, essaySnapshot()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("서술형 루브릭 — criterion에 정답 개념 포함되어도 통과 (오탐 방지)")
  void allowsRubricCriterionMentioningAnswerConcept() {
    // criterion 필드는 AI가 채점 기준명으로 자동 생성하며 정답 관련 용어를 포함할 수 있다
    ExamSubmissionGradeResponse response =
        response("전반적으로 잘 설명하였습니다.", "OAuthAuthorizationCodeFlow 이해도", "핵심 흐름을 올바르게 서술했습니다.");

    assertThatCode(() -> leakGuard.assertPublicResponseSafe(response, essaySnapshot()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("객관식 — 정답·해설 필드가 없는 안전한 응답은 통과")
  void allowsSafeChoiceResponseWithNoSensitiveFields() {
    // 객관식 문항의 채점 결과에 정답 레이블·해설이 없으면 통과
    ExamSubmissionGradeResponse response = response("정답 보기를 선택했습니다.", "정확도", "올바른 보기를 선택했습니다.");

    assertThatCode(() -> leakGuard.assertPublicResponseSafe(response, choiceSnapshot()))
        .doesNotThrowAnyException();
  }

  // 필드명 차단: DTO 구조에 민감 키가 있으면 항상 차단

  @Test
  @DisplayName("필드명 차단 — answerKeySeal 키가 응답에 포함되면 차단")
  void blocksSensitiveFieldName_answerKeySeal() {
    assertThatThrownBy(
            () -> leakGuard.assertPublicResponseSafe(Map.of("answerKeySeal", "sealed-value")))
        .isInstanceOf(CustomException.class);
  }

  @Test
  @DisplayName("필드명 차단 — correctAnswer 키가 응답에 포함되면 차단")
  void blocksSensitiveFieldName_correctAnswer() {
    assertThatThrownBy(
            () ->
                leakGuard.assertPublicResponseSafe(
                    Map.of("correctAnswer", "OAuthAuthorizationCodeFlow")))
        .isInstanceOf(CustomException.class);
  }

  @Test
  @DisplayName("필드명 차단 — explanation 키가 응답에 포함되면 차단")
  void blocksSensitiveFieldName_explanation() {
    assertThatThrownBy(
            () -> leakGuard.assertPublicResponseSafe(Map.of("explanation", "서버 전용 해설입니다.")))
        .isInstanceOf(CustomException.class);
  }

  @Test
  @DisplayName("필드명 차단 — correct_answer(스네이크 케이스) 키도 차단")
  void blocksSensitiveFieldName_correct_answer_snake_case() {
    assertThatThrownBy(
            () ->
                leakGuard.assertPublicResponseSafe(
                    Map.of("correct_answer", "OAuthAuthorizationCodeFlow")))
        .isInstanceOf(CustomException.class);
  }

  // 콘텐츠 스캔 차단: message 필드에 원시 정답이 포함되면 차단
  // (실제 누수 시나리오: message 필드를 통한 우회 시도)

  @Test
  @DisplayName("콘텐츠 스캔 — message 필드에 서술형 정답 원문이 포함되면 차단")
  void blocksRawAnswerValueInMessageField() {
    /*
     * message 필드는 에러 메시지·시스템 안내 등에 사용되며 콘텐츠 스캔 대상이다.
     * 이 필드에 원시 정답(OAuthAuthorizationCodeFlow)이 그대로 포함되면 차단한다.
     */
    Map<String, Object> responseWithMessage =
        Map.of("message", "정답은 OAuthAuthorizationCodeFlow 입니다.");

    assertThatThrownBy(
            () -> leakGuard.assertPublicResponseSafe(responseWithMessage, essaySnapshot()))
        .isInstanceOf(CustomException.class);
  }

  @Test
  @DisplayName("콘텐츠 스캔 — snapshot 없이 호출하면 필드명 검사만 수행하고 통과")
  void allowsResponseWithoutSnapshot() {
    // 결과 조회 경로는 snapshot=null이라 콘텐츠 스캔을 건너뛴다
    ExamSubmissionGradeResponse response =
        response("OAuthAuthorizationCodeFlow 방식이 핵심입니다.", "개념 적용", "올바르게 서술했습니다.");

    assertThatCode(() -> leakGuard.assertPublicResponseSafe(response)).doesNotThrowAnyException();
  }

  // 픽스처 헬퍼 메서드

  /**
   * feedback·criterion·reason을 포함하는 테스트용 채점 응답을 생성한다. ExamSubmissionGradeResponse와 동일한 구조를 사용하여 실제
   * 응답과 같은 방식으로 직렬화된다.
   */
  private ExamSubmissionGradeResponse response(String feedback, String criterion, String reason) {
    return ExamSubmissionGradeResponse.builder()
        .attemptId("attempt-1")
        .examId("exam-1")
        .totalScore(BigDecimal.ONE)
        .maxScore(BigDecimal.TEN)
        .percentage(BigDecimal.TEN)
        .passed(false)
        .gradingStatus(GradingStatus.GRADED)
        .gradedAt(OffsetDateTime.now())
        .results(
            List.of(
                QuestionGradeResponse.builder()
                    .questionId("q-essay")
                    .templateId("ko_essay")
                    .score(BigDecimal.ONE)
                    .maxScore(BigDecimal.TEN)
                    .correct(false)
                    .gradingMode(GradingMode.AI_RUBRIC)
                    .feedback(feedback)
                    .rubricBreakdown(
                        List.of(
                            RubricCriterionResponse.builder()
                                .criterion(criterion)
                                .score(BigDecimal.ONE)
                                .maxScore(BigDecimal.TEN)
                                .reason(reason)
                                .build()))
                    .confidence(0.8)
                    .needsManualReview(false)
                    .build()))
        .build();
  }

  /** 서술형 문항 스냅샷 (긴 정답 문자열 포함). */
  private ExamAttemptSnapshot essaySnapshot() {
    return new ExamAttemptSnapshot(
        "attempt-1",
        "exam-1",
        "sealed-answer-key",
        List.of(
            new ExamQuestion(
                "q-essay",
                "draft-1",
                "ko_essay",
                "topic",
                4,
                "evaluate",
                "stem",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                "OAuthAuthorizationCodeFlow",
                "서버 전용 해설입니다.",
                "server-only-source-reference",
                BigDecimal.TEN,
                "server-only-distractor-rationale")),
        BigDecimal.valueOf(60));
  }

  /** 객관식 문항 스냅샷 (짧은 보기 레이블 "B" 포함). */
  private ExamAttemptSnapshot choiceSnapshot() {
    return new ExamAttemptSnapshot(
        "attempt-choice",
        "exam-choice",
        "sealed-answer-key",
        List.of(
            new ExamQuestion(
                "q-choice",
                "draft-1",
                "ko_multiple_choice_4",
                "topic",
                2,
                "remember",
                "stem",
                List.of(
                    new QuestionOption("A", "오답", false), new QuestionOption("B", "정답 보기", true)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                "B",
                "서버 전용 해설입니다.",
                "server-only-source-reference",
                BigDecimal.TEN,
                "server-only-distractor-rationale")),
        BigDecimal.valueOf(60));
  }
}
