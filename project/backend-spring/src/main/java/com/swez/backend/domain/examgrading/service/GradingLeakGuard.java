/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.MatchingPair;
import com.swez.backend.domain.examgrading.model.QuestionOption;
import com.swez.backend.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공개 응답에 민감한 채점 정보가 새지 않는지 검증한다.
 *
 * <h3>두 단계 차단</h3>
 *
 * <ol>
 *   <li><b>필드명 검사(assertBlockedFieldNames)</b>: 응답 JSON에 원시 정답 키 필드 (correctAnswer, explanation,
 *       answerKeySeal 등)가 JSON 키로 있으면 차단한다. DTO 구조에서 새는 경우를 막는다.
 *   <li><b>콘텐츠 스캔(assertHiddenValueAbsent)</b>: 스냅샷의 숨겨진 값이 공개 텍스트에 그대로 포함되는지 검사. 단, AI 루브릭
 *       피드백(feedback·reason·criterion)은 이 스캔 대상에서 제외한다.
 * </ol>
 *
 * <h3>피드백 텍스트를 콘텐츠 스캔에서 제외하는 이유</h3>
 *
 * <p>서술형(ko_descriptive) 루브릭에서 FastAPI가 반환하는 feedback·reason·criterion은 "핵심 개념을 올바르게 서술했습니다"와 같은
 * 자연어 평가 문장이다. 정답 원문을 복사하지 않고 개념을 평가하므로, 원시 정답키와 substring이 우연히 겹쳐도 누수가
 * 아니다. 콘텐츠 스캔을 적용하면 정상 피드백이 오탐으로 차단된다.
 *
 * <p>객관식·매칭·정렬 문항의 정답 레이블/옵션 text는 is_correct 필드를 뺀 SafeOptionResponse로 이미 막고,
 * 필드명 검사(단계 1)가 한 번 더 확인한다. 그래서 피드백 텍스트를 콘텐츠 스캔에서 빼도 누수 차단 범위는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradingLeakGuard {

  /** 응답 JSON에 있으면 안 되는 민감 필드 키. DTO에서 이미 빼야 하고, 직렬화 결과에 나오면 차단한다. */
  private static final List<String> BLOCKED_FIELD_NAMES =
      List.of(
          "correctAnswer",
          "correct_answer",
          "explanation",
          "sourceReference",
          "source_reference",
          "answerKeySeal",
          "answer_key_seal",
          "answersHtml",
          "answers_html");

  /**
   * 콘텐츠 스캔(hiddenValue 포함 여부 검사)을 적용할 텍스트 필드.
   *
   * <p>feedback·reason·criterion은 AI 루브릭이 생성하는 자연어 평가 텍스트이므로 콘텐츠 스캔 대상에서 의도적으로 제외한다. message만 포함한다.
   * (위 클래스 주석의 "피드백 텍스트를 콘텐츠 스캔에서 제외하는 이유" 참조)
   */
  private static final Set<String> CONTENT_SCAN_FIELDS = Set.of("message");

  /**
   * 짧은 숨겨진 값(길이 < 3)이 답 문맥과 함께 노출될 때 차단하기 위한 문맥 토큰. 피드백 텍스트는 콘텐츠 스캔 대상에서 제외하므로, 이 토큰들은 message 필드에만
   * 적용된다.
   */
  private static final Set<String> REVEAL_CONTEXT_TOKENS =
      Set.of(
          "답",
          "답:",
          "답안",
          "정답",
          "답은",
          "모범답안",
          "기준답안",
          "해설",
          "선택",
          "선택지",
          "보기",
          "골라",
          "고르",
          "맞",
          "옳",
          "correct",
          "correctanswer",
          "correct answer",
          "answer",
          "answer is",
          "selected",
          "choice",
          "option",
          "should be");

  private final ObjectMapper objectMapper;

  /** 스냅샷 없이 필드명 검사만 한다. 결과 조회 경로에서 사용한다. */
  public void assertPublicResponseSafe(Object response) {
    assertPublicResponseSafe(response, null);
  }

  /**
   * 공개 응답에 민감 정보가 없는지 검증한다.
   *
   * <ol>
   *   <li>필드명 검사: 응답 JSON에 차단 필드 키가 없는지 확인.
   *   <li>콘텐츠 스캔(snapshot 제공 시): CONTENT_SCAN_FIELDS에 해당하는 필드 값에 스냅샷의 숨겨진 원시 값이 포함되는지 확인.
   *       feedback·reason·criterion은 이 스캔 대상에 포함되지 않는다.
   * </ol>
   */
  public void assertPublicResponseSafe(Object response, ExamAttemptSnapshot snapshot) {
    String serialized = serialize(response);
    JsonNode root = parse(serialized);

    // 1단계(DTO 구조 검사): 원시 정답 키 필드가 JSON 키로 있으면 차단한다
    assertBlockedFieldNames(root);

    if (snapshot == null || snapshot.questions() == null) {
      return;
    }

    // 2단계(콘텐츠 스캔): CONTENT_SCAN_FIELDS(message만)에 숨겨진 원시 값이 있는지 확인한다.
    // feedback·reason·criterion은 AI 루브릭 자연어 평가 텍스트라 스캔하지 않는다.
    List<String> contentScanTexts = contentScanTextValues(root);
    hiddenValues(snapshot).forEach(value -> assertHiddenValueAbsent(contentScanTexts, value));
  }

  private String serialize(Object response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException e) {
      log.error("[GradingLeakGuard] response serialization failed", e);
      throw new CustomException(ExamForgeGradingErrorCode.SENSITIVE_RESPONSE_BLOCKED);
    }
  }

  private JsonNode parse(String serialized) {
    try {
      return objectMapper.readTree(serialized);
    } catch (JsonProcessingException e) {
      log.error("[GradingLeakGuard] response parse failed", e);
      throw new CustomException(ExamForgeGradingErrorCode.SENSITIVE_RESPONSE_BLOCKED);
    }
  }

  /** JSON 트리를 재귀 순회하며 차단 필드명이 존재하면 예외를 던진다. */
  private void assertBlockedFieldNames(JsonNode node) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (BLOCKED_FIELD_NAMES.contains(field.getKey())) {
          log.error("[GradingLeakGuard] 차단된 민감 필드명이 공개 응답에 포함됨: {}", field.getKey());
          throw new CustomException(ExamForgeGradingErrorCode.SENSITIVE_RESPONSE_BLOCKED);
        }
        assertBlockedFieldNames(field.getValue());
      }
      return;
    }
    if (node.isArray()) {
      node.forEach(this::assertBlockedFieldNames);
    }
  }

  /**
   * 콘텐츠 스캔 대상 텍스트 값만 수집한다. CONTENT_SCAN_FIELDS(message)에 해당하는 필드의 값만 반환한다.
   * feedback·reason·criterion은 AI 루브릭 평가 텍스트이므로 포함하지 않는다.
   */
  private List<String> contentScanTextValues(JsonNode node) {
    List<String> result = new ArrayList<>();
    collectContentScanTextValues(node, "", result);
    return result;
  }

  private void collectContentScanTextValues(JsonNode node, String fieldName, List<String> result) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isTextual() && CONTENT_SCAN_FIELDS.contains(fieldName)) {
      result.add(node.asText(""));
      return;
    }
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        collectContentScanTextValues(field.getValue(), field.getKey(), result);
      }
      return;
    }
    if (node.isArray()) {
      node.forEach(item -> collectContentScanTextValues(item, fieldName, result));
    }
  }

  /**
   * 스냅샷에서 공개 응답에 절대 노출되면 안 되는 원시 값 목록을 수집한다.
   *
   * <ul>
   *   <li>correctAnswer, explanation, sourceReference, distractorRationale: 서버 전용 필드
   *   <li>correctOrdering, blankAnswers: 정렬·빈칸 정답 배열
   *   <li>matchingPairs.right: 매칭 문항의 정답 쌍
   *   <li>options.label/text (correct=true): 객관식 정답 옵션 레이블·텍스트
   * </ul>
   */
  private List<String> hiddenValues(ExamAttemptSnapshot snapshot) {
    List<String> result = new ArrayList<>();
    for (ExamQuestion question : snapshot.questions()) {
      addHiddenValue(result, question.correctAnswer());
      addHiddenValue(result, question.explanation());
      addHiddenValue(result, question.sourceReference());
      addHiddenValue(result, question.distractorRationale());
      addHiddenValues(result, question.correctOrdering());
      addHiddenValues(result, question.blankAnswers());
      if (question.matchingPairs() != null) {
        for (MatchingPair pair : question.matchingPairs()) {
          addHiddenValue(result, pair.right());
        }
      }
      if (question.options() != null) {
        for (QuestionOption option : question.options()) {
          if (option.correct()) {
            addHiddenValue(result, option.label());
            addHiddenValue(result, option.text());
          }
        }
      }
    }
    return result;
  }

  private void addHiddenValues(List<String> result, List<String> values) {
    if (values == null) {
      return;
    }
    values.forEach(value -> addHiddenValue(result, value));
  }

  private void addHiddenValue(List<String> result, String value) {
    String compactHidden = compact(value);
    if (compactHidden.isBlank()) {
      return;
    }
    result.add(value);
  }

  /**
   * 콘텐츠 스캔 대상 텍스트에 숨겨진 원시 값이 포함되는지 검사한다. 이 메서드는 CONTENT_SCAN_FIELDS(message)에 해당하는 텍스트에만 적용된다.
   * feedback·reason·criterion 텍스트는 이미 수집 단계에서 제외하므로 여기에 도달하지 않는다.
   */
  private void assertHiddenValueAbsent(List<String> contentScanTexts, String hiddenValue) {
    String compactHidden = compact(hiddenValue);
    if (compactHidden.isBlank()) {
      return;
    }
    for (String scanText : contentScanTexts) {
      String compactScanText = compact(scanText);
      if (compactScanText.contains(compactHidden)
          && shouldBlockHiddenValue(compactHidden, compactScanText)) {
        log.error("[GradingLeakGuard] 공개 응답에 숨겨진 채점 원시 값이 포함됨 — 차단");
        throw new CustomException(ExamForgeGradingErrorCode.SENSITIVE_RESPONSE_BLOCKED);
      }
    }
  }

  /**
   * 숨겨진 값 차단 여부를 결정한다.
   *
   * <ul>
   *   <li>길이 3 이상: 원시 정답 문자열이 그대로 들어간 것이라 차단.
   *   <li>길이 3 미만(예: 보기 레이블 "B"): 답 문맥 토큰과 함께 등장해야 차단. 단독으로 등장하면 정상 텍스트의 일부일 수 있으므로 허용.
   * </ul>
   */
  private boolean shouldBlockHiddenValue(String compactHidden, String compactScanText) {
    if (compactHidden.length() >= 3) {
      return true;
    }
    return REVEAL_CONTEXT_TOKENS.stream().map(this::compact).anyMatch(compactScanText::contains);
  }

  private String compact(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("\\s+", "").toLowerCase();
  }
}
