/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.MatchingPair;
import com.swez.backend.domain.examgrading.model.QuestionGrade;

/** 결정론 채점기의 빈칸/연결 유형 정확도와 부분 점수 단위 테스트 */
class DeterministicQuestionScorerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DeterministicQuestionScorer scorer = new DeterministicQuestionScorer();

  @Test
  void scoresBlankAnswersByPosition() {
    // HTTP 맞고 500 틀려서 4점 만점에 2점 기대
    ExamQuestion question =
        baseQuestion("q-blank", "ko_fill_blank", BigDecimal.valueOf(4))
            .withBlankAnswers(List.of("HTTP", "404"))
            .build();

    QuestionGrade grade = scorer.score(question, objectMapper.valueToTree(List.of("http", "500")));

    assertThat(grade.score()).isEqualByComparingTo("2.0000");
    assertThat(grade.maxScore()).isEqualByComparingTo("4.0000");
    assertThat(grade.correct()).isFalse();
    assertThat(grade.rubricBreakdown()).hasSize(2);
    // 피드백에는 맞힌 개수와 정답이 들어간다
    assertThat(grade.feedback()).contains("2개 중 1개").contains("HTTP").contains("404");
  }

  @Test
  void scoresMatchingAnswersByPair() {
    // GET-조회 맞고 POST-수정 틀려서 6점 만점에 3점 기대
    ExamQuestion question =
        baseQuestion("q-match", "ko_matching", BigDecimal.valueOf(6))
            .withMatchingPairs(
                List.of(new MatchingPair("GET", "조회"), new MatchingPair("POST", "생성")))
            .build();

    QuestionGrade grade =
        scorer.score(
            question,
            objectMapper.valueToTree(
                List.of(
                    java.util.Map.of("left", "GET", "right", "조회"),
                    java.util.Map.of("left", "POST", "right", "수정"))));

    assertThat(grade.score()).isEqualByComparingTo("3.0000");
    assertThat(grade.maxScore()).isEqualByComparingTo("6.0000");
    assertThat(grade.rubricBreakdown()).extracting("reason").containsExactly("연결 일치", "연결 불일치");
    // 피드백에는 맞힌 개수와 정답 연결이 들어간다
    assertThat(grade.feedback()).contains("2개 중 1개").contains("정답 연결").contains("GET");
  }

  private TestQuestionBuilder baseQuestion(
      String questionId, String templateId, BigDecimal points) {
    return new TestQuestionBuilder(questionId, templateId, points);
  }

  private record TestQuestionBuilder(
      String questionId,
      String templateId,
      BigDecimal points,
      List<String> blankAnswers,
      List<MatchingPair> matchingPairs) {

    TestQuestionBuilder(String questionId, String templateId, BigDecimal points) {
      this(questionId, templateId, points, List.of(), List.of());
    }

    TestQuestionBuilder withBlankAnswers(List<String> value) {
      return new TestQuestionBuilder(questionId, templateId, points, value, matchingPairs);
    }

    TestQuestionBuilder withMatchingPairs(List<MatchingPair> value) {
      return new TestQuestionBuilder(questionId, templateId, points, blankAnswers, value);
    }

    ExamQuestion build() {
      return new ExamQuestion(
          questionId,
          "draft-1",
          templateId,
          "topic",
          3,
          "apply",
          "stem",
          List.of(),
          matchingPairs,
          List.of(),
          List.of(),
          List.of(),
          blankAnswers,
          null,
          "HTTP,404",
          "secret explanation for server only",
          "server source reference",
          points,
          "secret distractor rationale");
    }
  }
}
