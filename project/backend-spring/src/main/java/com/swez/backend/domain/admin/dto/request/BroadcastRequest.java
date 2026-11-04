/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.dto.request;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** 텔레그램 브로드캐스트 발송 요청 */
@Schema(description = "텔레그램 브로드캐스트 발송 요청")
public record BroadcastRequest(
    @Schema(description = "발송할 메시지 (최대 4096자)", example = "내일 과제 마감입니다!")
        @NotBlank(message = "발송할 메시지가 비어있습니다.")
        @Size(max = 4096, message = "메시지는 최대 4096자까지 입력할 수 있습니다.")
        String message,
    @Schema(description = "발송 대상 사용자 ID 목록. null이면 전체 텔레그램 연동 사용자에게 발송", nullable = true) @Nullable List<String> targetUserIds) {}
