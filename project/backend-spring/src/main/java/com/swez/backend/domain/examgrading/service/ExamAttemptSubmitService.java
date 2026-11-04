/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.examgrading.client.ExamForgeFastApiClient;
import com.swez.backend.domain.examgrading.client.FastApiExamAnalysisRequest;
import com.swez.backend.domain.examgrading.client.FastApiExamAnalysisResponse;
import com.swez.backend.domain.examgrading.dto.request.AttemptSubmitRequest;
import com.swez.backend.domain.examgrading.dto.response.ExamSubmissionGradeResponse;
import com.swez.backend.domain.examgrading.mapper.ExamGradingMapper;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.QuestionOption;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;
import com.swez.backend.domain.examgrading.service.ExamAttemptSubmitPersistenceService.SubmitPhaseOutcome;
import com.swez.backend.domain.examgrading.support.ReviewAnswerFormatter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 응시 제출, 채점, AI 총평 요청을 순서대로 묶는 서비스.
 *
 * <p>응시자가 제출한 답안을 exam_submitted_answer에 저장하고, attempt 상태를 SUBMITTED로 전환한 뒤, 기존
 * ExamSubmissionGradingService.gradeSnapshot을 호출하여 채점한다.
 *
 * <p>FastAPI 총평(analyze) 동기 호출은 read timeout이 최대 200초(통상 10~60초)라 채점 트랜잭션
 * 안에서 호출하면 DB 커넥션을 호출 내내 점유한다(동시 제출 시 커넥션 풀 고갈 위험). 그래서 이 클래스는 @Transactional 없이 흐름만 조율하고, 트랜잭션 구간은
 * {@link ExamAttemptSubmitPersistenceService}에 위임해 3단계로 나눈다:
 *
 * <ol>
 *   <li><b>1단계 (트랜잭션)</b>: 소유권 검증·멱등 확인·답안 저장·채점·채점 결과 영속·LeakGuard 검증.
 *   <li><b>2단계 (트랜잭션 없음)</b>: FastAPI /api/mock-exams/analyze 동기 호출. 실패하면 analysis=null로 두고
 *       채점 실패로 전파하지 않는다.
 *   <li><b>3단계 (짧은 트랜잭션)</b>: 총평을 exam_grade_result.analysis에 저장 + ExamAttemptGradedEvent 발행.
 *       mockexam 리스너가 AFTER_COMMIT으로 mock_exam_submission을 복제 저장한다.
 * </ol>
 *
 * <p>HTTP 응답에는 2단계 결과(총평·약점 주제)가 그대로 들어간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAttemptSubmitService {

  private final ExamAttemptSubmitPersistenceService persistenceService;
  private final ExamGradingMapper mapper;
  private final ExamForgeFastApiClient fastApiClient;
  private final ReviewAnswerFormatter answerFormatter;
  private final ObjectMapper objectMapper;

  /**
   * 응시를 제출하고 채점한다.
   *
   * <p>이 메서드 자체는 트랜잭션이 없다. 1단계가 커밋된 뒤에는 채점 결과가 확정되므로, 이후 2·3단계가 실패해도 채점은 유지된다(총평만 누락). 1단계 안에서 채점이
   * 실패하면 SUBMITTED 전이가 롤백되어 attempt 상태가 IN_PROGRESS로 복원된다.
   *
   * @param userId 요청자 userId (소유권 검증용)
   * @return 채점 공개 응답 DTO (AI 총평 포함, 분석 실패 시 analysis=null)
   */
  public ExamSubmissionGradeResponse submitAndGrade(
      String attemptId, AttemptSubmitRequest request, String userId) {
    // 1단계: 답안 저장, 채점, 영속을 한 트랜잭션에서 하며 FastAPI는 호출하지 않는다
    SubmitPhaseOutcome outcome =
        persistenceService.submitAndPersistGrading(attemptId, request, userId);

    // 이미 채점 완료된 attempt 재제출은 기존 결과(저장된 총평 포함)를 그대로 반환한다
    if (outcome.idempotent()) {
      return outcome.cachedResponse();
    }

    // 2단계: AI 총평(약점 진단) 생성. 트랜잭션 밖이라 긴 호출(최대 200초)에도 DB 커넥션을 점유하지 않는다.
    // 실패하면 null로 두고 채점 흐름은 깨지 않는다.
    FastApiExamAnalysisResponse analysis =
        requestAnalysis(
            outcome.subject(),
            outcome.snapshot(),
            outcome.gradeResult(),
            outcome.submittedAnswers());

    // 3단계: 총평 저장과 채점 완료 이벤트 발행 (짧은 트랜잭션).
    // 여기서 실패해도 1단계에서 커밋된 채점 결과는 유지되므로 에러 로그만 남기고 응답은 정상 반환한다.
    try {
      persistenceService.persistAnalysisAndPublishGraded(attemptId, userId, analysis);
    } catch (RuntimeException e) {
      log.error(
          "[ExamAttemptSubmitService] 총평 저장/이벤트 발행 실패 — 채점 결과는 유지됨 | attemptId: {}, error: {}",
          attemptId,
          e.getMessage(),
          e);
    }

    // LeakGuard 검증은 1단계 트랜잭션 안에서 이미 끝났다
    ExamSubmissionGradeResponse response =
        mapper.toResponse(
            outcome.gradeResult(),
            analysis != null ? analysis.analysis() : null,
            analysis != null ? analysis.weakTopics() : null);

    log.info(
        "[ExamAttemptSubmitService] 제출·채점 완료 | attemptId: {}, 총점: {}/{}, 총평: {}",
        attemptId,
        outcome.gradeResult().totalScore(),
        outcome.gradeResult().maxScore(),
        analysis != null ? "생성됨" : "없음(분석 실패/생략)");
    return response;
  }

  // AI 총평(약점 진단) 헬퍼

  /**
   * 채점 결과를 FastAPI 분석 계약으로 변환해 총평을 요청한다.
   *
   * <p>요청 조립 중 어떤 오류가 나도 채점은 성공해야 하므로 전부 잡아 로깅 후 null을 반환한다.
   *
   * @param subject 과목명. LAZY 로딩을 피하려고 1단계 트랜잭션 안에서 미리 해석한 값
   */
  private FastApiExamAnalysisResponse requestAnalysis(
      String subject,
      ExamAttemptSnapshot snapshot,
      ExamGradeResult gradeResult,
      List<SubmittedAnswer> submittedAnswers) {
    try {
      Map<String, QuestionGrade> gradeByQuestionId = new HashMap<>();
      if (gradeResult.results() != null) {
        for (QuestionGrade grade : gradeResult.results()) {
          gradeByQuestionId.put(grade.questionId(), grade);
        }
      }
      Map<String, SubmittedAnswer> answerByQuestionId = new HashMap<>();
      for (SubmittedAnswer answer : submittedAnswers) {
        answerByQuestionId.put(answer.questionId(), answer);
      }

      List<FastApiExamAnalysisRequest.ResultItem> items = new ArrayList<>();
      int idx = 0;
      for (ExamQuestion question : snapshot.questions()) {
        idx++;
        QuestionGrade grade = gradeByQuestionId.get(question.questionId());
        SubmittedAnswer answer = answerByQuestionId.get(question.questionId());
        String answerJson =
            answer != null && answer.answer() != null ? answer.answer().toString() : null;
        items.add(
            FastApiExamAnalysisRequest.ResultItem.builder()
                .questionIdx(idx)
                .questionText(question.stem())
                .correct(grade != null && grade.correct())
                .options(serializeOptionTexts(question))
                .selectedOption("")
                .correctOption("")
                // 여러 문항 유형을 다루려고 보기 인덱스 대신 사람이 읽는 텍스트를 직접 전달한다
                .selectedText(answerFormatter.formatMyAnswer(answerJson, question))
                .correctText(answerFormatter.formatCorrectAnswer(question))
                .explanation(question.explanation() != null ? question.explanation() : "")
                .build());
      }

      int score = gradeResult.totalScore() != null ? gradeResult.totalScore().intValue() : 0;
      int totalPoints = gradeResult.maxScore() != null ? gradeResult.maxScore().intValue() : 0;
      FastApiExamAnalysisRequest request =
          FastApiExamAnalysisRequest.builder()
              .examId(gradeResult.examId() != null ? gradeResult.examId() : gradeResult.attemptId())
              .subject(subject)
              .score(score)
              .totalPoints(totalPoints)
              .grade(calculateGrade(score, totalPoints))
              .results(items)
              .build();
      return fastApiClient.analyzeMockExam(request);
    } catch (RuntimeException e) {
      // 분석 요청 조립에 실패해도 채점 흐름은 유지하고 에러 로그를 남긴다
      log.error(
          "[ExamAttemptSubmitService] 총평 요청 조립 실패, analysis=null로 진행 | attemptId: {}, error: {}",
          gradeResult.attemptId(),
          e.getMessage());
      return null;
    }
  }

  /** 점수 비율로 등급을 계산한다. mockexam 레거시(calculateGrade)와 같은 기준이다. */
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

  /** 객관식 보기 텍스트 목록을 JSON 배열 문자열로 직렬화한다(레거시 analyze 계약의 options 필드). */
  private String serializeOptionTexts(ExamQuestion question) {
    try {
      List<String> texts =
          question.options() == null
              ? List.of()
              : question.options().stream().map(QuestionOption::text).toList();
      return objectMapper.writeValueAsString(texts);
    } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
      return "[]";
    }
  }
}
