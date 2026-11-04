/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.calendar.entity.Assignment;
import com.swez.backend.domain.calendar.entity.Submission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 텍스트 제출물에 대해 FastAPI AssignmentGrader 채점 요청을 비동기로 트리거한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentGradingTriggerService {

  /** FastAPI 전용 RestTemplate. X-API-Key 헤더가 자동으로 붙는다. */
  @Qualifier("fastapiRestTemplate") private final RestTemplate fastapiRestTemplate;

  private final ObjectMapper objectMapper;

  @Value("${app.fastapi.base-url}")
  private String fastapiBaseUrl;

  /** Spring → FastAPI 내부 인증 토큰. FastAPI AssignmentGrader 의 X-Internal-Token 헤더에 쓴다. */
  @Value("${app.internal.token:}")
  private String internalToken;

  /**
   * 텍스트 제출물에 대해 FastAPI 채점을 비동기로 요청한다. 웹훅 처리 스레드를 블록하지 않도록 @Async로 분리한다.
   *
   * @param submission 채점할 제출물. status=queued 상태여야 한다
   * @param assignment 채점 기준을 꺼낼 과제
   * @param answerText 텔레그램에서 수신한 텍스트 답안
   */
  @Async
  public void triggerTextGrading(Submission submission, Assignment assignment, String answerText) {

    String submissionId = submission.getId();

    try {
      List<String> questionList = extractQuestions(assignment.getQuestions());

      Map<String, Object> requestBody =
          Map.of(
              "submission_id", submissionId,
              "assignment_title", assignment.getTitle() != null ? assignment.getTitle() : "제출 과제",
              "assignment_description",
                  assignment.getDescription() != null ? assignment.getDescription() : "",
              "assignment_questions", questionList,
              "answer_text", answerText);

      /* FastAPI AssignmentGrader 인증용 X-Internal-Token 헤더를 직접 붙인다.
       * fastapiRestTemplate 인터셉터의 X-API-Key 와 함께 보내 두 인증 방식을 모두 지원한다.
       */
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      if (StringUtils.hasText(internalToken)) {
        headers.set("X-Internal-Token", internalToken);
      }
      HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);
      String url = fastapiBaseUrl + "/api/assignment-grader/v1/grade";
      fastapiRestTemplate.postForObject(url, httpEntity, Map.class);

      log.info(
          "[GradingTrigger] FastAPI 채점 요청 성공 | submissionId: {}, assignmentId: {}",
          submissionId,
          assignment.getId());

    } catch (RestClientException e) {
      /* 채점 요청이 실패하면 submission 을 queued 상태로 남기고 운영 모니터링에서 처리한다. */
      log.error(
          "[GradingTrigger] FastAPI 채점 요청 실패 | submissionId: {}, error: {}",
          submissionId,
          e.getMessage());
    }
  }

  /** 과제 questions JSON 문자열을 문자열 목록으로 파싱한다. 파싱 실패 시 빈 리스트를 반환해 채점을 계속 진행한다. */
  private List<String> extractQuestions(String questionsJson) {
    if (questionsJson == null || questionsJson.isBlank()) {
      return List.of();
    }
    try {
      List<Object> raw = objectMapper.readValue(questionsJson, new TypeReference<>() {});
      return raw.stream()
          .map(
              item -> {
                if (item instanceof String s) {
                  return s;
                }
                /* 객체 형태면 question / text 키를 우선 추출한다 */
                if (item instanceof Map<?, ?> map) {
                  Object q = map.get("question");
                  if (q instanceof String sq && !sq.isBlank()) return sq;
                  Object t = map.get("text");
                  if (t instanceof String st && !st.isBlank()) return st;
                }
                return item.toString();
              })
          .filter(s -> !s.isBlank())
          .toList();
    } catch (JsonProcessingException e) {
      log.warn("[GradingTrigger] questions 파싱 실패 — 빈 목록 사용: {}", e.getMessage());
      return List.of();
    }
  }
}
