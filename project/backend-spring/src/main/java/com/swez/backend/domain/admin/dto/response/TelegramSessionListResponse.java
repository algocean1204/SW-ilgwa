/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 텔레그램 관련 세션 목록 응답")
public record TelegramSessionListResponse(List<SessionItem> sessions) {

  public record SessionItem(
      String id, String sessionType, LocalDateTime startedAt, String status, String summary) {}
}
