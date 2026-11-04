/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.client;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiQuestionGradeResponse(
    @JsonProperty("question_id") String questionId,
    @JsonProperty("template_id") String templateId,
    BigDecimal score,
    @JsonProperty("max_score") BigDecimal maxScore,
    @JsonProperty("is_correct") boolean correct,
    @JsonProperty("grading_mode") String gradingMode,
    String feedback,
    @JsonProperty("rubric_breakdown") List<FastApiRubricCriterionResponse> rubricBreakdown,
    double confidence,
    @JsonProperty("needs_manual_review") boolean needsManualReview) {}
