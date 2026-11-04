/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** 해당 과외의 채팅 메시지 목록을 담는 히스토리 응답 DTO */
public record ChatHistoryResponse(List<ChatHistoryItem> messages) {

  /** 개별 채팅 메시지 항목 */
  public record ChatHistoryItem(
      String id,
      String role,
      String message,
      List<String> citations,
      /** chapterId를 프론트 관점의 lessonId로 반환 */
      String lessonId,
      Integer slideIdx,
      LocalDateTime createdAt) {}
}
