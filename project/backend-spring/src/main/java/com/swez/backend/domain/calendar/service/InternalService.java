/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.calendar.dto.request.AiResultCallbackRequest;
import com.swez.backend.domain.calendar.dto.request.CreateSubmissionRequest;
import com.swez.backend.domain.calendar.dto.response.AiResultAckResponse;
import com.swez.backend.domain.calendar.dto.response.SubmissionResponse;
import com.swez.backend.domain.calendar.entity.Assignment;
import com.swez.backend.domain.calendar.entity.Submission;
import com.swez.backend.domain.calendar.exception.SubmissionErrorCode;
import com.swez.backend.domain.calendar.mapper.CalendarMapper;
import com.swez.backend.domain.calendar.repository.AssignmentRepository;
import com.swez.backend.domain.calendar.repository.SubmissionRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** FastAPI → Spring 내부 콜백으로 제출물을 생성하고 채점 결과를 받는다. */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class InternalService {

  private final SubmissionRepository submissionRepository;
  private final AssignmentRepository assignmentRepository;
  private final CalendarMapper calendarMapper;

  /** 채점 완료 후 학생에게 결과 DM 을 보낸다. 중복 발송을 막으려고 DM 은 Spring 에서만 보낸다. */
  private final SubmissionResultDmService resultDmService;

  /** FastAPI 요청으로 제출물 row 를 생성한다. X-Internal-Token 검증은 InternalTokenFilter 가 이미 마쳤다. */
  @Transactional
  public SubmissionResponse createSubmission(CreateSubmissionRequest request) {
    log.debug(
        "[InternalService] createSubmission() - START | assignmentId: {}", request.assignmentId());

    Assignment assignment =
        assignmentRepository
            .findById(request.assignmentId())
            .orElseThrow(() -> new CustomException(SubmissionErrorCode.SBM_001));

    Submission submission =
        Submission.builder()
            .id(IdGenerator.submission())
            .assignment(assignment)
            .user(assignment.getUser()) // request.userId() 는 다른 사용자일 수 있어 과제 소유자로 고정한다
            .officialFileUrl(request.officialFileUrl())
            .isRetry(Boolean.TRUE.equals(request.isRetry()))
            .status("queued")
            .build();
    submissionRepository.save(submission);

    SubmissionResponse result = calendarMapper.toSubmissionResponse(submission);
    log.debug("[InternalService] createSubmission() - END | submissionId: {}", submission.getId());
    return result;
  }

  /**
   * FastAPI 에서 AI 채점 결과를 받아 submission 상태를 전이한다. X-Internal-Token 검증은
   * InternalTokenFilter 가 이미 마쳤다.
   */
  @Transactional
  public AiResultAckResponse receiveAiResult(String submissionId, AiResultCallbackRequest request) {
    log.debug(
        "[InternalService] receiveAiResult() - START | submissionId: {}, status: {}",
        submissionId,
        request.statusTransition());

    Submission submission =
        submissionRepository
            .findById(submissionId)
            .orElseThrow(() -> new CustomException(SubmissionErrorCode.SBM_001));

    /*
        중복 콜백을 처리한다.
        - done 콜백 재시도는 과제 상태 전이를 한 번 더 적용하고 ack 한다.
        - failed 상태는 재처리하지 않는다.
    */
    if ("done".equals(submission.getStatus())) {
      if (isCompletedTransition(request.statusTransition())) {
        completeAssignment(submission);
        log.debug(
            "[InternalService] receiveAiResult() - IDEMPOTENT_DONE | submissionId: {}",
            submissionId);
        return buildAckResponse();
      }
      throw new CustomException(SubmissionErrorCode.SBM_002);
    }
    if ("failed".equals(submission.getStatus())) {
      throw new CustomException(SubmissionErrorCode.SBM_002);
    }

    /*
        retry_recommended 는 FastAPI 가 텔레그램에 직접 안내하므로 Spring 에서 따로 저장할 필요가 없다.
    */
    submission.applyAiResult(
        request.statusTransition(),
        request.ocrText(),
        request.score(),
        request.feedback(),
        request.aiConfidence(),
        request.ocrFailedPages(),
        request.totalPages());

    if (isCompletedTransition(request.statusTransition())) {
      completeAssignment(submission);
    }
    submissionRepository.save(submission);

    /*
        채점 완료(done) 때만 학생에게 결과 DM 을 보낸다.
        - FastAPI 도 DM 을 보내면 중복 발송되므로 DM 은 Spring 에서만 보낸다.
        - 채점 실패(failed)는 따로 안내하지 않는다.
    */
    if (isCompletedTransition(request.statusTransition())) {
      resultDmService.sendResultDm(submission);
    }

    log.debug(
        "[InternalService] receiveAiResult() - END | submissionId: {}, newStatus: {}",
        submissionId,
        request.statusTransition());
    return buildAckResponse();
  }

  private boolean isCompletedTransition(String statusTransition) {
    return "done".equals(statusTransition);
  }

  private void completeAssignment(Submission submission) {
    Assignment assignment = submission.getAssignment();
    assignment.complete();
    assignmentRepository.save(assignment);
  }

  private AiResultAckResponse buildAckResponse() {
    return AiResultAckResponse.builder().ack(true).nextAction("notify_user").build();
  }
}
