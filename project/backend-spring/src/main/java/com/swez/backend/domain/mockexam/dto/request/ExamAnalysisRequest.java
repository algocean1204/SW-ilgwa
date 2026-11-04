/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.dto.request;

import java.util.List;

import lombok.Builder;

/**
 * FastAPI 약점 진단(총평) 분석 요청 DTO (POST {fastapiBaseUrl}/api/mock-exams/analyze)
 *
 * <p>채점 직후 문항별 결과를 담아 FastAPI에 동기 전송한다. FastAPI와 합의된 고정 계약이므로 필드명을 임의로 변경하지 않는다.
 */
@Builder
public record ExamAnalysisRequest(
    String examId,
    String subject,
    int score,
    int totalPoints,
    String grade,
    List<ResultItem> results) {

  /** 문항별 채점 결과 항목. options 는 question 엔티티의 원문 문자열을 그대로 전달한다. */
  @Builder
  public record ResultItem(
      int questionIdx,
      String questionText,
      boolean correct,
      String options,
      String selectedOption,
      String correctOption,
      String explanation) {}
}
