/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** AI 채점 결과 콜백 수신 확인 응답 DTO */
@Builder
@Schema(description = "채점 결과 수신 확인")
public record AiResultAckResponse(
    @Schema(description = "수신 확인", example = "true") boolean ack,
    @Schema(description = "다음 액션", example = "notify_user") String nextAction) {}
