/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 텔레그램 연동 토큰 발급 응답 DTO.
 *
 * <p>expiresAt 은 UTC offset(+00:00)을 포함한 OffsetDateTime 으로 직렬화된다. LocalDateTime 을 쓰면 Jackson 이
 * timezone 없이 직렬화하고, 브라우저가 이를 KST 로컬 시각으로 읽어 9시간 이른 시각으로 보고 "즉시 만료" 오류를 낸다.
 */
@Builder
@Schema(description = "텔레그램 연동 토큰 응답")
public record TelegramLinkResponse(
    @Schema(description = "텔레그램 연동용 일회성 토큰 (Redis TTL 5분)") String linkToken,
    @Schema(description = "텔레그램 봇 딥링크 URL") String telegramBotUrl,
    @Schema(description = "토큰 만료 시각 (now + 5분, UTC ISO-8601 offset 포함)")
        OffsetDateTime expiresAt) {}
