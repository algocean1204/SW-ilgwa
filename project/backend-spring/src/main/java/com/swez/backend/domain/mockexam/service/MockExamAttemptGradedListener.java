/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.mockexam.service;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.event.ExamAttemptGradedEvent;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.QuestionReviewData;
import com.swez.backend.domain.examgrading.port.ExamAttemptRepositoryPort;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;
import com.swez.backend.domain.mockexam.entity.MockExam;
import com.swez.backend.domain.mockexam.entity.MockExamSubmission;
import com.swez.backend.domain.mockexam.enums.MockExamStatus;
import com.swez.backend.domain.mockexam.repository.MockExamRepository;
import com.swez.backend.domain.mockexam.repository.MockExamSubmissionRepository;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * examgrading 채점 완료 이벤트를 받아 mock_exam_submission 에 복제 저장한다.
 *
 * <p>응시 플로우(프론트 → /api/exam-forge/submissions/.../submit)는 examgrading 경로로만 채점·저장한다.
 * 그런데 ChapterStudio 약점 집계(weakness_aggregator)는 {@code public.mock_exam_submission.analysis}의
 * weakTopics JSON을 읽고, 레거시 결과 조회 API도 이 테이블을 쓴다. 그래서 채점 결과와 AI 총평을 mockexam
 * 테이블로 복제해 양쪽을 맞춘다.
 *
 * <p>AFTER_COMMIT + @Async 라서 채점 트랜잭션 커밋 뒤 별도 스레드에서 돌고 제출 응답을 늦추지 않는다.
 * 복제 실패는 채점 성공에 영향을 주지 않으며, 예외를 삼키지 않고 반드시 로그로 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockExamAttemptGradedListener {

  private final ExamAttemptRepository examAttemptRepository;
  private final ExamAttemptRepositoryPort examAttemptRepositoryPort;
  private final MockExamRepository mockExamRepository;
  private final MockExamSubmissionRepository mockExamSubmissionRepository;
  private final ObjectMapper objectMapper;

  /**
   * mock_exam_submission 을 저장하고 mock_exam 상태를 COMPLETED 로 맞춘다.
   *
   * <p>mockExamId가 없는 독립 응시는 복제 대상이 아니라 건너뛴다. 이미 제출 기록이 있으면 멱등하게 건너뛴다.
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleGraded(ExamAttemptGradedEvent event) {
    try {
      ExamAttempt attempt = examAttemptRepository.findById(event.attemptId()).orElse(null);
      if (attempt == null || attempt.getMockExamId() == null) {
        // 모의고사 카드와 연결되지 않은 독립 응시는 복제하지 않는다
        return;
      }

      MockExam exam = mockExamRepository.findById(attempt.getMockExamId()).orElse(null);
      if (exam == null) {
        log.warn(
            "[MockExamAttemptGradedListener] 연결된 mock_exam 없음 — 복제 생략 | attemptId: {}, mockExamId: {}",
            event.attemptId(),
            attempt.getMockExamId());
        return;
      }

      // 중복 콜백·재제출에 대비해 이미 제출 기록이 있으면 다시 저장하지 않는다 (멱등)
      if (mockExamSubmissionRepository
          .findByMockExamIdAndUserId(exam.getId(), event.userId())
          .isPresent()) {
        log.info(
            "[MockExamAttemptGradedListener] 기존 제출 기록 존재 — 복제 생략 | mockExamId: {}", exam.getId());
        return;
      }

      ExamGradeResult gradeResult =
          examAttemptRepositoryPort.findGradeResult(event.attemptId()).orElse(null);
      if (gradeResult == null) {
        log.warn(
            "[MockExamAttemptGradedListener] 채점 결과 없음 — 복제 생략 | attemptId: {}", event.attemptId());
        return;
      }

      int score = gradeResult.totalScore() != null ? gradeResult.totalScore().intValue() : 0;
      int totalPoints = gradeResult.maxScore() != null ? gradeResult.maxScore().intValue() : 0;
      String analysisJson =
          examAttemptRepositoryPort.findAnalysisJson(event.attemptId()).orElse(null);

      MockExamSubmission submission =
          MockExamSubmission.builder()
              .id(IdGenerator.mockExamSubmission())
              .mockExam(exam)
              .user(attempt.getUser())
              .answers(buildAnswersJson(event.attemptId()))
              .score(score)
              .totalPoints(totalPoints)
              .timeTaken(null) // examgrading 경로는 소요 시간을 수집하지 않는다
              .grade(calculateGrade(score, totalPoints))
              // 약점 집계가 weakTopics 키를 읽으므로 {"summary","weakTopics"} JSON 을 그대로 저장한다
              .analysis(analysisJson)
              .build();
      mockExamSubmissionRepository.save(submission);

      // 목록 화면에서 모의고사 카드가 COMPLETED 로 보이게 상태를 맞춘다
      exam.updateStatus(MockExamStatus.COMPLETED);
      mockExamRepository.save(exam);

      log.info(
          "[MockExamAttemptGradedListener] 제출 복제 완료 | attemptId: {}, mockExamId: {}, 총평: {}",
          event.attemptId(),
          exam.getId(),
          analysisJson != null ? "포함" : "없음");
    } catch (RuntimeException e) {
      // 복제 실패는 채점 성공을 깨지 않는다. 예외를 삼키지 않고 반드시 로그로 남긴다
      log.error(
          "[MockExamAttemptGradedListener] mock_exam_submission 복제 실패 | attemptId: {}, error: {}",
          event.attemptId(),
          e.getMessage(),
          e);
    }
  }

  /** 제출 답안을 레거시 answers JSON([{questionId, selectedOption}]) 형태로 직렬화한다. */
  private String buildAnswersJson(String attemptId) {
    try {
      List<QuestionReviewData> reviewData = examAttemptRepositoryPort.findReviewData(attemptId);
      ArrayNode array = objectMapper.createArrayNode();
      for (QuestionReviewData rd : reviewData) {
        ObjectNode item = array.addObject();
        item.put("questionId", rd.question().questionId());
        item.put(
            "selectedOption", rd.submittedAnswerJson() != null ? rd.submittedAnswerJson() : "");
      }
      return array.toString();
    } catch (RuntimeException e) {
      log.warn(
          "[MockExamAttemptGradedListener] answers 직렬화 실패 — 빈 배열로 대체 | attemptId: {}", attemptId);
      return "[]";
    }
  }

  /** 점수 비율로 등급을 계산한다. MockExamService.calculateGrade 와 같은 정책이다. */
  private String calculateGrade(int score, int totalPoints) {
    if (totalPoints == 0) {
      return "F";
    }
    double percent = (double) score / totalPoints * 100;
    if (percent >= 90) return "A+";
    if (percent >= 85) return "A";
    if (percent >= 80) return "B+";
    if (percent >= 75) return "B";
    if (percent >= 70) return "C+";
    if (percent >= 65) return "C";
    if (percent >= 60) return "D";
    return "F";
  }
}
