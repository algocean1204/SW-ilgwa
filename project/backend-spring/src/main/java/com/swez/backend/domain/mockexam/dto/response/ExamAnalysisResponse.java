/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * FastAPI 약점 진단(총평) 분석 응답 DTO (POST {fastapiBaseUrl}/api/mock-exams/analyze 응답 바디)
 *
 * <p>{@code { "analysis": "<한국어 총평 문자열>" }} 형태다. FastAPI 가 필드를 추가해도 역직렬화가 깨지지 않게
 * 미지정 속성은 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExamAnalysisResponse(String analysis) {}
