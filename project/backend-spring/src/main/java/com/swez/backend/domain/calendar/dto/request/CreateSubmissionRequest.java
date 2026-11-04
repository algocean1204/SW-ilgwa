/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** 제출물 생성 요청 DTO. FastAPI → Spring 으로 POST /internal/submissions 를 호출할 때 쓴다. */
@Schema(description = "제출물 생성 요청 (내부 전용)")
public record CreateSubmissionRequest(
    @NotBlank @Schema(description = "과제 ID") String assignmentId,
    @NotBlank @Schema(description = "사용자 ID") String userId,
    @Schema(description = "제출 파일 URL") String officialFileUrl,
    @Schema(description = "재제출 여부", defaultValue = "false") Boolean isRetry) {}
