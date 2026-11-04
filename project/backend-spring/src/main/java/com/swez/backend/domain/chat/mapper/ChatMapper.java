/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.chat.dto.response.ChatHistoryResponse;
import com.swez.backend.domain.chat.dto.response.ChatHistoryResponse.ChatHistoryItem;
import com.swez.backend.domain.chat.dto.response.ChatResponse;
import com.swez.backend.domain.chat.entity.ChatMessage;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 채팅 도메인 엔티티와 DTO를 서로 변환한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMapper {

  private final ObjectMapper objectMapper;

  /**
   * 사용자 메시지를 ChatMessage 엔티티로 변환한다 (role=user).
   *
   * @param chapter 챕터 (nullable)
   * @param slideId 슬라이드 ID (nullable)
   * @param slideIdx 슬라이드 인덱스 (nullable)
   * @param playbackSec 재생 시점 (nullable)
   * @return 저장 전 ChatMessage 엔티티
   */
  public ChatMessage toUserMessage(
      User user,
      Course course,
      Chapter chapter,
      String slideId,
      Integer slideIdx,
      String message,
      BigDecimal playbackSec) {
    return ChatMessage.builder()
        .id(IdGenerator.chatMessage())
        .user(user)
        .course(course)
        .chapter(chapter)
        .slideId(slideId)
        .slideIdx(slideIdx)
        .role("user")
        .message(message)
        .playbackSec(playbackSec)
        .build();
  }

  /**
   * AI 응답을 ChatMessage 엔티티로 변환한다 (role=assistant).
   *
   * @param chapter 챕터 (nullable)
   * @param slideId 슬라이드 ID (nullable)
   * @param slideIdx 슬라이드 인덱스 (nullable)
   * @param citations 인용 목록 (nullable)
   * @param playbackSec 재생 시점 (nullable)
   * @return 저장 전 ChatMessage 엔티티
   */
  public ChatMessage toAssistantMessage(
      User user,
      Course course,
      Chapter chapter,
      String slideId,
      Integer slideIdx,
      String responseText,
      List<String> citations,
      BigDecimal playbackSec) {
    String citationsJson =
        citations != null && !citations.isEmpty() ? toCitationsJson(citations) : null;
    return ChatMessage.builder()
        .id(IdGenerator.chatMessage())
        .user(user)
        .course(course)
        .chapter(chapter)
        .slideId(slideId)
        .slideIdx(slideIdx)
        .role("assistant")
        .message(responseText)
        .citations(citationsJson)
        .playbackSec(playbackSec)
        .build();
  }

  /** ChatMessage 엔티티 목록을 ChatHistoryResponse로 변환한다. */
  public ChatHistoryResponse toHistoryResponse(List<ChatMessage> messages) {
    List<ChatHistoryItem> items =
        messages.stream()
            .map(
                msg ->
                    new ChatHistoryItem(
                        msg.getId(),
                        msg.getRole(),
                        msg.getMessage(),
                        parseCitations(msg.getCitations()),
                        msg.getChapter() != null ? msg.getChapter().getId() : null,
                        msg.getSlideIdx(),
                        msg.getCreatedAt()))
            .toList();
    return new ChatHistoryResponse(items);
  }

  /**
   * AI 응답 데이터를 ChatResponse DTO로 변환한다.
   *
   * @param sessionId 저장된 AI 응답 메시지 ID
   */
  public ChatResponse toChatResponse(
      String responseText, List<String> citations, String sessionId) {
    return new ChatResponse(responseText, citations, sessionId);
  }

  // 내부 헬퍼

  /** 인용 목록을 JSON 배열 문자열로 변환한다. 이스케이프는 ObjectMapper가 처리한다. */
  private String toCitationsJson(List<String> citations) {
    try {
      return objectMapper.writeValueAsString(citations);
    } catch (JsonProcessingException e) {
      log.error(
          "[ChatMapper] citations JSON 직렬화 실패 — citations: {}, 원인: {}", citations, e.getMessage());
      return "[]";
    }
  }

  /** JSON 배열 문자열을 ObjectMapper로 역직렬화해 인용 목록으로 만든다. */
  private List<String> parseCitations(String citationsJson) {
    if (citationsJson == null || citationsJson.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(citationsJson, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException e) {
      log.error(
          "[ChatMapper] citations JSON 파싱 실패 — raw: '{}', 원인: {}", citationsJson, e.getMessage());
      return List.of();
    }
  }
}
