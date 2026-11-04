/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.swez.backend.domain.mockexam.dto.response.ExamResultResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamDetailResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamListResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamQuestionResponse;
import com.swez.backend.domain.mockexam.entity.MockExam;
import com.swez.backend.domain.mockexam.entity.MockExamQuestion;
import com.swez.backend.domain.mockexam.entity.MockExamSubmission;

/** 모의고사 도메인 엔티티 ↔ DTO 변환 */
@Component
public class MockExamMapper {

  /** MockExam 엔티티를 MockExamListResponse 로 변환한다. */
  public MockExamListResponse toListResponse(MockExam exam) {
    // attemptId 는 cutover 경로에서 따로 넣고, 레거시 경로에서는 null 이다
    return MockExamListResponse.builder()
        .id(exam.getId())
        .title(exam.getTitle())
        .examType(exam.getExamType())
        .questionCount(exam.getQuestionCount())
        .difficulty(exam.getDifficulty())
        .timeLimit(exam.getTimeLimit())
        .status(exam.getStatus())
        .totalPoints(exam.getTotalPoints())
        .createdAt(exam.getCreatedAt())
        .attemptId(null)
        .build();
  }

  /**
   * MockExam 엔티티와 attemptId를 MockExamListResponse 로 변환한다.
   *
   * <p>cutover=true 경로에서 ExamAttemptGenerationService가 발급한 attemptId를 함께 반환할 때 사용한다. 프론트는
   * attemptId가 null이 아니면 examgrading 경로(detail·submit·result)를 사용해야 한다.
   *
   * @param attemptId ExamAttempt 식별자 (cutover 경로에서만 non-null)
   */
  public MockExamListResponse toListResponseWithAttempt(MockExam exam, String attemptId) {
    return toListResponseWithAttempt(exam, attemptId, null);
  }

  /**
   * MockExam 엔티티와 attemptId, 실패 사유를 MockExamListResponse 로 변환한다.
   *
   * <p>failureReason 은 최신 attempt 가 FAILED 일 때 attempt.failureReason 을 그대로 전달한다. 프론트는 이 값이 non-null
   * 이면 mock_exam.status 가 GENERATING 이어도 '생성 실패'로 표시하고 폴링을 멈춰, '출제 중'에 계속 머물지 않는다.
   *
   * @param attemptId ExamAttempt 식별자 (cutover 경로에서만 non-null)
   * @param failureReason 생성 실패 사유 코드 (FAILED attempt 일 때만 non-null)
   */
  public MockExamListResponse toListResponseWithAttempt(
      MockExam exam, String attemptId, String failureReason) {
    return MockExamListResponse.builder()
        .id(exam.getId())
        .title(exam.getTitle())
        .examType(exam.getExamType())
        .questionCount(exam.getQuestionCount())
        .difficulty(exam.getDifficulty())
        .timeLimit(exam.getTimeLimit())
        .status(exam.getStatus())
        .totalPoints(exam.getTotalPoints())
        .createdAt(exam.getCreatedAt())
        .attemptId(attemptId)
        .generationFailureReason(failureReason)
        .build();
  }

  /** MockExamQuestion 엔티티를 MockExamQuestionResponse 로 변환한다. 정답(correctOption)은 포함하지 않는다. */
  public MockExamQuestionResponse toQuestionResponse(MockExamQuestion question) {
    return MockExamQuestionResponse.builder()
        .id(question.getId())
        .questionIdx(question.getQuestionIdx())
        .questionText(question.getQuestionText())
        .questionType(question.getQuestionType())
        .options(question.getOptions())
        .points(question.getPoints())
        .build();
  }

  /** MockExam + 문항 목록을 MockExamDetailResponse 로 변환한다. */
  public MockExamDetailResponse toDetailResponse(MockExam exam, List<MockExamQuestion> questions) {
    List<MockExamQuestionResponse> questionResponses =
        questions.stream().map(this::toQuestionResponse).toList();

    return MockExamDetailResponse.builder()
        .id(exam.getId())
        .title(exam.getTitle())
        .examType(exam.getExamType())
        .questionCount(exam.getQuestionCount())
        .difficulty(exam.getDifficulty())
        .timeLimit(exam.getTimeLimit())
        .status(exam.getStatus())
        .totalPoints(exam.getTotalPoints())
        .createdAt(exam.getCreatedAt())
        .questions(questionResponses)
        .build();
  }

  /** 제출 결과를 ExamResultResponse 로 변환한다. */
  public ExamResultResponse toResultResponse(
      MockExam exam,
      MockExamSubmission submission,
      List<ExamResultResponse.QuestionResultItem> questionResults) {
    return ExamResultResponse.builder()
        .examId(exam.getId())
        .title(exam.getTitle())
        .score(submission.getScore())
        .totalPoints(submission.getTotalPoints())
        .grade(submission.getGrade())
        .timeTaken(submission.getTimeTaken())
        .submittedAt(submission.getSubmittedAt())
        .analysis(submission.getAnalysis())
        .questionResults(questionResults)
        .build();
  }
}
