/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** 과외 수정 요청 DTO (PUT /tutoring/{courseId}) */
@Schema(description = "과외 수정 요청")
public record UpdateTutoringRequest(
    @Schema(description = "과외명") String courseName,
    @Schema(description = "과목") String subject,
    @Schema(description = "시험 날짜") LocalDate examDate) {}
