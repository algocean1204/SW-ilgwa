/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.dto.response;

import java.util.List;

/** Spring이 프론트엔드에 돌려주는 AI 채팅 응답 DTO */
public record ChatResponse(
    /** AI가 생성한 응답 텍스트 */
    String responseText,
    /** AI 응답의 인용 목록 */
    List<String> citations,
    /** 저장된 ChatMessage ID (AI 응답 메시지) */
    String sessionId) {}
