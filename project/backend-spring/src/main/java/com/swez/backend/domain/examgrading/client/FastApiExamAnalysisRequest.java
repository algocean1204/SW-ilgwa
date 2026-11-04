/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.examgrading.client;

import java.util.List;

import lombok.Builder;

/**
 * FastAPI AI 총평(약점 진단) 분석 요청 DTO (POST /api/mock-exams/analyze).
 *
 * <p>mockexam 레거시 경로의 {@code ExamAnalysisRequest}와 동일한 FastAPI 계약을 따르되, examgrading 도메인이
 * mockexam DTO를 import하지 않도록(도메인 격리) 클라이언트 전용 record를 분리한다.
 *
 * <p>비객관식(단답·서술·빈칸 등) 문항은 보기 인덱스가 없으므로 {@code selectedText}/{@code correctText}에
 * 사람이 읽는 답 텍스트를 직접 담는다. FastAPI는 텍스트 필드가 있으면 인덱스 복원보다 우선 사용한다.
 */
@Builder
public record FastApiExamAnalysisRequest(
    String examId,
    String subject,
    int score,
    int totalPoints,
    String grade,
    List<ResultItem> results) {

  /** 문항별 채점 결과 항목. FastAPI MockExamResultItem 계약과 1:1로 맞춘다. */
  @Builder
  public record ResultItem(
      int questionIdx,
      String questionText,
      boolean correct,
      String options,
      String selectedOption,
      String correctOption,
      String selectedText,
      String correctText,
      String explanation) {}
}
