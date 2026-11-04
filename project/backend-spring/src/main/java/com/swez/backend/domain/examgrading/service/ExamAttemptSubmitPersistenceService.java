/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swez.backend.domain.examgrading.client.FastApiExamAnalysisResponse;
import com.swez.backend.domain.examgrading.dto.request.AttemptSubmitRequest;
import com.swez.backend.domain.examgrading.dto.request.SubmittedAnswerRequest;
import com.swez.backend.domain.examgrading.dto.response.ExamSubmissionGradeResponse;
import com.swez.backend.domain.examgrading.event.ExamAttemptGradedEvent;
import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.mapper.ExamGradingMapper;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;
import com.swez.backend.domain.examgrading.port.ExamAttemptRepositoryPort;
import com.swez.backend.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 응시 제출·채점의 트랜잭션 구간만 묶는 영속 서비스.
 *
 * <p>FastAPI 총평(analyze) 동기 호출은 read timeout이 최대 200초라, 채점 트랜잭션 안에서 호출하면 그 시간 동안 DB
 * 커넥션이 점유되어 동시 제출 시 커넥션 풀 고갈·행 잠금 장기화 위험이 있다. 그래서 제출 흐름을 3단계로 나누고 이 빈이 1단계(채점 영속)와 3단계(총평 저장 + 이벤트
 * 발행)의 짧은 트랜잭션만 연다. 2단계(FastAPI 호출)는 {@link ExamAttemptSubmitService}가 트랜잭션 밖에서 한다.
 *
 * <p>별도 빈으로 분리한 이유는 자기 호출(self-invocation) 시 @Transactional 프록시가 동작하지 않기 때문이다 ({@link
 * ExamAttemptGenerationFailureService}와 동일 패턴).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAttemptSubmitPersistenceService {

  private final ExamAttemptRepositoryPort attemptRepository;
  private final ExamSubmissionGradingService gradingService;
  private final ExamGradingMapper mapper;
  private final GradingLeakGuard leakGuard;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 1단계(채점 영속) 결과.
   *
   * <p>멱등 경로(이미 채점 완료된 attempt 재제출)면 cachedResponse만 채워지고 나머지는 null이다. 신규 채점 경로면 cachedResponse가
   * null이고 2단계 총평 요청에 필요한 재료(gradeResult·snapshot·submittedAnswers·subject)가 채워진다.
   *
   * @param cachedResponse 멱등 경로의 기존 채점 응답 (신규 채점이면 null)
   * @param snapshot 채점 스냅샷 (서버 전용 필드 포함, 응답에 직접 노출하지 않는다)
   * @param subject 분석 요청용 과목명. LAZY course 접근이 필요해 트랜잭션 안에서 미리 해석한다
   */
  public record SubmitPhaseOutcome(
      ExamSubmissionGradeResponse cachedResponse,
      ExamGradeResult gradeResult,
      ExamAttemptSnapshot snapshot,
      List<SubmittedAnswer> submittedAnswers,
      String subject) {

    /** 멱등 경로 여부. true면 기존 결과를 그대로 반환하면 된다 */
    public boolean idempotent() {
      return cachedResponse != null;
    }
  }

  /**
   * 1단계: 답안 저장, 채점, 채점 결과 영속을 한 트랜잭션에서 한다.
   *
   * <p>답안 저장(saveSubmittedAnswers) 후 채점(gradeSnapshot)이 실패하면 SUBMITTED 전이가 롤백되어 attempt 상태가
   * IN_PROGRESS로 돌아간다. GradingLeakGuard 검증도 이 트랜잭션 안에서 해서, 누수가 잡히면 채점 영속까지 함께 롤백한다.
   *
   * @param userId 요청자 userId (소유권 검증용)
   * @return 멱등 경로면 기존 응답, 신규 채점이면 2단계 재료
   */
  @Transactional
  public SubmitPhaseOutcome submitAndPersistGrading(
      String attemptId, AttemptSubmitRequest request, String userId) {
    // 소유권 검증과 비관적 쓰기 락으로 같은 attempt 동시 제출(더블클릭/재제출)을 직렬화한다.
    // 두 번째 트랜잭션은 첫 트랜잭션이 SUBMITTED로 전이·커밋할 때까지 이 조회에서 대기하다가,
    // 락 해제 후 SUBMITTED 상태를 읽어 아래 멱등 경로로 빠진다(중복 채점·exam_grade_result PK 충돌을 막는다).
    com.swez.backend.domain.examgrading.entity.ExamAttempt existingAttempt =
        attemptRepository
            .findAttemptByOwnerForUpdate(attemptId, userId)
            .orElseThrow(() -> new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));

    // 이미 채점 완료/제출된 attempt의 재제출은 기존 결과를 그대로 반환한다.
    // GRADED / MANUAL_REVIEW_REQUIRED / SUBMITTED 상태 모두 채점 결과가 이미 저장된 상태이므로
    // exam_grade_result_pkey 중복 500 오류가 나지 않게 재채점 없이 기존 결과를 반환한다.
    com.swez.backend.domain.examgrading.enums.AttemptStatus currentStatus =
        existingAttempt.getStatus();
    boolean alreadyGraded =
        currentStatus == com.swez.backend.domain.examgrading.enums.AttemptStatus.GRADED
            || currentStatus
                == com.swez.backend.domain.examgrading.enums.AttemptStatus.MANUAL_REVIEW_REQUIRED
            || currentStatus == com.swez.backend.domain.examgrading.enums.AttemptStatus.SUBMITTED;

    if (alreadyGraded) {
      // 재채점·재저장 없이 저장된 결과와 AI 총평으로 멱등 응답한다
      ExamGradeResult existingResult =
          attemptRepository
              .findGradeResult(attemptId)
              .orElseThrow(
                  () -> new CustomException(ExamForgeGradingErrorCode.GRADE_RESULT_NOT_FOUND));
      log.info(
          "[ExamAttemptSubmitPersistenceService] 이미 채점 완료된 attempt 재제출 — 기존 결과 반환 | attemptId: {}, status: {}",
          attemptId,
          currentStatus);
      String storedAnalysisJson = attemptRepository.findAnalysisJson(attemptId).orElse(null);
      ExamSubmissionGradeResponse cached =
          mapper.toResponse(
              existingResult,
              parseAnalysisSummary(storedAnalysisJson),
              parseWeakTopics(storedAnalysisJson));
      return new SubmitPhaseOutcome(cached, null, null, null, null);
    }

    // "READY && seal 존재 && 미제출"일 때만 신규 채점을 허용한다.
    // 생성 중(GENERATING)·생성 실패(FAILED)·미시작(CREATED)이거나 정답지 봉인이 없는 응시에 제출이 들어오면
    // 채점할 수 없으므로 막는다. IN_PROGRESS는 레거시 시작 상태로 허용한다.
    assertSubmittable(attemptId, existingAttempt, currentStatus);

    // 스냅샷에는 서버 전용 필드(정답·해설 등)가 들어 있다
    ExamAttemptSnapshot snapshot =
        attemptRepository
            .findGradingSnapshot(attemptId)
            .orElseThrow(() -> new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));

    List<SubmittedAnswer> submittedAnswers = toDomainAnswers(request.submittedAnswers());

    // 답안 저장과 함께 attempt를 SUBMITTED로 전환한다
    attemptRepository.saveSubmittedAnswers(attemptId, submittedAnswers);

    // 결정론(객관식 등)과 AI 루브릭(서술형)으로 채점한다
    ExamGradeResult gradeResult = gradingService.gradeSnapshot(snapshot, submittedAnswers);

    // 결과를 저장하고 attempt를 GRADED 또는 MANUAL_REVIEW_REQUIRED로 전환한다
    attemptRepository.saveGradingResult(gradeResult);

    // 누수가 감지되면 이 트랜잭션 전체(답안·채점 영속)가 롤백된다.
    // 총평 필드는 FastAPI 생성 텍스트라 누수 표면이 아니므로 총평 없는 응답으로 검증해도 동일하다.
    leakGuard.assertPublicResponseSafe(mapper.toResponse(gradeResult), snapshot);

    // 과목명은 LAZY course 접근이 필요하므로 트랜잭션이 살아 있는 지금 해석해 둔다
    String subject = resolveSubject(existingAttempt);
    return new SubmitPhaseOutcome(null, gradeResult, snapshot, submittedAnswers, subject);
  }

  /**
   * 3단계: AI 총평 저장과 채점 완료 이벤트 발행을 짧은 트랜잭션으로 한다.
   *
   * <p>ExamAttemptGradedEvent는 mockexam 리스너가 AFTER_COMMIT으로 받으므로 이 트랜잭션 안에서 발행해야 한다. 총평
   * 저장과 같은 트랜잭션이라 리스너 실행 시점에는 analysis가 이미 커밋되어 있다.
   *
   * @param userId 응시자 userId (mockexam 복제 리스너용)
   * @param analysis FastAPI 총평 응답. null이면 저장 없이 이벤트만 발행한다
   */
  @Transactional
  public void persistAnalysisAndPublishGraded(
      String attemptId, String userId, FastApiExamAnalysisResponse analysis) {
    if (analysis != null) {
      attemptRepository.saveAnalysisJson(attemptId, toAnalysisJson(analysis));
    }
    // AFTER_COMMIT 리스너가 이 이벤트로 mock_exam_submission을 복제 저장한다
    eventPublisher.publishEvent(new ExamAttemptGradedEvent(attemptId, userId));
  }

  /**
   * 신규 채점을 진행하기 전 응시가 제출 가능한 상태인지 검증한다.
   *
   * <p>제출 허용 조건은 "READY && answer_key_seal 존재" 또는 레거시 IN_PROGRESS다. 그 외 상태는 다음 사유로 차단한다:
   *
   * <ul>
   *   <li>GENERATING: 문항 생성 중. 생성 콜백 전 제출 접근(스냅샷 미완성).
   *   <li>FAILED: 문항 생성 실패. 채점 대상 자체가 없다.
   *   <li>CREATED: 시작 전. seal 미발급.
   *   <li>READY인데 answer_key_seal이 비어 있음: 무결성 검증 불가(비정상 콜백).
   * </ul>
   *
   * 멱등 경로(GRADED/SUBMITTED 등)는 이 메서드 호출 전에 이미 분기되므로 여기로 오지 않는다.
   *
   * @throws CustomException 위 차단 사유에 해당하면 ATTEMPT_NOT_SUBMITTABLE(409)
   */
  private void assertSubmittable(
      String attemptId,
      com.swez.backend.domain.examgrading.entity.ExamAttempt attempt,
      com.swez.backend.domain.examgrading.enums.AttemptStatus status) {
    boolean submittableState =
        status == com.swez.backend.domain.examgrading.enums.AttemptStatus.READY
            || status == com.swez.backend.domain.examgrading.enums.AttemptStatus.IN_PROGRESS;
    if (!submittableState) {
      log.warn(
          "[ExamAttemptSubmitPersistenceService] 제출 불가 상태 — 차단 | attemptId: {}, status: {}",
          attemptId,
          status);
      throw new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_SUBMITTABLE);
    }
    // READY 상태는 FastAPI 생성 콜백이 정답지 봉인(answer_key_seal)을 함께 저장해야 정상이다.
    // seal이 없으면 채점 무결성을 검증할 수 없어 제출을 막는다(비정상 콜백 대비).
    if (status == com.swez.backend.domain.examgrading.enums.AttemptStatus.READY
        && (attempt.getAnswerKeySeal() == null || attempt.getAnswerKeySeal().isBlank())) {
      log.warn(
          "[ExamAttemptSubmitPersistenceService] READY인데 정답지 봉인 없음 — 제출 차단 | attemptId: {}",
          attemptId);
      throw new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_SUBMITTABLE);
    }
  }

  /** SubmittedAnswerRequest 리스트를 도메인 SubmittedAnswer 리스트로 변환한다. null이면 빈 리스트로 처리한다. */
  private List<SubmittedAnswer> toDomainAnswers(List<SubmittedAnswerRequest> requests) {
    if (requests == null) {
      return List.of();
    }
    return requests.stream().map(req -> mapper.toDomain(req)).toList();
  }

  /** 분석 요청 헤더용 과목명. 과외 subject를 쓰고, 없으면 "모의고사". */
  private String resolveSubject(com.swez.backend.domain.examgrading.entity.ExamAttempt attempt) {
    try {
      if (attempt.getCourse() != null && attempt.getCourse().getSubject() != null) {
        String subject = attempt.getCourse().getSubject();
        if (!subject.isBlank()) {
          return subject;
        }
      }
    } catch (RuntimeException e) {
      // LAZY 로딩 실패 등은 과목명 폴백으로 처리한다
      log.warn("[ExamAttemptSubmitPersistenceService] 과목명 조회 실패 — 기본값 사용: {}", e.getMessage());
    }
    return "모의고사";
  }

  /** 분석 응답을 저장용 JSON({"summary","weakTopics"})으로 직렬화한다. */
  private String toAnalysisJson(FastApiExamAnalysisResponse analysis) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("summary", analysis.analysis());
    var topicsNode = node.putArray("weakTopics");
    if (analysis.weakTopics() != null) {
      analysis.weakTopics().forEach(topicsNode::add);
    }
    return node.toString();
  }

  /** 저장된 분석 JSON에서 총평 본문을 추출한다. 파싱 실패 시 원문 그대로 반환(과거 형식 호환). */
  private String parseAnalysisSummary(String analysisJson) {
    if (analysisJson == null || analysisJson.isBlank()) {
      return null;
    }
    try {
      var node = objectMapper.readTree(analysisJson);
      if (node.hasNonNull("summary")) {
        return node.get("summary").asText();
      }
      return analysisJson;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return analysisJson;
    }
  }

  /** 저장된 분석 JSON에서 약점 주제 목록을 추출한다. 없으면 null. */
  private List<String> parseWeakTopics(String analysisJson) {
    if (analysisJson == null || analysisJson.isBlank()) {
      return null;
    }
    try {
      var node = objectMapper.readTree(analysisJson);
      var topics = node.get("weakTopics");
      if (topics == null || !topics.isArray()) {
        return null;
      }
      List<String> result = new ArrayList<>();
      topics.forEach(t -> result.add(t.asText()));
      return result;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return null;
    }
  }
}
