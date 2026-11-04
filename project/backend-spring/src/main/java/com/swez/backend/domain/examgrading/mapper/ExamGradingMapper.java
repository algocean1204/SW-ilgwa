/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.mapper;

import org.springframework.stereotype.Component;

import com.swez.backend.domain.examgrading.dto.request.SubmittedAnswerRequest;
import com.swez.backend.domain.examgrading.dto.response.ExamSubmissionGradeResponse;
import com.swez.backend.domain.examgrading.dto.response.QuestionGradeResponse;
import com.swez.backend.domain.examgrading.dto.response.RubricCriterionResponse;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.RubricCriterion;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;

@Component
public class ExamGradingMapper {

  public SubmittedAnswer toDomain(SubmittedAnswerRequest request) {
    return new SubmittedAnswer(request.questionId(), request.answer());
  }

  public ExamSubmissionGradeResponse toResponse(ExamGradeResult result) {
    return toResponse(result, null, null);
  }

  /**
   * 채점 결과 + AI 총평을 함께 담은 응답 DTO를 생성한다.
   *
   * @param analysis AI 총평 본문. 분석 실패 시 null
   * @param weakTopics AI 진단 약점 주제 목록. 분석 실패 시 null
   */
  public ExamSubmissionGradeResponse toResponse(
      ExamGradeResult result, String analysis, java.util.List<String> weakTopics) {
    return ExamSubmissionGradeResponse.builder()
        .attemptId(result.attemptId())
        .examId(result.examId())
        .totalScore(result.totalScore())
        .maxScore(result.maxScore())
        .percentage(result.percentage())
        .passed(result.passed())
        .gradingStatus(result.gradingStatus())
        .gradedAt(result.gradedAt())
        .results(result.results().stream().map(this::toResponse).toList())
        .analysis(analysis)
        .weakTopics(weakTopics)
        .build();
  }

  private QuestionGradeResponse toResponse(QuestionGrade grade) {
    return QuestionGradeResponse.builder()
        .questionId(grade.questionId())
        .templateId(grade.templateId())
        .score(grade.score())
        .maxScore(grade.maxScore())
        .correct(grade.correct())
        .gradingMode(grade.gradingMode())
        .feedback(grade.feedback())
        .rubricBreakdown(grade.rubricBreakdown().stream().map(this::toResponse).toList())
        .confidence(grade.confidence())
        .needsManualReview(grade.needsManualReview())
        .build();
  }

  private RubricCriterionResponse toResponse(RubricCriterion criterion) {
    return RubricCriterionResponse.builder()
        .criterion(criterion.criterion())
        .score(criterion.score())
        .maxScore(criterion.maxScore())
        .reason(criterion.reason())
        .build();
  }
}
