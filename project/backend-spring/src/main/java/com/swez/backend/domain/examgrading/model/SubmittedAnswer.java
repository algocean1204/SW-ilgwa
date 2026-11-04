/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record SubmittedAnswer(@JsonProperty("question_id") String questionId, JsonNode answer) {}
