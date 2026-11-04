/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import io.swagger.v3.oas.annotations.media.Schema;

/** 온보딩 완료 요청 DTO */
@Schema(description = "온보딩 완료 요청")
public record OnboardingRequest(
    @NotEmpty @Schema(description = "관심 과목 배열 (최소 1개)", example = "[\"수학\", \"과학\"]")
        List<String> interests,
    @NotBlank @Schema(description = "학년", example = "고등학교 2학년") String grade,
    @NotBlank @Schema(description = "학습 목표", example = "수능 대비") String learningGoal,
    @NotBlank @Schema(description = "선호 말투 (존댓말/반말/격려형/도전형)", example = "격려형") String preferredTone,
    @Schema(description = "일일 학습 목표 시간(분), 생략 시 30분", example = "60")
        Integer dailyStudyGoalMinutes) {}
