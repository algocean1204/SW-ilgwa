/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** 튜터 수정 요청 DTO. 부분 수정이라 모든 필드가 nullable 이다. */
@Schema(description = "튜터 수정 요청 (null 필드는 기존 값 유지)")
public record UpdateTutorRequest(
    @Size(max = 20) @Schema(description = "튜터 이름 (최대 20자)") String name,
    @Size(max = 100) @Schema(description = "짧은 소개 문구 (최대 100자)") String tagline,
    @Schema(description = "프로필 이미지 URL (nullable)") String profileImageUrl,
    @Min(0) @Max(100) @Schema(description = "말투 슬라이더 값 (0~100)") Integer toneSlider,
    @Min(0) @Max(100) @Schema(description = "설명 속도 슬라이더 값 (0~100)") Integer speedSlider,
    @Min(0) @Max(100) @Schema(description = "설명 깊이 슬라이더 값 (0~100)") Integer depthSlider,
    @Min(0) @Max(100) @Schema(description = "질문 방식 슬라이더 값 (0~100)") Integer questionStyleSlider,
    @Schema(description = "이모지 사용 여부") Boolean useEmoji,
    @Schema(description = "존댓말 사용 여부") Boolean useFormalSpeech) {}
