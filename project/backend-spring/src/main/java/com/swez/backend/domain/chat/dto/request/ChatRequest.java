/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

/** 프론트엔드가 Spring으로 보내는 AI 채팅 요청 DTO */
public record ChatRequest(
    /** URL의 lessonId. 내부적으로 Chapter 엔티티의 chapterId에 대응한다. */
    String lessonId,
    String slideId,
    Integer slideIdx,
    @NotBlank(message = "메시지는 비어 있을 수 없습니다.") String message,
    String selectedText,
    BigDecimal playbackSec) {}
