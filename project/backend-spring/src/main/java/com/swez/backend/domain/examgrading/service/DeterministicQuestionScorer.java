/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.examgrading.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.GradingMode;
import com.swez.backend.domain.examgrading.model.MatchingPair;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.QuestionOption;
import com.swez.backend.domain.examgrading.model.RubricCriterion;
import com.swez.backend.domain.examgrading.support.AnswerNormalizer;
import com.swez.backend.domain.examgrading.support.QuestionTemplatePolicy;

@Component
public class DeterministicQuestionScorer {

  private final AnswerNormalizer normalizer = new AnswerNormalizer();
  private final QuestionTemplatePolicy policy = new QuestionTemplatePolicy();

  // 오답 시 학생을 해설 섹션으로 유도하는 공통 문구
  private static final String REVIEW_HINT = "아래 해설에서 이유를 확인해 보세요.";

  public QuestionGrade score(ExamQuestion question, JsonNode answer) {
    String templateId = question.templateId();
    if (policy.isChoice(templateId)) {
      return scoreChoice(question, answer);
    }
    if (policy.isTrueFalse(templateId)) {
      return scoreTrueFalse(question, answer);
    }
    if (policy.isBlank(templateId)) {
      return scoreBlank(question, answer);
    }
    if (policy.isOrdering(templateId)) {
      return scoreOrdering(question, answer);
    }
    if (policy.isMatching(templateId)) {
      return scoreMatching(question, answer);
    }
    return scoreExact(question, answer);
  }

  public QuestionGrade unanswered(ExamQuestion question) {
    // 미제출도 '답안 미제출' 라벨 대신 정답을 공개하는 실질 피드백을 제공한다.
    String answerText =
        question.options() != null && !question.options().isEmpty()
            ? correctOptionText(question)
            : answerDisplay(question.correctAnswer());
    String clause = answerText.isEmpty() ? "" : " 정답은 '" + answerText + "' 입니다.";
    String feedback = ("답안을 제출하지 않았어요." + clause + " " + REVIEW_HINT).strip();
    return result(question, BigDecimal.ZERO, feedback, List.of());
  }

  public QuestionGrade scoreExact(ExamQuestion question, JsonNode answer) {
    String candidate = normalizer.compactText(normalizer.scalarText(answer));
    boolean correct = normalizer.acceptedVariants(question.correctAnswer()).contains(candidate);
    BigDecimal score = correct ? points(question) : BigDecimal.ZERO;
    String reason = correct ? "허용 답안과 일치" : "허용 답안과 불일치";
    String feedback = objectiveFeedback(score, points(question), answerDisplay(question.correctAnswer()));
    return result(
        question, score, feedback, List.of(criterion("정답 일치", score, points(question), reason)));
  }

  private QuestionGrade scoreChoice(ExamQuestion question, JsonNode answer) {
    String candidate = normalizer.compactText(normalizer.scalarText(answer));
    List<String> accepted = new ArrayList<>(normalizer.acceptedVariants(question.correctAnswer()));
    if (question.options() != null) {
      for (QuestionOption option : question.options()) {
        if (option.correct()) {
          accepted.add(normalizer.compactText(option.label()));
          accepted.add(normalizer.compactText(option.text()));
        }
      }
    }
    boolean correct = accepted.contains(candidate);
    BigDecimal score = correct ? points(question) : BigDecimal.ZERO;
    String reason = correct ? "정답 보기 선택" : "오답 보기 선택";
    String feedback = objectiveFeedback(score, points(question), correctOptionText(question));
    return result(
        question, score, feedback, List.of(criterion("보기 선택", score, points(question), reason)));
  }

  private QuestionGrade scoreTrueFalse(ExamQuestion question, JsonNode answer) {
    String candidate = boolToken(normalizer.scalarText(answer));
    boolean correct =
        normalizer.acceptedVariants(question.correctAnswer()).stream()
            .map(this::boolToken)
            .anyMatch(candidate::equals);
    BigDecimal score = correct ? points(question) : BigDecimal.ZERO;
    String reason = correct ? "참거짓 판정 일치" : "참거짓 판정 불일치";
    // '참거짓 판정 일치' 라벨 대신 사람이 읽는 참(O)/거짓(X)로 정답을 보여준다.
    String correctToken =
        normalizer.acceptedVariants(question.correctAnswer()).stream()
            .map(this::boolToken)
            .filter(t -> t.equals("true") || t.equals("false"))
            .findFirst()
            .orElse("");
    String correctLabel =
        correctToken.equals("true")
            ? "참(O)"
            : correctToken.equals("false") ? "거짓(X)" : answerDisplay(question.correctAnswer());
    String feedback = objectiveFeedback(score, points(question), correctLabel);
    return result(
        question, score, feedback, List.of(criterion("참거짓", score, points(question), reason)));
  }

  private QuestionGrade scoreBlank(ExamQuestion question, JsonNode answer) {
    List<String> expected =
        question.blankAnswers() == null || question.blankAnswers().isEmpty()
            ? normalizer.splitSequenceText(question.correctAnswer())
            : question.blankAnswers();
    if (expected.isEmpty()) {
      return scoreExact(question, answer);
    }
    return scoreSequence(question, normalizer.sequenceValues(answer), expected, "빈칸", "정답", ", ");
  }

  private QuestionGrade scoreOrdering(ExamQuestion question, JsonNode answer) {
    List<String> expected =
        question.correctOrdering() == null || question.correctOrdering().isEmpty()
            ? normalizer.splitSequenceText(question.correctAnswer())
            : question.correctOrdering();
    if (expected.isEmpty()) {
      return scoreExact(question, answer);
    }
    return scoreSequence(
        question, normalizer.sequenceValues(answer), expected, "순서", "정답 순서", " → ");
  }

  private QuestionGrade scoreMatching(ExamQuestion question, JsonNode answer) {
    List<MatchingPair> expected =
        question.matchingPairs() == null ? List.of() : question.matchingPairs();
    if (expected.isEmpty()) {
      return scoreExact(question, answer);
    }
    Map<String, String> submitted = normalizer.mappingValues(answer);
    BigDecimal unit =
        points(question).divide(BigDecimal.valueOf(expected.size()), 8, RoundingMode.HALF_UP);
    BigDecimal total = BigDecimal.ZERO;
    int correctCount = 0;
    List<RubricCriterion> criteria = new ArrayList<>();
    for (MatchingPair pair : expected) {
      String candidate =
          normalizer.compactText(submitted.getOrDefault(normalizer.normalizeText(pair.left()), ""));
      boolean correct = candidate.equals(normalizer.compactText(pair.right()));
      BigDecimal itemScore = correct ? unit : BigDecimal.ZERO;
      total = total.add(itemScore);
      if (correct) {
        correctCount++;
      }
      criteria.add(criterion(pair.left(), itemScore, unit, correct ? "연결 일치" : "연결 불일치"));
    }
    String pairsJoin =
        expected.stream()
            .map(p -> answerDisplay(p.left(), 24) + "↔" + answerDisplay(p.right(), 24))
            .collect(Collectors.joining(", "));
    String feedback = sequenceFeedback(correctCount, expected.size(), "정답 연결", pairsJoin);
    return result(question, total, feedback, criteria);
  }

  private QuestionGrade scoreSequence(
      ExamQuestion question,
      List<String> submitted,
      List<String> expected,
      String label,
      String answerNoun,
      String joinSep) {
    BigDecimal unit =
        points(question).divide(BigDecimal.valueOf(expected.size()), 8, RoundingMode.HALF_UP);
    BigDecimal total = BigDecimal.ZERO;
    int correctCount = 0;
    List<RubricCriterion> criteria = new ArrayList<>();
    for (int index = 0; index < expected.size(); index++) {
      boolean correct =
          index < submitted.size()
              && normalizer
                  .acceptedVariants(expected.get(index))
                  .contains(normalizer.compactText(submitted.get(index)));
      BigDecimal itemScore = correct ? unit : BigDecimal.ZERO;
      total = total.add(itemScore);
      if (correct) {
        correctCount++;
      }
      criteria.add(criterion(label + " " + (index + 1), itemScore, unit, correct ? "일치" : "불일치"));
    }
    String answersJoin =
        expected.stream().map(s -> answerDisplay(s, 40)).collect(Collectors.joining(joinSep));
    String feedback = sequenceFeedback(correctCount, expected.size(), answerNoun, answersJoin);
    return result(question, total, feedback, criteria);
  }

  /** 부분점수 문항(빈칸·순서·연결)의 맞힌 개수와 정답 목록을 담은 실질 피드백을 만든다. */
  private String sequenceFeedback(int correctCount, int total, String answerNoun, String answersJoin) {
    if (correctCount >= total) {
      return "모두 맞혔어요! " + answerNoun + ": " + answersJoin;
    }
    if (correctCount > 0) {
      return total
          + "개 중 "
          + correctCount
          + "개를 맞혔어요. "
          + answerNoun
          + ": "
          + answersJoin
          + " "
          + REVIEW_HINT;
    }
    return "오답이에요. " + answerNoun + ": " + answersJoin + " " + REVIEW_HINT;
  }

  /**
   * 객관 문항용 실질 피드백을 만든다. 정/오/부분 판정, 정답, 오답 시 안내를 담는다.
   *
   * <p>짧은 라벨('참거짓 판정 일치' 등) 대신 학생이 바로 이해할 수 있는 한두 문장 피드백을 제공한다. GradingLeakGuard는
   * feedback 텍스트를 콘텐츠 스캔 대상에서 제외하므로(자연어 평가 텍스트로 간주) 정답 노출이 차단되지 않으며, GRADED 복습
   * 경로에서는 정답이 reviewAnswer로 이미 공개된다.
   */
  private String objectiveFeedback(BigDecimal score, BigDecimal maxScore, String answerText) {
    String answer = answerText == null ? "" : answerText.strip();
    String clause = answer.isEmpty() ? "" : " 정답은 '" + answer + "' 입니다.";
    if (score.compareTo(maxScore) >= 0) {
      return ("정확히 맞혔어요!" + clause).strip();
    }
    if (score.compareTo(BigDecimal.ZERO) > 0) {
      return ("부분 정답이에요." + clause + " " + REVIEW_HINT).strip();
    }
    return ("오답이에요." + clause + " " + REVIEW_HINT).strip();
  }

  /** 정답 표시 문자열을 정리하고 너무 길면 잘라 피드백에 끼워 넣는다. */
  private String answerDisplay(String text) {
    return answerDisplay(text, 80);
  }

  private String answerDisplay(String text, int limit) {
    String snippet = text == null ? "" : text.strip();
    if (snippet.length() <= limit) {
      return snippet;
    }
    return snippet.substring(0, limit).stripTrailing() + "…";
  }

  /** 정답 보기의 번호·문장을 표시용 문자열로 만든다(없으면 correctAnswer 폴백). */
  private String correctOptionText(ExamQuestion question) {
    if (question.options() != null) {
      for (QuestionOption option : question.options()) {
        if (option.correct()) {
          String text = answerDisplay(option.text());
          String label = option.label() == null ? "" : option.label().strip();
          if (!text.isEmpty() && !label.isEmpty()) {
            return label + ". " + text;
          }
          return text.isEmpty() ? label : text;
        }
      }
    }
    return answerDisplay(question.correctAnswer());
  }

  private QuestionGrade result(
      ExamQuestion question, BigDecimal score, String feedback, List<RubricCriterion> criteria) {
    BigDecimal maxScore = points(question);
    BigDecimal finalScore =
        score.max(BigDecimal.ZERO).min(maxScore).setScale(4, RoundingMode.HALF_UP);
    return new QuestionGrade(
        question.questionId(),
        question.templateId(),
        finalScore,
        maxScore.setScale(4, RoundingMode.HALF_UP),
        finalScore.compareTo(maxScore) >= 0,
        GradingMode.DETERMINISTIC,
        feedback,
        criteria,
        1.0,
        false);
  }

  private RubricCriterion criterion(
      String name, BigDecimal score, BigDecimal maxScore, String reason) {
    return new RubricCriterion(
        name,
        score.setScale(4, RoundingMode.HALF_UP),
        maxScore.setScale(4, RoundingMode.HALF_UP),
        reason);
  }

  private BigDecimal points(ExamQuestion question) {
    return question.pointValue().max(new BigDecimal("0.01"));
  }

  private String boolToken(String value) {
    String normalized = normalizer.compactText(value);
    if (List.of("o", "true", "t", "yes", "y", "참", "맞음", "옳음").contains(normalized)) {
      return "true";
    }
    if (List.of("x", "false", "f", "no", "n", "거짓", "틀림", "그름").contains(normalized)) {
      return "false";
    }
    return normalized;
  }
}
