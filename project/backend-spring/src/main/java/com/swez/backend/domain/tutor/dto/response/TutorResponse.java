/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 튜터 단건 응답 DTO */
@Builder
@Schema(description = "튜터 응답")
public record TutorResponse(
    @Schema(description = "튜터 ID") String tutorId,
    @Schema(description = "튜터 이름") String name,
    @Schema(description = "짧은 소개 문구") String tagline,
    @Schema(description = "프로필 이미지 URL") String profileImageUrl,
    @Schema(description = "음성 클론 샘플 URL") String voiceSampleUrl,
    @Schema(description = "기본 제공 여부") boolean isDefault,
    @Schema(description = "말투 슬라이더 값") short toneSlider,
    @Schema(description = "설명 속도 슬라이더 값") short speedSlider,
    @Schema(description = "설명 깊이 슬라이더 값") short depthSlider,
    @Schema(description = "질문 방식 슬라이더 값") short questionStyleSlider,
    @Schema(description = "이모지 사용 여부") boolean useEmoji,
    @Schema(description = "존댓말 사용 여부") boolean useFormalSpeech,
    @Schema(description = "생성 일시") LocalDateTime createdAt,
    @Schema(description = "수정 일시") LocalDateTime updatedAt) {}
