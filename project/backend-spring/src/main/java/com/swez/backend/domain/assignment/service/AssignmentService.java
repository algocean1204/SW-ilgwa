/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.assignment.dto.request.SubmitAnswerRequest;
import com.swez.backend.domain.assignment.dto.response.AssignmentDetailResponse;
import com.swez.backend.domain.assignment.dto.response.AssignmentListResponse;
import com.swez.backend.domain.assignment.dto.response.GradeResponse;
import com.swez.backend.domain.assignment.dto.response.SubmissionResponse;
import com.swez.backend.domain.assignment.exception.AssignmentErrorCode;
import com.swez.backend.domain.assignment.mapper.AssignmentMapper;
import com.swez.backend.domain.calendar.entity.Assignment;
import com.swez.backend.domain.calendar.entity.Submission;
import com.swez.backend.domain.calendar.repository.AssignmentRepository;
import com.swez.backend.domain.calendar.repository.SubmissionRepository;
import com.swez.backend.domain.calendar.service.AssignmentGradingTriggerService;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.exception.CourseErrorCode;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 과제 목록·상세 조회, 제출, 채점 결과 조회를 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

  private final CourseRepository courseRepository;
  private final AssignmentRepository assignmentRepository;
  private final SubmissionRepository submissionRepository;
  private final AssignmentMapper assignmentMapper;
  private final ObjectMapper objectMapper;
  private final AssignmentGradingTriggerService gradingTriggerService;

  /**
   * 과제 목록을 조회한다. 상태 필터를 지원하며, 마감일 오름차순으로 정렬한다.
   *
   * @param status 과제 상태 필터 (null이면 전체)
   */
  @Transactional(readOnly = true)
  public List<AssignmentListResponse> getAssignments(
      String userId, String courseId, String status) {
    log.debug(
        "[AssignmentService] getAssignments() - START | userId: {}, courseId: {}, status: {}",
        userId,
        courseId,
        status);

    findCourseByIdAndUser(courseId, userId);

    List<Assignment> assignments =
        assignmentRepository.findByCourseIdAndOptionalStatus(courseId, status);

    List<AssignmentListResponse> result =
        assignments.stream().map(assignmentMapper::toListResponse).toList();

    log.debug("[AssignmentService] getAssignments() - END | count: {}", result.size());
    return result;
  }

  /** 과제 상세 정보를 조회한다. 문제 목록과 이전 제출 답안을 포함한다. */
  @Transactional(readOnly = true)
  public AssignmentDetailResponse getAssignment(
      String userId, String courseId, String assignmentId) {
    log.debug(
        "[AssignmentService] getAssignment() - START | userId: {}, courseId: {}, assignmentId: {}",
        userId,
        courseId,
        assignmentId);

    findCourseByIdAndUser(courseId, userId);

    Assignment assignment = findAssignmentByIdAndCourse(assignmentId, courseId);

    /* 텍스트 제출은 제출 답안을, 파일 제출은 OCR 텍스트를 이전 답안으로 쓴다. */
    String previousAnswer =
        submissionRepository
            .findTopByAssignmentIdAndUserIdOrderByCreatedAtDesc(assignmentId, userId)
            .map(this::resolvePreviousAnswer)
            .orElse(null);

    AssignmentDetailResponse result = assignmentMapper.toDetailResponse(assignment, previousAnswer);

    log.debug("[AssignmentService] getAssignment() - END | assignmentId: {}", assignmentId);
    return result;
  }

  /**
   * 과제를 제출하고 submission 레코드를 status=queued로 만든다. 마감일이 지난 과제와 이미 제출한 과제는
   * 제출을 막는다.
   */
  @Transactional
  public SubmissionResponse submitAssignment(
      String userId, String courseId, String assignmentId, SubmitAnswerRequest request) {
    log.debug(
        "[AssignmentService] submitAssignment() - START | userId: {}, courseId: {}, assignmentId: {}",
        userId,
        courseId,
        assignmentId);

    Course course = findCourseByIdAndUser(courseId, userId);

    Assignment assignment = findAssignmentByIdAndCourse(assignmentId, courseId);

    if (assignment.getDueDate() != null && assignment.getDueDate().isBefore(LocalDateTime.now())) {
      throw new CustomException(AssignmentErrorCode.ASG_002);
    }

    if (submissionRepository.existsByAssignmentIdAndUserId(assignmentId, userId)) {
      throw new CustomException(AssignmentErrorCode.ASG_003);
    }

    String answersJson;
    try {
      answersJson = objectMapper.writeValueAsString(request.answers());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("답안 직렬화 실패", e);
    }

    Submission submission =
        Submission.builder()
            .id(IdGenerator.submission())
            .assignment(assignment)
            .user(course.getUser())
            .status("queued")
            .submittedAnswers(answersJson)
            .build();

    Submission saved = submissionRepository.save(submission);

    /*
        FastAPI 텍스트 채점은 커밋 뒤 비동기로 트리거한다.
        FastAPI 콜백이 커밋 전 submission을 읽지 못하므로 afterCommit 시점에 실행한다.
    */
    String answerText =
        request.answers().stream()
            .map(SubmitAnswerRequest.AnswerItem::answer)
            .filter(a -> a != null && !a.isBlank())
            .collect(Collectors.joining("\n\n"));
    triggerGradingAfterCommit(saved, assignment, answerText);

    SubmissionResponse result = assignmentMapper.toSubmissionResponse(saved);

    log.debug("[AssignmentService] submitAssignment() - END | submissionId: {}", saved.getId());
    return result;
  }

  /**
   * 직전 제출의 답안 텍스트를 도출한다. 텍스트 제출은 submittedAnswers(JSON)에서 답안을 추출하고, 파일/OCR 제출은
   * officialOcrText를 사용한다. submittedAnswers 파싱이 실패하면 officialOcrText로 폴백한다.
   */
  private String resolvePreviousAnswer(Submission submission) {
    String json = submission.getSubmittedAnswers();
    if (json != null && !json.isBlank()) {
      try {
        List<SubmitAnswerRequest.AnswerItem> answers =
            objectMapper.readValue(json, new TypeReference<>() {});
        String text =
            answers.stream()
                .map(SubmitAnswerRequest.AnswerItem::answer)
                .filter(a -> a != null && !a.isBlank())
                .collect(Collectors.joining("\n\n"));
        if (!text.isBlank()) {
          return text;
        }
      } catch (JsonProcessingException e) {
        log.warn(
            "[AssignmentService] submittedAnswers 파싱 실패 — officialOcrText로 폴백 | {}", e.getMessage());
      }
    }
    return submission.getOfficialOcrText();
  }

  /**
   * 제출 저장 트랜잭션이 커밋된 뒤 FastAPI 텍스트 채점을 비동기로 트리거한다. 테스트처럼 트랜잭션 동기화가
   * 없으면 즉시 실행한다. LessonService.triggerNextLessonAfterCommit과 같은 afterCommit 패턴이다.
   */
  private void triggerGradingAfterCommit(
      Submission submission, Assignment assignment, String answerText) {
    Runnable trigger =
        () -> gradingTriggerService.triggerTextGrading(submission, assignment, answerText);

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      trigger.run();
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            trigger.run();
          }
        });
  }

  /** 최신 제출의 채점 결과를 조회한다. status=done인 제출만 반환한다. */
  @Transactional(readOnly = true)
  public GradeResponse getGrades(String userId, String courseId, String assignmentId) {
    log.debug(
        "[AssignmentService] getGrades() - START | userId: {}, courseId: {}, assignmentId: {}",
        userId,
        courseId,
        assignmentId);

    findCourseByIdAndUser(courseId, userId);

    Assignment assignment = findAssignmentByIdAndCourse(assignmentId, courseId);

    Submission submission =
        submissionRepository
            .findTopByAssignmentIdAndUserIdOrderByCreatedAtDesc(assignmentId, userId)
            .orElseThrow(() -> new CustomException(AssignmentErrorCode.ASG_005));

    if (!"done".equals(submission.getStatus())) {
      throw new CustomException(AssignmentErrorCode.ASG_004);
    }

    GradeResponse result = assignmentMapper.toGradeResponse(submission, assignment.getMaxScore());

    log.debug("[AssignmentService] getGrades() - END | submissionId: {}", submission.getId());
    return result;
  }

  // 내부 헬퍼

  /** 과외를 조회하고 소유권을 확인한다. 없거나 소유자가 달라도 존재를 드러내지 않도록 똑같이 CRS_001을 던진다. */
  private Course findCourseByIdAndUser(String courseId, String userId) {
    Course course =
        courseRepository
            .findById(courseId)
            .orElseThrow(() -> new CustomException(CourseErrorCode.CRS_001));
    if (!course.getUser().getId().equals(userId)) {
      throw new CustomException(CourseErrorCode.CRS_001);
    }
    return course;
  }

  /** 과제를 조회하고 소속 과외를 확인한다. 없거나 소속 과외가 다르면 ASG_001을 던진다. */
  private Assignment findAssignmentByIdAndCourse(String assignmentId, String courseId) {
    Assignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new CustomException(AssignmentErrorCode.ASG_001));
    if (assignment.getCourse() == null || !assignment.getCourse().getId().equals(courseId)) {
      throw new CustomException(AssignmentErrorCode.ASG_001);
    }
    return assignment;
  }
}
