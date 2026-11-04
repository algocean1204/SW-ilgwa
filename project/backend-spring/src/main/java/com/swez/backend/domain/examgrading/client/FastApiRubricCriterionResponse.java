/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiRubricCriterionResponse(
    String criterion,
    BigDecimal score,
    @JsonProperty("max_score") BigDecimal maxScore,
    String reason) {}
