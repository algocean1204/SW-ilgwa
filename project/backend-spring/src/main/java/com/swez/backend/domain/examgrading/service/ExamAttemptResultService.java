/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.examgrading.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.swez.backend.domain.examgrading.dto.response.AttemptResultResponse;
import com.swez.backend.domain.examgrading.dto.response.QuestionGradeResponse;
import com.swez.backend.domain.examgrading.dto.response.RubricCriterionResponse;
import com.swez.backend.domain.examgrading.dto.response.SafeOptionResponse;
import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.QuestionOption;
import com.swez.backend.domain.examgrading.model.QuestionReviewData;
import com.swez.backend.domain.examgrading.model.RubricCriterion;
import com.swez.backend.domain.examgrading.port.ExamAttemptRepositoryPort;
import com.swez.backend.domain.examgrading.support.QuestionTemplatePolicy;
import com.swez.backend.domain.examgrading.support.ReviewAnswerFormatter;
import com.swez.backend.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 응시 결과 조회 서비스.
 *
 * <p>채점 완료된 응시의 결과(집계 요약 + 문항별 채점)를 공개 DTO로 반환한다.
 *
 * <h3>복습 필드를 채점 후에만 여는 방법</h3>
 *
 * <ol>
 *   <li>{@link #getAttemptResult}는 exam_grade_result가 있는지 먼저 본다. 미채점이면 ATTEMPT_NOT_FOUND라
 *       응시 중에는 이 경로에 들어올 수 없다.
 *   <li>복습 필드(reviewAnswer·reviewSolution 등)는 이 경로에서만 채운다. 응시 중 상세 조회
 *       ({@code ExamAttemptDetailService})는 이 서비스를 쓰지 않는다.
 *   <li>GradingLeakGuard가 차단 필드명({@code correctAnswer}, {@code explanation} 등)을 한 번 더 검사한다.
 *       그래서 복습 필드 이름을 {@code reviewAnswer}, {@code reviewSolution}으로 따로 지었다.
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAttemptResultService {

  private final ExamAttemptRepositoryPort attemptRepository;
  private final GradingLeakGuard leakGuard;
  private final ReviewAnswerFormatter answerFormatter;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  // QuestionTemplatePolicy는 Spring 빈이 아니므로 직접 생성한다 (DeterministicQuestionScorer와 동일한 패턴)
  private final QuestionTemplatePolicy templatePolicy = new QuestionTemplatePolicy();

  /**
   * 응시 결과를 조회한다. 채점 결과에 복습용 스냅샷과 제출 답안을 합쳐 공개 DTO로 만들고,
   * 응답은 GradingLeakGuard로 한 번 더 검사한다.
   *
   * @param userId    요청자 userId (소유권 검증용)
   * @return 채점 결과 공개 DTO (복습 필드 포함)
   */
  public AttemptResultResponse getAttemptResult(String attemptId, String userId) {
    attemptRepository
        .findAttemptByOwner(attemptId, userId)
        .orElseThrow(() -> new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));

    // 채점 전이거나 미채점이면 결과가 없어 ATTEMPT_NOT_FOUND
    ExamGradeResult gradeResult =
        attemptRepository
            .findGradeResult(attemptId)
            .orElseThrow(() -> new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));

    // 채점 완료 시험만 이 경로에 도달하므로 GRADED를 전제로 정답·해설을 노출한다
    List<QuestionReviewData> reviewData = attemptRepository.findReviewData(attemptId);

    // 채점 결과와 병합할 때 O(1)로 찾도록 questionId로 묶는다
    Map<String, QuestionReviewData> reviewByQuestionId =
        reviewData.stream()
            .collect(
                Collectors.toMap(
                    rd -> rd.question().questionId(),
                    rd -> rd,
                    // 동일 questionId가 중복일 경우 첫 번째 우선 (정상 데이터에서 발생 안 함)
                    (a, b) -> a));

    // 분석 실패/미생성이면 null이고 응답 필드도 null로 둔다
    String analysisJson = attemptRepository.findAnalysisJson(attemptId).orElse(null);

    AttemptResultResponse response = toResultResponse(gradeResult, reviewByQuestionId, analysisJson);

    // 차단 필드명(correctAnswer·explanation 등)이 응답에 없는지 2차로 확인한다
    leakGuard.assertPublicResponseSafe(response);

    log.info(
        "[ExamAttemptResultService] 응시 결과 조회 완료 | attemptId: {}, 총점: {}/{}",
        attemptId,
        gradeResult.totalScore(),
        gradeResult.maxScore());
    return response;
  }

  // 변환 헬퍼

  /** ExamGradeResult 도메인 record를 AttemptResultResponse 공개 DTO로 변환한다 (AI 총평 포함). */
  private AttemptResultResponse toResultResponse(
      ExamGradeResult result,
      Map<String, QuestionReviewData> reviewByQuestionId,
      String analysisJson) {
    return AttemptResultResponse.builder()
        .attemptId(result.attemptId())
        .examId(result.examId())
        .totalScore(result.totalScore())
        .maxScore(result.maxScore())
        .percentage(result.percentage())
        .passed(result.passed())
        .gradingStatus(result.gradingStatus())
        .gradedAt(result.gradedAt())
        .results(
            result.results() != null
                ? result.results().stream()
                    .map(grade -> toQuestionGradeResponse(grade, reviewByQuestionId))
                    .toList()
                : java.util.List.of())
        .analysis(parseAnalysisSummary(analysisJson))
        .weakTopics(parseWeakTopics(analysisJson))
        .build();
  }

  /** 저장된 분석 JSON({"summary","weakTopics"})에서 총평 본문을 추출한다. 파싱 실패 시 원문 반환. */
  private String parseAnalysisSummary(String analysisJson) {
    if (analysisJson == null || analysisJson.isBlank()) {
      return null;
    }
    try {
      var node = objectMapper.readTree(analysisJson);
      return node.hasNonNull("summary") ? node.get("summary").asText() : analysisJson;
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
      List<String> result = new java.util.ArrayList<>();
      topics.forEach(t -> result.add(t.asText()));
      return result;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return null;
    }
  }

  /**
   * QuestionGrade 도메인 record를 QuestionGradeResponse 공개 DTO로 변환한다.
   *
   * <p>reviewByQuestionId Map에서 해당 문항의 복습 데이터를 조회하여 복습 전용 필드를 채운다.
   * 복습 데이터가 없으면 복습 필드는 null로 처리한다.
   */
  private QuestionGradeResponse toQuestionGradeResponse(
      QuestionGrade grade, Map<String, QuestionReviewData> reviewByQuestionId) {
    QuestionReviewData review = reviewByQuestionId.get(grade.questionId());

    QuestionGradeResponse.QuestionGradeResponseBuilder builder =
        QuestionGradeResponse.builder()
            .questionId(grade.questionId())
            .templateId(grade.templateId())
            .score(grade.score())
            .maxScore(grade.maxScore())
            .correct(grade.correct())
            .gradingMode(grade.gradingMode())
            .feedback(grade.feedback())
            .rubricBreakdown(
                grade.rubricBreakdown() != null
                    ? grade.rubricBreakdown().stream().map(this::toRubricResponse).toList()
                    : java.util.List.of())
            .confidence(grade.confidence())
            .needsManualReview(grade.needsManualReview());

    if (review == null) {
      // 스냅샷 문항이 없으면 복습 필드를 null로 둔다
      log.warn(
          "[ExamAttemptResultService] 문항 스냅샷 없음 — 복습 필드 null | questionId: {}",
          grade.questionId());
      return builder
          .displayOrder(null)
          .stem(null)
          .displayOptions(null)
          .myAnswer(null)
          .reviewAnswer(null)
          .reviewSolution(null)
          .build();
    }

    ExamQuestion question = review.question();
    String submittedAnswerJson = review.submittedAnswerJson();

    // 엔티티에서 직접 읽어 QuestionReviewData에 담아 둔 순번
    int displayOrder = review.displayOrder();

    List<SafeOptionResponse> displayOptions = buildSafeOptions(question);

    String myAnswer = answerFormatter.formatMyAnswer(submittedAnswerJson, question);

    // 정답과 해설은 복습 경로(GRADED) 전용이라 reviewAnswer·reviewSolution 필드명을 쓴다
    String reviewAnswer = answerFormatter.formatCorrectAnswer(question);

    String reviewSolution = question.explanation();

    return builder
        .displayOrder(displayOrder)
        .stem(question.stem())
        .displayOptions(displayOptions)
        .myAnswer(myAnswer)
        .reviewAnswer(reviewAnswer)
        .reviewSolution(reviewSolution)
        .build();
  }

  /**
   * 객관식·매칭형 문항의 보기 목록을 is_correct를 뺀 공개 형식으로 변환한다.
   * 해당 유형이 아니거나 보기가 없으면 null을 반환한다.
   */
  private List<SafeOptionResponse> buildSafeOptions(ExamQuestion question) {
    String templateId = question.templateId();
    if (templateId == null) {
      return null;
    }
    // 객관식·OX형만 보기 목록 노출 (매칭형은 pairs 별도 표시이므로 제외)
    if (!templatePolicy.isChoice(templateId) && !templatePolicy.isTrueFalse(templateId)) {
      return null;
    }
    List<QuestionOption> options = question.options();
    if (options == null || options.isEmpty()) {
      return null;
    }
    // is_correct를 빼고 label·text만 SafeOptionResponse에 담는다
    return options.stream()
        .map(opt -> SafeOptionResponse.builder().label(opt.label()).text(opt.text()).build())
        .toList();
  }

  /** RubricCriterion 도메인 record를 RubricCriterionResponse DTO로 변환한다. */
  private RubricCriterionResponse toRubricResponse(RubricCriterion criterion) {
    return RubricCriterionResponse.builder()
        .criterion(criterion.criterion())
        .score(criterion.score())
        .maxScore(criterion.maxScore())
        .reason(criterion.reason())
        .build();
  }
}
