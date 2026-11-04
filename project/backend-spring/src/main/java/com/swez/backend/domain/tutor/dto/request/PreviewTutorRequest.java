/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** 튜터 미리보기 요청 DTO. FastAPI 프리뷰 생성을 호출할 때 쓴다. */
@Schema(description = "튜터 미리보기 요청")
public record PreviewTutorRequest(
    @Min(0) @Max(100) @Schema(description = "말투 슬라이더 값 (0~100)") Integer toneSlider,
    @Min(0) @Max(100) @Schema(description = "설명 속도 슬라이더 값 (0~100)") Integer speedSlider,
    @Min(0) @Max(100) @Schema(description = "설명 깊이 슬라이더 값 (0~100)") Integer depthSlider,
    @Min(0) @Max(100) @Schema(description = "질문 방식 슬라이더 값 (0~100)") Integer questionStyleSlider,
    @Schema(description = "이모지 사용 여부") Boolean useEmoji,
    @Schema(description = "존댓말 사용 여부") Boolean useFormalSpeech,
    @NotBlank @Schema(description = "미리보기에 사용할 샘플 질문", example = "미분이 뭐예요?")
        String sampleQuestion) {}
