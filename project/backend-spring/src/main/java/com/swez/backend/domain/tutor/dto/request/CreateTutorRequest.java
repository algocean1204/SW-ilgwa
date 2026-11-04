/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** 튜터 생성 요청 DTO */
@Schema(description = "튜터 생성 요청")
public record CreateTutorRequest(
    @NotBlank @Size(max = 20) @Schema(description = "튜터 이름 (최대 20자)", example = "나만의 튜터")
        String name,
    @Size(max = 100) @Schema(description = "짧은 소개 문구 (최대 100자)") String tagline,
    @Schema(description = "프로필 이미지 URL (nullable, S3 미설정 시 null)") String profileImageUrl,
    @Min(0) @Max(100) @Schema(description = "말투 슬라이더 값 (0~100, 기본값 50)") Integer toneSlider,
    @Min(0) @Max(100) @Schema(description = "설명 속도 슬라이더 값 (0~100, 기본값 50)") Integer speedSlider,
    @Min(0) @Max(100) @Schema(description = "설명 깊이 슬라이더 값 (0~100, 기본값 50)") Integer depthSlider,
    @Min(0) @Max(100) @Schema(description = "질문 방식 슬라이더 값 (0~100, 기본값 50)")
        Integer questionStyleSlider,
    @Schema(description = "이모지 사용 여부 (기본값 false)") Boolean useEmoji,
    @Schema(description = "존댓말 사용 여부 (기본값 true)") Boolean useFormalSpeech) {}
