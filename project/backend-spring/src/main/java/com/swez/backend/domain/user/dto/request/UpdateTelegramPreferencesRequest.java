/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 텔레그램 자동 전송 옵션 업데이트 요청 DTO. null 필드는 기존 값을 유지한다 (부분 병합). */
@Schema(description = "텔레그램 자동 전송 옵션 업데이트 요청 (null 필드는 변경 없음)")
public record UpdateTelegramPreferencesRequest(
    @Schema(description = "암기노트 전송", example = "true") Boolean noteEnabled,
    @Schema(description = "퀴즈 알림", example = "true") Boolean quizEnabled,
    @Schema(description = "친구 공유 수신", example = "false") Boolean friendEnabled) {}
