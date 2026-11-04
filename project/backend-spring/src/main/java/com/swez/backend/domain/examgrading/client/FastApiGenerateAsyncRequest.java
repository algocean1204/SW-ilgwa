/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.client;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FastAPI 비동기 문항 생성 트리거 요청 페이로드.
 *
 * <p>POST {fastapi}/api/exam-forge/mock/generate-async 엔드포인트로 전송한다. 공유 계약 필드명은 snake_case JSON 직렬화를
 * 따른다.
 */
public record FastApiGenerateAsyncRequest(
    @JsonProperty("attempt_id") String attemptId,
    @JsonProperty("course_id") String courseId,
    @JsonProperty("subject") String subject,
    @JsonProperty("topic") String topic,
    @JsonProperty("question_count") Integer questionCount,
    @JsonProperty("question_types") List<String> questionTypes,
    @JsonProperty("difficulty") String difficulty,
    @JsonProperty("pass_percentage") BigDecimal passPercentage) {}
