/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 텔레그램 자동 전송 옵션 응답 DTO.
 *
 * <p>user_settings 테이블의 telegram_note_enabled / telegram_quiz_enabled / telegram_friend_enabled 컬럼을
 * 그대로 반영한다. 텔레그램 연동 여부는 별도 /telegram/status 엔드포인트로 조회하며, 이 DTO는 옵션 값만 담는다.
 */
@Builder
@Schema(description = "텔레그램 자동 전송 옵션 응답")
public record TelegramPreferencesResponse(
    @Schema(description = "암기노트 전송 활성화") boolean noteEnabled,
    @Schema(description = "퀴즈 알림 활성화") boolean quizEnabled,
    @Schema(description = "친구 공유 수신 활성화") boolean friendEnabled) {}
