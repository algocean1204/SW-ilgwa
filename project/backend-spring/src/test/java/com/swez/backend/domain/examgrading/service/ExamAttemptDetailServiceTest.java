/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.examgrading.dto.response.AttemptDetailResponse;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion;
import com.swez.backend.domain.examgrading.enums.AttemptStatus;
import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.mapper.ExamGradingPersistenceMapper;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.QuestionReviewData;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;
import com.swez.backend.domain.examgrading.port.ExamAttemptRepositoryPort;
import com.swez.backend.global.exception.CustomException;

/**
 * 응시 상세 조회 서비스 단위 테스트.
 *
 * <p>시드 attempt(객관식 MCQ 2문항)를 사용하여 다음을 검증한다:
 *
 * <ul>
 *   <li>정상 조회: 민감 필드(correctAnswer·explanation·is_correct)가 응답에 없음
 *   <li>소유권 불일치: CustomException 발생
 *   <li>문항 없는 응시: 빈 목록 반환
 * </ul>
 */
class ExamAttemptDetailServiceTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  @DisplayName("정상 조회 — 민감 필드 없이 문항 목록 반환")
  void getAttemptDetailReturnsSafeQuestionsOnly() {
    // 시드 attempt: 객관식 2문항, 사용자 user-1
    FakeDetailRepository repo = new FakeDetailRepository(seedAttempt(), seedQuestions());
    ExamAttemptDetailService service = detailService(repo);

    AttemptDetailResponse response = service.getAttemptDetail("attempt-seed", "user-1");

    assertThat(response.attemptId()).isEqualTo("attempt-seed");
    assertThat(response.examId()).isEqualTo("exam-seed");
    assertThat(response.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
    assertThat(response.questions()).hasSize(2);
    // 문항 본문 존재 확인
    assertThat(response.questions().get(0).stem()).isNotBlank();
    // 보기 레이블만 있고 is_correct 필드가 없는지는 직렬화 단계에서 GradingLeakGuard가 확인한다
    assertThat(response.questions().get(0).options())
        .allSatisfy(
            opt -> {
              assertThat(opt.label()).isNotNull();
              assertThat(opt.text()).isNotNull();
            });
  }

  @Test
  @DisplayName("소유권 불일치 — CustomException 발생")
  void getAttemptDetailThrowsWhenOwnerMismatch() {
    FakeDetailRepository repo = new FakeDetailRepository(seedAttempt(), seedQuestions());
    ExamAttemptDetailService service = detailService(repo);

    // user-2로 요청 → attempt는 user-1 소유
    assertThatThrownBy(() -> service.getAttemptDetail("attempt-seed", "user-2"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));
  }

  @Test
  @DisplayName("문항 없는 응시 — 빈 문항 목록 반환")
  void getAttemptDetailWithNoQuestionsReturnsEmptyList() {
    FakeDetailRepository repo = new FakeDetailRepository(seedAttempt(), List.of());
    ExamAttemptDetailService service = detailService(repo);

    AttemptDetailResponse response = service.getAttemptDetail("attempt-seed", "user-1");
    assertThat(response.questions()).isEmpty();
  }

  @Test
  @DisplayName("빈칸채우기 문항 — blankCount가 채워지고 blank_answers는 누출되지 않는다")
  void getAttemptDetail_fillBlank_blankCountPopulated_blankAnswersNotLeaked() {
    // grading_metadata_json에 blank_positions 2개, blank_answers(정답)가 있는 경우
    // FastAPI가 snake_case 키로 보내고 DB에도 snake_case로 저장되므로 @JsonProperty 매핑이 필요하다
    String metaJson = "{\"blank_positions\":[0,5],\"blank_answers\":[\"캡슐화\",\"은닉\"]}";
    ExamAttemptQuestion q =
        ExamAttemptQuestion.builder()
            .attempt(seedAttempt())
            .questionId("q-blank")
            .templateId("ko_fill_blank")
            .displayOrder(1)
            .stem("___ 은/는 객체지향에서 ___ 을/를 구현한다.")
            .points(BigDecimal.valueOf(3))
            .gradingMetadataJson(metaJson)
            .correctAnswer("캡슐화, 은닉")
            .explanation("서버 전용")
            .build();

    FakeDetailRepository repo = new FakeDetailRepository(seedAttempt(), List.of(q));
    ExamAttemptDetailService service = detailService(repo);

    AttemptDetailResponse response = service.getAttemptDetail("attempt-seed", "user-1");

    assertThat(response.questions()).hasSize(1);
    var question = response.questions().get(0);
    // 빈칸 수만 전달하고 blank_answers(정답)는 넣지 않는다
    assertThat(question.blankCount()).isEqualTo(2);
    // 직렬화 결과에 blank_answers 필드가 없는지는 GradingLeakGuard가 확인한다
    // 옵션, 순서 항목, 연결 항목은 null
    assertThat(question.options()).isNull();
    assertThat(question.orderingItems()).isNull();
    assertThat(question.matchingLeftItems()).isNull();
  }

  @Test
  @DisplayName("순서배열 문항 — orderingItems가 채워지고 correct_ordering은 누출되지 않는다")
  void getAttemptDetail_ordering_orderingItemsPopulated_correctOrderingNotLeaked() {
    // grading_metadata_json에 ordering_items와 correct_ordering(정답)이 있는 경우
    // FastAPI가 snake_case 키로 보내고 DB에도 snake_case로 저장되므로 @JsonProperty 매핑이 필요하다
    String metaJson =
        "{\"ordering_items\":[\"단계C\",\"단계A\",\"단계D\",\"단계B\"],"
            + "\"correct_ordering\":[\"단계A\",\"단계B\",\"단계C\",\"단계D\"]}";
    ExamAttemptQuestion q =
        ExamAttemptQuestion.builder()
            .attempt(seedAttempt())
            .questionId("q-order")
            .templateId("ko_ordering")
            .displayOrder(1)
            .stem("다음을 올바른 순서로 배열하시오.")
            .points(BigDecimal.valueOf(4))
            .gradingMetadataJson(metaJson)
            .correctAnswer("단계A -> 단계B -> 단계C -> 단계D")
            .explanation("서버 전용")
            .build();

    FakeDetailRepository repo = new FakeDetailRepository(seedAttempt(), List.of(q));
    ExamAttemptDetailService service = detailService(repo);

    AttemptDetailResponse response = service.getAttemptDetail("attempt-seed", "user-1");

    var question = response.questions().get(0);
    // 섞인 항목 목록만 전달 (정답 순서인 correct_ordering은 포함하지 않음)
    assertThat(question.orderingItems()).containsExactlyInAnyOrder("단계C", "단계A", "단계D", "단계B");
    assertThat(question.blankCount()).isNull();
    assertThat(question.matchingLeftItems()).isNull();
  }

  @Test
  @DisplayName("연결형 문항 — matchingLeftItems/matchingRightItems가 채워지고 정답 매핑은 누출되지 않는다")
  void getAttemptDetail_matching_leftRightPopulated_answerMappingNotLeaked() {
    // grading_metadata_json에 matching_pairs가 있는 경우
    // FastAPI가 snake_case 키로 보내고 DB에도 snake_case로 저장되므로 @JsonProperty 매핑이 필요하다
    String metaJson =
        "{\"matching_pairs\":["
            + "{\"left\":\"스택\",\"right\":\"LIFO\"},"
            + "{\"left\":\"큐\",\"right\":\"FIFO\"}"
            + "]}";
    ExamAttemptQuestion q =
        ExamAttemptQuestion.builder()
            .attempt(seedAttempt())
            .questionId("q-match")
            .templateId("ko_matching")
            .displayOrder(1)
            .stem("좌측과 우측을 올바르게 연결하시오.")
            .points(BigDecimal.valueOf(2))
            .gradingMetadataJson(metaJson)
            .correctAnswer("스택-LIFO, 큐-FIFO")
            .explanation("서버 전용")
            .build();

    FakeDetailRepository repo = new FakeDetailRepository(seedAttempt(), List.of(q));
    ExamAttemptDetailService service = detailService(repo);

    AttemptDetailResponse response = service.getAttemptDetail("attempt-seed", "user-1");

    var question = response.questions().get(0);
    // 좌항 목록 (표시 순서 유지)
    assertThat(question.matchingLeftItems()).containsExactly("스택", "큐");
    // 우항 목록 (섞인 상태로 전달하고 정답 매핑은 넣지 않는다)
    assertThat(question.matchingRightItems()).containsExactlyInAnyOrder("LIFO", "FIFO");
    assertThat(question.blankCount()).isNull();
    assertThat(question.orderingItems()).isNull();
  }

  // 시드 데이터 빌더

  private ExamAttempt seedAttempt() {
    // Lombok @Builder와 @AllArgsConstructor로 리플렉션 없이 엔티티를 만든다
    return ExamAttempt.builder()
        .attemptId("attempt-seed")
        .examId("exam-seed")
        .user(seedUser("user-1"))
        .status(AttemptStatus.IN_PROGRESS)
        .passPercentage(BigDecimal.valueOf(60))
        .answerKeySeal("sealed-key")
        .createdAt(OffsetDateTime.now())
        .build();
  }

  private User seedUser(String userId) {
    // User 엔티티는 @AllArgsConstructor를 통해 빌더로 생성
    return User.builder().id(userId).email(userId + "@test.com").build();
  }

  private List<ExamAttemptQuestion> seedQuestions() {
    // 객관식 2문항. options_json에 is_correct가 들어 있다(DB 저장 형태)
    String optionsJson =
        "[{\"label\":\"A\",\"text\":\"인증 실패\",\"is_correct\":false},"
            + "{\"label\":\"B\",\"text\":\"요청 성공\",\"is_correct\":true}]";
    ExamAttemptQuestion q1 =
        ExamAttemptQuestion.builder()
            .attempt(seedAttempt())
            .questionId("q-1")
            .templateId("ko_multiple_choice_4")
            .displayOrder(1)
            .stem("HTTP 200 상태 코드의 의미는?")
            .points(BigDecimal.valueOf(2))
            .optionsJson(optionsJson)
            // 민감 필드. DB에는 있지만 DTO로 바꿀 때는 넣지 않는다
            .correctAnswer("B")
            .explanation("서버 전용 해설")
            .sourceReference("서버 전용 출처")
            .build();
    ExamAttemptQuestion q2 =
        ExamAttemptQuestion.builder()
            .attempt(seedAttempt())
            .questionId("q-2")
            .templateId("ko_multiple_choice_4")
            .displayOrder(2)
            .stem("HTTP 404 상태 코드의 의미는?")
            .points(BigDecimal.valueOf(2))
            .optionsJson(optionsJson)
            .correctAnswer("A")
            .explanation("서버 전용 해설2")
            .sourceReference("서버 전용 출처2")
            .build();
    return List.of(q1, q2);
  }

  private ExamAttemptDetailService detailService(ExamAttemptRepositoryPort repo) {
    return new ExamAttemptDetailService(
        repo, new GradingLeakGuard(objectMapper), new ExamGradingPersistenceMapper(objectMapper));
  }

  // Fake 저장소 구현체

  private static final class FakeDetailRepository implements ExamAttemptRepositoryPort {
    // AI 총평 포트 메서드 (테스트용 인메모리 구현)
    private String analysisJson;

    @Override
    public void saveAnalysisJson(String attemptId, String json) {
      this.analysisJson = json;
    }

    @Override
    public java.util.Optional<String> findAnalysisJson(String attemptId) {
      return java.util.Optional.ofNullable(analysisJson);
    }


    private final ExamAttempt attempt;
    private final List<ExamAttemptQuestion> questions;

    FakeDetailRepository(ExamAttempt attempt, List<ExamAttemptQuestion> questions) {
      this.attempt = attempt;
      this.questions = questions;
    }

    @Override
    public Optional<ExamAttemptSnapshot> findGradingSnapshot(String attemptId) {
      return Optional.empty();
    }

    @Override
    public void saveGradingResult(ExamGradeResult result) {}

    @Override
    public Optional<ExamAttempt> findAttemptByOwner(String attemptId, String userId) {
      if (attempt.getAttemptId().equals(attemptId)
          && attempt.getUser() != null
          && userId.equals(attempt.getUser().getId())) {
        return Optional.of(attempt);
      }
      return Optional.empty();
    }

    @Override
    public List<ExamAttemptQuestion> findQuestionsByAttemptId(String attemptId) {
      return attempt.getAttemptId().equals(attemptId) ? questions : List.of();
    }

    @Override
    public void saveSubmittedAnswers(String attemptId, List<SubmittedAnswer> answers) {}

    @Override
    public Optional<ExamGradeResult> findGradeResult(String attemptId) {
      return Optional.empty();
    }

    @Override
    public List<QuestionReviewData> findReviewData(String attemptId) {
      // 응시 상세 경로 테스트에서는 복습 데이터를 사용하지 않으므로 빈 목록 반환
      return List.of();
    }
  }
}
