/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.request;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 응시자용 모의고사 생성 시작 요청 DTO (POST /api/exam-forge/attempts/generate).
 *
 * <p>현재는 JWT 인증만 요구하고 coin·mockexam 연동 소유권 검증은 하지 않는다.
 */
@Schema(description = "모의고사 문항 생성 요청")
public record ExamGenerateRequest(
    @NotBlank @Schema(description = "과외 ID (course_id)", example = "crs_01J...") String courseId,
    @NotBlank @Schema(description = "과목명", example = "운영체제") String subject,
    @Schema(description = "세부 주제 (선택)", example = "프로세스 스케줄링") String topic,
    @Min(1) @Max(50) @NotNull @Schema(description = "생성 문항 수 (1~50, FastAPI가 최소 20으로 클램프)", example = "20")
        Integer questionCount,
    @Schema(
            description = "문항 유형 목록 (FastAPI 템플릿 ID) — null/빈 목록이면 FastAPI가 유형 자동 분배(최종모의고사)",
            example = "[\"ko_multiple_choice_5\",\"ko_short_answer\"]")
        List<String> questionTypes,
    @Schema(description = "난이도 (easy/medium/hard, 기본값 medium)", example = "medium")
        String difficulty,
    @Min(0) @Max(100) @Schema(description = "합격 기준 점수 비율 (0~100, 기본값 60)", example = "60")
        BigDecimal passPercentage,
    @Schema(description = "연결할 모의고사 카드 ID (nullable)", example = "mex_01J...") String mockExamId) {}
