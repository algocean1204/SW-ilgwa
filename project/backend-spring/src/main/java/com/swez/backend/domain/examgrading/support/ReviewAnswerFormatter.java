/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.examgrading.support;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.MatchingPair;
import com.swez.backend.domain.examgrading.model.QuestionOption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 복습 모드 응답에 쓸 답을 사람이 읽을 수 있는 문자열로 포맷한다.
 *
 * <p>exam_submitted_answer.answer_json과 exam_attempt_question의 정답 정보를
 * 문항 유형별 규칙에 따라 사람이 읽을 수 있는 문자열로 변환한다.
 *
 * <ul>
 *   <li>객관식(ko_multiple_choice_*): 제출 레이블에 해당하는 보기 텍스트 반환
 *   <li>OX(ko_true_false): "O" 또는 "X"
 *   <li>단답(ko_short_answer): 제출된 텍스트 그대로
 *   <li>빈칸(ko_fill_blank): 쉼표 구분 나열
 *   <li>순서(ko_ordering): 화살표 구분 나열 (예: "A → C → B")
 *   <li>연결(ko_matching): "좌항-우항" 쌍 쉼표 구분 (예: "A-1, B-2")
 *   <li>서술(ko_descriptive): 제출 텍스트 그대로
 * </ul>
 *
 * <p>이 모듈은 복습 결과 경로(GRADED 상태 한정)에서만 호출한다. 응시 중 경로에서 호출되어서는 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAnswerFormatter {

  private final ObjectMapper objectMapper;
  // QuestionTemplatePolicy는 Spring 빈이 아니므로 직접 생성한다 (DeterministicQuestionScorer와 동일한 패턴)
  private final QuestionTemplatePolicy templatePolicy = new QuestionTemplatePolicy();

  /**
   * 제출 답안 JSON 문자열을 사람이 읽을 수 있는 문자열로 포맷한다.
   *
   * @param answerJson exam_submitted_answer.answer_json (nullable)
   * @param question 문항 스냅샷 (templateId·options 참조)
   * @return 포맷된 제출 답안 문자열, 미응답이면 "(미응답)"
   */
  public String formatMyAnswer(String answerJson, ExamQuestion question) {
    if (answerJson == null || answerJson.isBlank()) {
      return "(미응답)";
    }
    try {
      JsonNode node = objectMapper.readTree(answerJson);
      return formatByTemplate(node, question, false);
    } catch (Exception e) {
      log.warn(
          "[ReviewAnswerFormatter] 제출 답안 파싱 실패 | questionId: {}, error: {}",
          question.questionId(),
          e.getMessage());
      return answerJson;
    }
  }

  /** 정답(exam_attempt_question.correct_answer + 관련 필드)을 사람이 읽을 수 있는 문자열로 포맷한다. */
  public String formatCorrectAnswer(ExamQuestion question) {
    try {
      return buildCorrectAnswerText(question);
    } catch (Exception e) {
      log.warn(
          "[ReviewAnswerFormatter] 정답 포맷 실패 | questionId: {}, error: {}",
          question.questionId(),
          e.getMessage());
      return question.correctAnswer() != null ? question.correctAnswer() : "(정보 없음)";
    }
  }

  // 내부: 제출 답안 유형별 포맷

  /** 문항 유형에 따라 제출 답안을 포맷한다. */
  private String formatByTemplate(JsonNode node, ExamQuestion question, boolean isCorrect) {
    String templateId = question.templateId();
    if (templateId == null) {
      // templateId 없으면 raw 텍스트 추출
      return rawText(node);
    }

    if (templatePolicy.isChoice(templateId)) {
      // 객관식: {"label": "B"} → 보기 레이블에 해당하는 보기 텍스트
      return formatChoiceAnswer(node, question.options());
    }
    if (templatePolicy.isTrueFalse(templateId)) {
      // OX: {"label": "true"/"false"} 또는 "true"/"false" → "O" 또는 "X"
      return formatTrueFalse(node);
    }
    if (templatePolicy.isShortAnswer(templateId) || templatePolicy.isRubric(templateId)) {
      // 단답·서술: {"text": "..."} → 텍스트 그대로
      return rawText(node);
    }
    if (templatePolicy.isBlank(templateId)) {
      // 빈칸: {"blanks": ["답1", "답2"]} → "답1, 답2"
      return formatBlanks(node);
    }
    if (templatePolicy.isOrdering(templateId)) {
      // 순서: {"order": ["A", "C", "B"]} → "A → C → B"
      return formatOrdering(node);
    }
    if (templatePolicy.isMatching(templateId)) {
      // 연결: {"pairs": [{"left": "A", "right": "1"}]} → "A-1, B-2"
      return formatMatching(node);
    }
    // 알 수 없는 유형은 raw 텍스트를 추출한다
    return rawText(node);
  }

  /** 객관식 보기 레이블을 보기 텍스트로 치환한다. */
  private String formatChoiceAnswer(JsonNode node, List<QuestionOption> options) {
    // {"label": "B"} 또는 "B" 형태
    String label = null;
    if (node.isObject()) {
      JsonNode labelNode = node.get("label");
      if (labelNode != null) {
        label = labelNode.asText("").trim();
      }
    } else if (node.isTextual()) {
      label = node.asText("").trim();
    }

    if (label == null || label.isBlank()) {
      return "(미응답)";
    }

    // options 목록에서 레이블 일치 보기 텍스트 조회
    if (options != null) {
      final String finalLabel = label;
      return options.stream()
          .filter(opt -> finalLabel.equalsIgnoreCase(opt.label()))
          .map(opt -> opt.label() + ". " + opt.text())
          .findFirst()
          .orElse(label); // 보기 목록에서 못 찾으면 레이블 그대로
    }
    return label;
  }

  /** OX 답안을 "O" 또는 "X"로 변환한다. */
  private String formatTrueFalse(JsonNode node) {
    String raw = rawText(node).toLowerCase().trim();
    if ("true".equals(raw) || "o".equals(raw) || "yes".equals(raw)) {
      return "O";
    }
    if ("false".equals(raw) || "x".equals(raw) || "no".equals(raw)) {
      return "X";
    }
    return raw.isBlank() ? "(미응답)" : raw;
  }

  /** 빈칸 답안을 쉼표 구분으로 포맷한다. */
  private String formatBlanks(JsonNode node) {
    if (node.isObject()) {
      JsonNode blanks = node.get("blanks");
      if (blanks != null && blanks.isArray()) {
        return joinArray(blanks, ", ");
      }
    }
    if (node.isArray()) {
      return joinArray(node, ", ");
    }
    return rawText(node);
  }

  /** 순서 답안을 "A → C → B" 형태로 포맷한다. */
  private String formatOrdering(JsonNode node) {
    if (node.isObject()) {
      JsonNode order = node.get("order");
      if (order != null && order.isArray()) {
        return joinArray(order, " → ");
      }
    }
    if (node.isArray()) {
      return joinArray(node, " → ");
    }
    return rawText(node);
  }

  /** 연결(매칭) 답안을 "A-1, B-2" 형태로 포맷한다. */
  private String formatMatching(JsonNode node) {
    if (node.isObject()) {
      JsonNode pairs = node.get("pairs");
      if (pairs != null && pairs.isArray()) {
        return buildMatchingText(pairs);
      }
    }
    if (node.isArray()) {
      return buildMatchingText(node);
    }
    return rawText(node);
  }

  /** 매칭 배열을 "좌-우, 좌-우" 형태로 변환한다. */
  private String buildMatchingText(JsonNode pairsNode) {
    StringBuilder sb = new StringBuilder();
    pairsNode.forEach(
        pair -> {
          if (pair.isObject()) {
            String left = getFieldText(pair, "left", "from", "key");
            String right = getFieldText(pair, "right", "to", "value");
            if (!left.isBlank()) {
              if (sb.length() > 0) sb.append(", ");
              sb.append(left).append("-").append(right.isBlank() ? "?" : right);
            }
          }
        });
    return sb.length() > 0 ? sb.toString() : "(미응답)";
  }

  // 내부: 정답 포맷

  /** 문항 유형에 따라 정답 필드를 포맷한다. */
  private String buildCorrectAnswerText(ExamQuestion question) {
    String templateId = question.templateId();
    if (templateId == null) {
      return question.correctAnswer() != null ? question.correctAnswer() : "(정보 없음)";
    }

    if (templatePolicy.isChoice(templateId)) {
      // 객관식: correctAnswer가 정답 레이블 ("B" 등) → 보기 텍스트로 치환
      String label = question.correctAnswer();
      if (label == null || label.isBlank()) return "(정보 없음)";
      List<QuestionOption> options = question.options();
      if (options != null) {
        return options.stream()
            .filter(opt -> label.equalsIgnoreCase(opt.label()))
            .map(opt -> opt.label() + ". " + opt.text())
            .findFirst()
            .orElse(label);
      }
      return label;
    }

    if (templatePolicy.isTrueFalse(templateId)) {
      // OX: correct_answer가 "true"/"false" → "O"/"X"
      String raw = question.correctAnswer();
      if (raw == null) return "(정보 없음)";
      return "true".equalsIgnoreCase(raw.trim()) ? "O" : "X";
    }

    if (templatePolicy.isBlank(templateId)) {
      // 빈칸: blankAnswers 배열을 쉼표 구분
      List<String> blanks = question.blankAnswers();
      if (blanks != null && !blanks.isEmpty()) {
        return String.join(", ", blanks);
      }
      return question.correctAnswer() != null ? question.correctAnswer() : "(정보 없음)";
    }

    if (templatePolicy.isOrdering(templateId)) {
      // 순서: correctOrdering 배열을 화살표 구분
      List<String> ordering = question.correctOrdering();
      if (ordering != null && !ordering.isEmpty()) {
        return String.join(" → ", ordering);
      }
      return question.correctAnswer() != null ? question.correctAnswer() : "(정보 없음)";
    }

    if (templatePolicy.isMatching(templateId)) {
      // 연결: matchingPairs에서 left-right 쌍 구성
      List<MatchingPair> pairs = question.matchingPairs();
      if (pairs != null && !pairs.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        pairs.forEach(
            p -> {
              if (sb.length() > 0) sb.append(", ");
              sb.append(p.left()).append("-").append(p.right());
            });
        return sb.toString();
      }
      return question.correctAnswer() != null ? question.correctAnswer() : "(정보 없음)";
    }

    // 단답·서술·기타: correctAnswer 원문 그대로
    String answer = question.correctAnswer();
    return answer != null ? answer : "(정보 없음)";
  }

  // 유틸리티

  /** JsonNode 배열을 구분자로 연결한 문자열로 변환한다. */
  private String joinArray(JsonNode array, String delimiter) {
    StringBuilder sb = new StringBuilder();
    array.forEach(
        item -> {
          if (sb.length() > 0) sb.append(delimiter);
          sb.append(item.asText("").trim());
        });
    return sb.length() > 0 ? sb.toString() : "(미응답)";
  }

  /** JsonNode에서 텍스트를 추출한다. 객체이면 text/value/answer 키를 순서대로 시도한다. */
  private String rawText(JsonNode node) {
    if (node == null || node.isNull()) return "(미응답)";
    if (node.isTextual()) return node.asText("").trim();
    if (node.isObject()) {
      for (String key : List.of("text", "value", "answer", "label")) {
        JsonNode child = node.get(key);
        if (child != null && child.isTextual()) {
          return child.asText("").trim();
        }
      }
      return node.toString();
    }
    return node.asText("").trim();
  }

  /** 객체에서 후보 키 목록 중 첫 번째로 존재하는 텍스트를 반환한다. */
  private String getFieldText(JsonNode obj, String... keys) {
    for (String key : keys) {
      JsonNode node = obj.get(key);
      if (node != null) {
        return node.asText("").trim();
      }
    }
    return "";
  }
}
