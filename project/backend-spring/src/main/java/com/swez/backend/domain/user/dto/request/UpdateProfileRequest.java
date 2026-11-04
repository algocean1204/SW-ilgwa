/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.dto.request;

import java.util.List;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** 프로필 수정 요청 DTO. null 필드는 기존 값을 유지한다 (부분 수정). */
@Schema(description = "프로필 수정 요청 (null 필드는 변경 없음)")
public record UpdateProfileRequest(
    @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
        @Schema(description = "닉네임 (2~20자)", example = "수학마스터")
        String nickname,
    @Schema(description = "프로필 이미지 URL") String profileImageUrl,
    @Schema(description = "학년", example = "고등학교 3학년") String grade,
    @Schema(description = "전공 (대학생)") String major,
    @Schema(description = "학교명") String education,
    @Schema(description = "학습 목표", example = "수능 대비") String learningGoal,
    @Schema(description = "관심 과목 배열", example = "[\"수학\", \"물리\"]") List<String> interests,
    @Schema(description = "선호 말투", example = "격려형") String preferredTone) {}
