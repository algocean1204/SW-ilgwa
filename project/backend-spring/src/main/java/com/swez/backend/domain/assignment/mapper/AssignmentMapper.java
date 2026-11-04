/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.mapper;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.assignment.dto.response.AssignmentDetailResponse;
import com.swez.backend.domain.assignment.dto.response.AssignmentDetailResponse.QuestionItem;
import com.swez.backend.domain.assignment.dto.response.AssignmentListResponse;
import com.swez.backend.domain.assignment.dto.response.GradeResponse;
import com.swez.backend.domain.assignment.dto.response.SubmissionResponse;
import com.swez.backend.domain.calendar.entity.Assignment;
import com.swez.backend.domain.calendar.entity.Submission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 과제 도메인 엔티티를 DTO로 변환한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentMapper {

  private final ObjectMapper objectMapper;

  /** Assignment 엔티티를 목록 응답으로 변환한다. */
  public AssignmentListResponse toListResponse(Assignment assignment) {
    return AssignmentListResponse.builder()
        .id(assignment.getId())
        .title(assignment.getTitle())
        .status(assignment.getStatus())
        .dueDate(assignment.getDueDate())
        .totalQuestions(assignment.getTotalQuestions())
        .maxScore(assignment.getMaxScore())
        .createdAt(assignment.getCreatedAt())
        .build();
  }

  /**
   * Assignment 엔티티를 상세 응답으로 변환한다. questions JSON을 파싱하고 이전 제출 답안을 넣는다.
   *
   * @param previousAnswer 이전 제출 답안 텍스트 (없으면 null)
   */
  public AssignmentDetailResponse toDetailResponse(Assignment assignment, String previousAnswer) {
    return AssignmentDetailResponse.builder()
        .id(assignment.getId())
        .title(assignment.getTitle())
        .description(assignment.getDescription())
        .status(assignment.getStatus())
        .dueDate(assignment.getDueDate())
        .totalQuestions(assignment.getTotalQuestions())
        .maxScore(assignment.getMaxScore())
        .questions(parseQuestions(assignment.getQuestions()))
        .previousAnswer(previousAnswer)
        .createdAt(assignment.getCreatedAt())
        .build();
  }

  /** Submission 엔티티를 제출 응답으로 변환한다. */
  public SubmissionResponse toSubmissionResponse(Submission submission) {
    return SubmissionResponse.builder()
        .submissionId(submission.getId())
        .assignmentId(submission.getAssignment().getId())
        .status(submission.getStatus())
        .submittedAt(submission.getSubmittedAt())
        .build();
  }

  /** Submission 엔티티를 채점 결과 응답으로 변환한다. */
  public GradeResponse toGradeResponse(Submission submission, Integer maxScore) {
    return GradeResponse.builder()
        .submissionId(submission.getId())
        .assignmentId(submission.getAssignment().getId())
        .score(submission.getOfficialScore())
        .maxScore(maxScore)
        .feedback(submission.getOfficialFeedback())
        .aiConfidence(submission.getOfficialAiConfidence())
        .ocrFailedPages(submission.getOcrFailedPages())
        .totalPages(submission.getTotalPages())
        .gradedAt(submission.getGradedAt())
        .submittedAt(submission.getSubmittedAt())
        .build();
  }

  // 내부 헬퍼

  /** JSON 문자열을 QuestionItem 목록으로 파싱한다. 파싱 실패 시 빈 목록을 반환한다. */
  private List<QuestionItem> parseQuestions(String questionsJson) {
    if (questionsJson == null || questionsJson.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return objectMapper.readValue(questionsJson, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.warn("[AssignmentMapper] questions JSON 파싱 실패: {}", e.getMessage());
      return Collections.emptyList();
    }
  }
}
