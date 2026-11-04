/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 모의고사 문항 응답 DTO. 정답(correctOption)은 넣지 않는다. */
@Builder
@Schema(description = "모의고사 문항")
public record MockExamQuestionResponse(
    @Schema(description = "문항 ID") String id,
    @Schema(description = "문항 순서") int questionIdx,
    @Schema(description = "문제 텍스트") String questionText,
    @Schema(description = "문항 유형") String questionType,
    @Schema(description = "선택지 (JSON 배열 문자열)") String options,
    @Schema(description = "배점") int points) {}
