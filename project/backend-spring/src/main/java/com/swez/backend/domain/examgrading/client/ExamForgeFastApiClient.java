/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.client;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.GradingMode;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.RubricCriterion;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;
import com.swez.backend.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FastAPI ExamForge 엔드포인트 HTTP 클라이언트.
 *
 * <p>채점({@code gradeRubricQuestions})과 비동기 문항 생성({@code generateAsync}) 두 경로를 호출한다.
 *
 * <p>두 경로 모두 {@code examForgeRestClient} 빈을 재사용한다. generate-async 는 FastAPI 가 202 를 즉시 반환하므로
 * readTimeout(200s) 이내에 응답하고, 별도 클라이언트 인스턴스가 필요 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.fastapi",
    name = {"base-url", "api-key"})
public class ExamForgeFastApiClient {

  /** 채점·생성 공용 RestClient (ExamForgeRestClientConfig 빈, readTimeout 200s) */
  private final RestClient examForgeRestClient;

  /** FastAPI /api/exam-forge/grade-submission 엔드포인트를 호출해 루브릭 채점을 한다. */
  public List<QuestionGrade> gradeRubricQuestions(
      ExamAttemptSnapshot snapshot, List<SubmittedAnswer> rubricAnswers) {
    log.info(
        "[ExamForgeFastApiClient] gradeRubricQuestions() - START | attemptId: {}, count: {}",
        snapshot.attemptId(),
        rubricAnswers.size());

    if (rubricAnswers.isEmpty()) {
      return List.of();
    }

    List<ExamQuestion> rubricQuestions =
        snapshot.questions().stream()
            .filter(question -> containsAnswer(rubricAnswers, question.questionId()))
            .toList();

    try {
      FastApiGradeSubmissionResponse response =
          examForgeRestClient
              .post()
              .uri("/api/exam-forge/grade-submission")
              .contentType(MediaType.APPLICATION_JSON)
              .body(FastApiGradeSubmissionRequest.from(snapshot, rubricQuestions, rubricAnswers))
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  (request, rawResponse) -> {
                    String body =
                        new String(rawResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    log.warn(
                        "[ExamForgeFastApiClient] gradeRubricQuestions() - FastAPI error | status: {}, body: {}",
                        rawResponse.getStatusCode(),
                        body);
                    throw new CustomException(ExamForgeGradingErrorCode.FASTAPI_GRADING_FAILED);
                  })
              .body(FastApiGradeSubmissionResponse.class);

      if (response == null || response.results() == null) {
        throw new CustomException(ExamForgeGradingErrorCode.FASTAPI_GRADING_FAILED);
      }
      List<QuestionGrade> result = response.results().stream().map(this::toGrade).toList();
      log.info(
          "[ExamForgeFastApiClient] gradeRubricQuestions() - END | attemptId: {}, count: {}",
          snapshot.attemptId(),
          result.size());
      return result;
    } catch (CustomException e) {
      throw e;
    } catch (RestClientException e) {
      log.warn(
          "[ExamForgeFastApiClient] gradeRubricQuestions() - ERROR | attemptId: {}",
          snapshot.attemptId(),
          e);
      throw new CustomException(ExamForgeGradingErrorCode.FASTAPI_GRADING_FAILED);
    } catch (RuntimeException e) {
      log.warn(
          "[ExamForgeFastApiClient] gradeRubricQuestions() - invalid FastAPI response | attemptId: {}",
          snapshot.attemptId(),
          e);
      throw new CustomException(ExamForgeGradingErrorCode.FASTAPI_GRADING_FAILED);
    }
  }

  /**
   * FastAPI /api/exam-forge/mock/generate-async 엔드포인트를 호출해 비동기 문항 생성을 트리거한다.
   *
   * <p>FastAPI는 즉시 202 Accepted를 반환하고 백그라운드에서 문항을 생성한 뒤, 완료 시 Spring
   * /internal/exam-attempts/{attemptId}/generated 콜백으로 결과를 전달한다.
   *
   * <p>generate-async 는 202 를 즉시 반환하므로 readTimeout(200s) 이내에 끝난다. 긴 타임아웃용 클라이언트를 따로 두지 않고 공용
   * {@code examForgeRestClient} 빈을 재사용한다.
   *
   * @param topic 세부 주제 (nullable)
   * @param questionTypes FastAPI 등록 템플릿 ID 목록. 빈 목록이면 FastAPI가 유형을 자동 분배한다
   * @param difficulty 난이도 (easy/medium/hard). null이면 medium으로 폴백한다
   * @throws CustomException 4xx/5xx 또는 연결 오류가 나면 FASTAPI_GENERATION_FAILED
   */
  public void generateAsync(
      String attemptId,
      String courseId,
      String subject,
      String topic,
      Integer questionCount,
      List<String> questionTypes,
      String difficulty,
      BigDecimal passPercentage) {
    log.info(
        "[ExamForgeFastApiClient] generateAsync() - START | attemptId: {}, subject: {}, questionCount: {}, difficulty: {}",
        attemptId,
        subject,
        questionCount,
        difficulty);

    FastApiGenerateAsyncRequest requestBody =
        new FastApiGenerateAsyncRequest(
            attemptId,
            courseId,
            subject,
            topic,
            questionCount,
            // FastAPI 스키마는 list를 요구하므로 null은 빈 목록(자동 분배)으로 바꾼다
            questionTypes != null ? questionTypes : List.of(),
            normalizeDifficulty(difficulty),
            passPercentage);
    try {
      examForgeRestClient
          .post()
          .uri("/api/exam-forge/mock/generate-async")
          .contentType(MediaType.APPLICATION_JSON)
          .body(requestBody)
          .retrieve()
          .onStatus(
              HttpStatusCode::isError,
              (request, rawResponse) -> {
                String body;
                try {
                  body = new String(rawResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception readEx) {
                  body = "(body read error)";
                }
                log.warn(
                    "[ExamForgeFastApiClient] generateAsync() - FastAPI error | status: {}, body: {}",
                    rawResponse.getStatusCode(),
                    body);
                throw new CustomException(ExamForgeGradingErrorCode.FASTAPI_GENERATION_FAILED);
              })
          .toBodilessEntity(); // 202 응답은 본문이 없어 헤더만 읽는다

      log.info(
          "[ExamForgeFastApiClient] generateAsync() - END (trigger sent) | attemptId: {}",
          attemptId);
    } catch (CustomException e) {
      throw e;
    } catch (RestClientException e) {
      log.warn(
          "[ExamForgeFastApiClient] generateAsync() - network error | attemptId: {}", attemptId, e);
      throw new CustomException(ExamForgeGradingErrorCode.FASTAPI_GENERATION_FAILED);
    }
  }

  /**
   * 난이도 입력을 FastAPI 계약값(easy/medium/hard)으로 정규화한다.
   *
   * <p>FastAPI difficulty 필드는 max_length=20이라 임의 장문 입력이 422로 거부되어 attempt가 FAILED로
   * 빠질 수 있다. FastAPI 측 미지정/오타 폴백(medium)과 같은 규칙을 Spring 경계에서 먼저 적용해
   * 검증 오류가 나지 않게 한다.
   */
  private static String normalizeDifficulty(String difficulty) {
    if (difficulty == null || difficulty.isBlank()) {
      return "medium";
    }
    String normalized = difficulty.trim().toLowerCase();
    return switch (normalized) {
      case "easy", "medium", "hard" -> normalized;
      default -> "medium";
    };
  }

  /**
   * FastAPI /api/mock-exams/analyze 엔드포인트를 호출해 AI 총평(약점 진단)을 생성한다.
   *
   * <p>분석은 채점의 부가 기능이라 연결·타임아웃·4xx/5xx·역직렬화 실패가 채점 흐름을 깨지 않게
   * 모든 예외를 잡아 로그를 남기고 {@code null}을 반환한다.
   *
   * @return 분석 응답 (analysis + weakTopics), 실패 시 {@code null}
   */
  public FastApiExamAnalysisResponse analyzeMockExam(FastApiExamAnalysisRequest request) {
    log.info(
        "[ExamForgeFastApiClient] analyzeMockExam() - START | examId: {}, 문항수: {}",
        request.examId(),
        request.results() != null ? request.results().size() : 0);
    try {
      FastApiExamAnalysisResponse response =
          examForgeRestClient
              .post()
              .uri("/api/mock-exams/analyze")
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(FastApiExamAnalysisResponse.class);

      if (response == null || response.analysis() == null || response.analysis().isBlank()) {
        log.warn(
            "[ExamForgeFastApiClient] analyzeMockExam() - 분석 응답이 비어 있음 | examId: {}",
            request.examId());
        return null;
      }
      log.info(
          "[ExamForgeFastApiClient] analyzeMockExam() - END | examId: {}, 총평 {}자, 약점주제 {}개",
          request.examId(),
          response.analysis().length(),
          response.weakTopics() != null ? response.weakTopics().size() : 0);
      return response;
    } catch (RestClientException e) {
      // 연결 실패/타임아웃/4xx/5xx는 채점 실패로 전파하지 않고 로그만 남긴 뒤 null을 반환한다
      log.error(
          "[ExamForgeFastApiClient] analyzeMockExam() - 분석 호출 실패, null로 진행 | examId: {}, error: {}",
          request.examId(),
          e.getMessage());
      return null;
    } catch (RuntimeException e) {
      // 역직렬화 등 예기치 못한 런타임 오류도 채점은 막지 않는다
      log.error(
          "[ExamForgeFastApiClient] analyzeMockExam() - 예기치 못한 오류, null로 진행 | examId: {}, error: {}",
          request.examId(),
          e.getMessage());
      return null;
    }
  }

  private boolean containsAnswer(List<SubmittedAnswer> answers, String questionId) {
    return answers.stream().anyMatch(answer -> questionId.equals(answer.questionId()));
  }

  private QuestionGrade toGrade(FastApiQuestionGradeResponse response) {
    if (response == null
        || response.questionId() == null
        || response.templateId() == null
        || response.score() == null
        || response.maxScore() == null
        || response.gradingMode() == null
        || response.feedback() == null
        || response.rubricBreakdown() == null) {
      throw new CustomException(ExamForgeGradingErrorCode.FASTAPI_GRADING_FAILED);
    }
    return new QuestionGrade(
        response.questionId(),
        response.templateId(),
        response.score(),
        response.maxScore(),
        response.correct(),
        parseGradingMode(response.gradingMode()),
        response.feedback(),
        response.rubricBreakdown().stream()
            .map(
                item ->
                    new RubricCriterion(
                        item.criterion(), item.score(), item.maxScore(), item.reason()))
            .toList(),
        response.confidence(),
        response.needsManualReview());
  }

  private GradingMode parseGradingMode(String value) {
    try {
      return GradingMode.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new CustomException(ExamForgeGradingErrorCode.FASTAPI_GRADING_FAILED);
    }
  }
}
