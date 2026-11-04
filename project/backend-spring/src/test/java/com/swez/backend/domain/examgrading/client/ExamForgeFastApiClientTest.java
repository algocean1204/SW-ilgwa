/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;
import com.swez.backend.global.exception.CustomException;

/**
 * ExamForgeFastApiClient 단위 테스트.
 *
 * <p>MockRestServiceServer를 사용해 실제 FastAPI 없이 HTTP 계층만 검증한다. FastAPI가 알 수 없는 grading_mode 값을 반환할 때
 * CustomException으로 래핑되는지 확인한다.
 */
class ExamForgeFastApiClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void wrapsInvalidFastApiResponseContractAsCustomException() {
    // MockRestServiceServer는 RestClient.Builder에 바인딩 후 builder.build()로 RestClient를 생성해야 한다
    RestClient.Builder builder =
        RestClient.builder().baseUrl("http://fastapi").defaultHeader("X-API-Key", "test-key");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://fastapi/api/exam-forge/grade-submission"))
        .andExpect(header("X-API-Key", "test-key"))
        .andRespond(
            withSuccess(
                """
                {
                  "attempt_id": "attempt-1",
                  "exam_id": "exam-1",
                  "total_score": 1,
                  "max_score": 5,
                  "percentage": 20,
                  "passed": false,
                  "grading_status": "graded",
                  "graded_at": "2026-05-26T00:00:00Z",
                  "results": [
                    {
                      "question_id": "q-essay",
                      "template_id": "ko_essay",
                      "score": 1,
                      "max_score": 5,
                      "is_correct": false,
                      "grading_mode": "vendor_drift",
                      "feedback": "부분 충족",
                      "rubric_breakdown": [],
                      "confidence": 0.8,
                      "needs_manual_review": false
                    }
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    // 생성자에는 MockRestServiceServer를 바인딩한 builder.build() 결과를 넘긴다
    ExamForgeFastApiClient client = new ExamForgeFastApiClient(builder.build());

    // "vendor_drift"는 GradingMode enum에 없으므로 CustomException이 발생해야 한다
    assertThatThrownBy(
            () ->
                client.gradeRubricQuestions(
                    snapshot(),
                    List.of(new SubmittedAnswer("q-essay", objectMapper.valueToTree("답안")))))
        .isInstanceOf(CustomException.class);
    server.verify();
  }

  @Test
  void generateAsyncSendsSnakeCaseContractAndNormalizesDifficulty() {
    // FastAPI MockGenerateAsyncRequest 계약: snake_case 키와 difficulty 정규화(미지원 값 → medium).
    // FastAPI difficulty 필드는 max_length=20이라 긴 값은 422로 거부되므로 Spring에서 먼저 걸러야 한다
    RestClient.Builder builder =
        RestClient.builder().baseUrl("http://fastapi").defaultHeader("X-API-Key", "test-key");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://fastapi/api/exam-forge/mock/generate-async"))
        .andExpect(header("X-API-Key", "test-key"))
        .andExpect(
            org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                .json(
                    """
                    {
                      "attempt_id": "attempt-1",
                      "course_id": "course-1",
                      "subject": "운영체제",
                      "question_count": 20,
                      "question_types": [],
                      "difficulty": "medium",
                      "pass_percentage": 60
                    }
                    """))
        .andRespond(withSuccess("{\"accepted\":true}", MediaType.APPLICATION_JSON));

    ExamForgeFastApiClient client = new ExamForgeFastApiClient(builder.build());

    // questionTypes=null → 빈 목록, difficulty 미지원 장문 → medium 정규화가 함께 검증된다
    client.generateAsync(
        "attempt-1",
        "course-1",
        "운영체제",
        null,
        20,
        null,
        "아주아주 어려운 난이도로 만들어 주세요 (계약 외 임의 입력)",
        BigDecimal.valueOf(60));
    server.verify();
  }

  @Test
  void generateAsyncKeepsSupportedDifficultyCaseInsensitively() {
    // 대소문자/공백 차이는 정규화해 전달한다 ("HARD " → "hard")
    RestClient.Builder builder =
        RestClient.builder().baseUrl("http://fastapi").defaultHeader("X-API-Key", "test-key");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://fastapi/api/exam-forge/mock/generate-async"))
        .andExpect(
            org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath(
                    "$.difficulty")
                .value("hard"))
        .andRespond(withSuccess("{\"accepted\":true}", MediaType.APPLICATION_JSON));

    ExamForgeFastApiClient client = new ExamForgeFastApiClient(builder.build());

    client.generateAsync(
        "attempt-1",
        "course-1",
        "운영체제",
        null,
        20,
        List.of("ko_multiple_choice_5"),
        "HARD ",
        BigDecimal.valueOf(60));
    server.verify();
  }

  private ExamAttemptSnapshot snapshot() {
    return new ExamAttemptSnapshot(
        "attempt-1",
        "exam-1",
        "sealed-answer-key",
        List.of(
            new ExamQuestion(
                "q-essay",
                "draft-1",
                "ko_essay",
                "topic",
                5,
                "evaluate",
                "stem",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                "OAuthAuthorizationCodeFlow",
                "서버 전용 해설입니다.",
                "server-only-source-reference",
                BigDecimal.valueOf(5),
                "server-only-distractor-rationale")),
        BigDecimal.valueOf(60));
  }
}
