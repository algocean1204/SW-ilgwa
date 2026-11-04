/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.model;

import java.math.BigDecimal;
import java.util.List;

public record QuestionGrade(
    String questionId,
    String templateId,
    BigDecimal score,
    BigDecimal maxScore,
    boolean correct,
    GradingMode gradingMode,
    String feedback,
    List<RubricCriterion> rubricBreakdown,
    double confidence,
    boolean needsManualReview) {}
