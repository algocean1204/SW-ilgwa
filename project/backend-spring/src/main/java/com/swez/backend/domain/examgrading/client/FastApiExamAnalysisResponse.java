/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.examgrading.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * FastAPI AI 총평 분석 응답 DTO: {@code {"analysis": "...", "weakTopics": ["..."]}}.
 *
 * <p>weakTopics는 mock_exam_submission.analysis JSON으로 복제되어 ChapterStudio 약점 집계가 읽는다.
 * FastAPI가 필드를 추가해도 역직렬화가 깨지지 않도록 미지정 속성은 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FastApiExamAnalysisResponse(String analysis, List<String> weakTopics) {}
