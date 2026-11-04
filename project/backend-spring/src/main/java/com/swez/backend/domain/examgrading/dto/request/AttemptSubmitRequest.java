/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 응시 제출 요청 DTO.
 *
 * <p>응시자가 입력한 답안 목록을 담는다. attemptId는 경로 변수로 받으므로 여기에는 포함하지 않는다. submittedAnswers가 빈 리스트이면 미응답 전체
 * 제출로 간주한다.
 */
@Schema(description = "응시 제출 요청 DTO — 답안 목록")
public record AttemptSubmitRequest(
    @Valid @NotNull @Size(max = 100) @Schema(description = "응시자가 제출한 답안 목록 (빈 리스트 = 전체 미응답)")
        List<SubmittedAnswerRequest> submittedAnswers) {}
