/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion;
import com.swez.backend.domain.examgrading.entity.ExamGradeResult;
import com.swez.backend.domain.examgrading.entity.ExamQuestionGrade;
import com.swez.backend.domain.examgrading.enums.AttemptStatus;
import com.swez.backend.domain.examgrading.enums.GradingMode;
import com.swez.backend.domain.examgrading.enums.GradingStatus;
import com.swez.backend.domain.examgrading.mapper.ExamGradingPersistenceMapper;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.repository.ExamAttemptQuestionRepository;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;
import com.swez.backend.domain.examgrading.repository.ExamGradeResultRepository;
import com.swez.backend.domain.examgrading.repository.ExamQuestionGradeRepository;
import com.swez.backend.domain.examgrading.repository.ExamSubmittedAnswerRepository;

/**
 * ExamAttemptRepositoryJpaAdapter.findGradeResult의 templateId 주입 단위 테스트.
 *
 * <p>불변식: result를 조립할 때 각 QuestionGrade.templateId가 exam_attempt_question.template_id 값과 일치해야 한다
 * (non-null).
 *
 * <p>검증 케이스:
 *
 * <ul>
 *   <li>객관식(MCQ) 2문항: templateId non-null 단언
 *   <li>서술형(ko_descriptive) 포함 혼합: 서술형도 templateId non-null 단언
 *   <li>exam_question_grade에 없는 questionId: null 허용 (스냅샷 미매칭)
 *   <li>채점 결과 없음: Optional.empty() 반환
 * </ul>
 */
class ExamAttemptRepositoryJpaAdapterFindGradeResultTest {

  // JPA 리포지토리 Mock
  private ExamAttemptRepository attemptRepository;
  private ExamAttemptQuestionRepository questionRepository;
  private ExamGradeResultRepository gradeResultRepository;
  private ExamQuestionGradeRepository questionGradeRepository;
  private ExamSubmittedAnswerRepository submittedAnswerRepository;

  private ExamAttemptRepositoryJpaAdapter adapter;

  private static final String ATTEMPT_ID = "attempt-test-001";

  @BeforeEach
  void setUp() {
    attemptRepository = mock(ExamAttemptRepository.class);
    questionRepository = mock(ExamAttemptQuestionRepository.class);
    gradeResultRepository = mock(ExamGradeResultRepository.class);
    questionGradeRepository = mock(ExamQuestionGradeRepository.class);
    submittedAnswerRepository = mock(ExamSubmittedAnswerRepository.class);

    ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    ExamGradingPersistenceMapper persistenceMapper = new ExamGradingPersistenceMapper(objectMapper);

    adapter =
        new ExamAttemptRepositoryJpaAdapter(
            attemptRepository,
            questionRepository,
            gradeResultRepository,
            questionGradeRepository,
            submittedAnswerRepository,
            persistenceMapper,
            objectMapper);
  }

  @Test
  @DisplayName("MCQ 2문항 채점 결과 — templateId가 스냅샷 값과 일치 (non-null)")
  void findGradeResult_mcqTwoQuestions_templateIdNonNull() {
    // exam_grade_result Mock
    ExamGradeResult gradeResultEntity = buildGradeResultEntity(ATTEMPT_ID, "exam-mcq-001");
    when(gradeResultRepository.findByAttemptId(ATTEMPT_ID))
        .thenReturn(Optional.of(gradeResultEntity));

    // exam_question_grade Mock: MCQ 2문항
    ExamQuestionGrade grade1 = buildQuestionGradeEntity(ATTEMPT_ID, "q-mcq-1");
    ExamQuestionGrade grade2 = buildQuestionGradeEntity(ATTEMPT_ID, "q-mcq-2");
    when(questionGradeRepository.findByAttemptAttemptId(ATTEMPT_ID))
        .thenReturn(List.of(grade1, grade2));

    // exam_attempt_question Mock: templateId 포함
    ExamAttemptQuestion snapshot1 =
        buildSnapshotQuestion(ATTEMPT_ID, "q-mcq-1", "ko_multiple_choice_5", 1);
    ExamAttemptQuestion snapshot2 =
        buildSnapshotQuestion(ATTEMPT_ID, "q-mcq-2", "ko_multiple_choice_4", 2);
    when(questionRepository.findByAttemptAttemptIdOrderByDisplayOrderAsc(ATTEMPT_ID))
        .thenReturn(List.of(snapshot1, snapshot2));

    // 실행
    Optional<com.swez.backend.domain.examgrading.model.ExamGradeResult> result =
        adapter.findGradeResult(ATTEMPT_ID);

    // 검증
    assertThat(result).isPresent();
    List<QuestionGrade> grades = result.get().results();
    assertThat(grades).hasSize(2);

    // templateId non-null + 스냅샷 값 일치 단언
    QuestionGrade qg1 =
        grades.stream().filter(g -> "q-mcq-1".equals(g.questionId())).findFirst().orElseThrow();
    QuestionGrade qg2 =
        grades.stream().filter(g -> "q-mcq-2".equals(g.questionId())).findFirst().orElseThrow();

    assertThat(qg1.templateId())
        .as("q-mcq-1 templateId는 스냅샷 값 ko_multiple_choice_5 이어야 한다")
        .isEqualTo("ko_multiple_choice_5");
    assertThat(qg2.templateId())
        .as("q-mcq-2 templateId는 스냅샷 값 ko_multiple_choice_4 이어야 한다")
        .isEqualTo("ko_multiple_choice_4");
  }

  @Test
  @DisplayName("서술형(ko_descriptive) 포함 혼합 3문항 — 서술형도 templateId non-null")
  void findGradeResult_mixedWithDescriptive_descriptiveTemplateIdNonNull() {
    // exam_grade_result Mock
    ExamGradeResult gradeResultEntity = buildGradeResultEntity(ATTEMPT_ID, "exam-mixed-001");
    when(gradeResultRepository.findByAttemptId(ATTEMPT_ID))
        .thenReturn(Optional.of(gradeResultEntity));

    // exam_question_grade Mock: MCQ 1 + 서술형 1 + 빈칸 1
    ExamQuestionGrade gradeChoice = buildQuestionGradeEntity(ATTEMPT_ID, "q-choice-1");
    ExamQuestionGrade gradeDesc = buildQuestionGradeEntity(ATTEMPT_ID, "q-desc-1");
    ExamQuestionGrade gradeBlank = buildQuestionGradeEntity(ATTEMPT_ID, "q-blank-1");
    when(questionGradeRepository.findByAttemptAttemptId(ATTEMPT_ID))
        .thenReturn(List.of(gradeChoice, gradeDesc, gradeBlank));

    // exam_attempt_question Mock: 유형별 templateId
    ExamAttemptQuestion snapshotChoice =
        buildSnapshotQuestion(ATTEMPT_ID, "q-choice-1", "ko_multiple_choice_5", 1);
    ExamAttemptQuestion snapshotDesc =
        buildSnapshotQuestion(ATTEMPT_ID, "q-desc-1", "ko_descriptive", 2);
    ExamAttemptQuestion snapshotBlank =
        buildSnapshotQuestion(ATTEMPT_ID, "q-blank-1", "ko_fill_blank", 3);
    when(questionRepository.findByAttemptAttemptIdOrderByDisplayOrderAsc(ATTEMPT_ID))
        .thenReturn(List.of(snapshotChoice, snapshotDesc, snapshotBlank));

    // 실행
    Optional<com.swez.backend.domain.examgrading.model.ExamGradeResult> result =
        adapter.findGradeResult(ATTEMPT_ID);

    // 검증
    assertThat(result).isPresent();
    List<QuestionGrade> grades = result.get().results();
    assertThat(grades).hasSize(3);

    // 서술형 문항 templateId non-null 단언
    QuestionGrade descGrade =
        grades.stream().filter(g -> "q-desc-1".equals(g.questionId())).findFirst().orElseThrow();
    assertThat(descGrade.templateId())
        .as("서술형 문항 templateId는 ko_descriptive 이어야 한다 — null이면 프론트 크래시 발생")
        .isEqualTo("ko_descriptive");

    // 나머지 두 문항도 templateId non-null 단언
    assertThat(grades)
        .allSatisfy(
            g -> assertThat(g.templateId()).as("모든 문항 templateId가 non-null이어야 한다").isNotNull());
  }

  @Test
  @DisplayName("스냅샷에 없는 questionId — QuestionGrade.templateId null (스냅샷 누락 경계 케이스)")
  void findGradeResult_questionNotInSnapshot_templateIdIsNull() {
    // exam_grade_result Mock
    ExamGradeResult gradeResultEntity = buildGradeResultEntity(ATTEMPT_ID, "exam-orphan-001");
    when(gradeResultRepository.findByAttemptId(ATTEMPT_ID))
        .thenReturn(Optional.of(gradeResultEntity));

    // exam_question_grade에는 q-orphan 존재
    ExamQuestionGrade gradeOrphan = buildQuestionGradeEntity(ATTEMPT_ID, "q-orphan");
    when(questionGradeRepository.findByAttemptAttemptId(ATTEMPT_ID))
        .thenReturn(List.of(gradeOrphan));

    // exam_attempt_question 스냅샷에는 q-orphan 없음
    when(questionRepository.findByAttemptAttemptIdOrderByDisplayOrderAsc(ATTEMPT_ID))
        .thenReturn(List.of()); // 비어 있음

    // 실행
    Optional<com.swez.backend.domain.examgrading.model.ExamGradeResult> result =
        adapter.findGradeResult(ATTEMPT_ID);

    // 검증: 스냅샷이 없으면 예외 대신 경고 로그를 남기고 null을 허용한다
    assertThat(result).isPresent();
    QuestionGrade orphanGrade = result.get().results().get(0);
    assertThat(orphanGrade.questionId()).isEqualTo("q-orphan");
    assertThat(orphanGrade.templateId())
        .as("스냅샷에 없는 문항은 templateId=null이 된다 (스냅샷 데이터 무결성 문제)")
        .isNull();
  }

  @Test
  @DisplayName("채점 결과 없음 — Optional.empty() 반환")
  void findGradeResult_noGradeResult_returnsEmpty() {
    when(gradeResultRepository.findByAttemptId(ATTEMPT_ID)).thenReturn(Optional.empty());

    Optional<com.swez.backend.domain.examgrading.model.ExamGradeResult> result =
        adapter.findGradeResult(ATTEMPT_ID);

    assertThat(result).isEmpty();
  }

  // 테스트 픽스처 빌더

  /** ExamGradeResult 엔티티 픽스처 */
  private ExamGradeResult buildGradeResultEntity(String attemptId, String examId) {
    ExamAttempt attempt =
        ExamAttempt.builder()
            .attemptId(attemptId)
            .examId(examId)
            .status(AttemptStatus.GRADED)
            .passPercentage(BigDecimal.valueOf(60))
            .answerKeySeal("test-seal")
            .createdAt(OffsetDateTime.now())
            .build();

    return ExamGradeResult.builder()
        .attempt(attempt)
        .totalScore(BigDecimal.valueOf(8))
        .maxScore(BigDecimal.valueOf(10))
        .percentage(new BigDecimal("80.00"))
        .passed(true)
        .gradingStatus(GradingStatus.GRADED)
        .gradedAt(OffsetDateTime.now())
        .build();
  }

  /** ExamQuestionGrade 엔티티 픽스처. rubricBreakdownJson은 null(객관식 기본값)로 설정한다. */
  private ExamQuestionGrade buildQuestionGradeEntity(String attemptId, String questionId) {
    ExamAttempt attempt =
        ExamAttempt.builder()
            .attemptId(attemptId)
            .examId("exam-test")
            .status(AttemptStatus.GRADED)
            .passPercentage(BigDecimal.valueOf(60))
            .answerKeySeal("test-seal")
            .createdAt(OffsetDateTime.now())
            .build();

    return ExamQuestionGrade.builder()
        .attempt(attempt)
        .questionId(questionId)
        .score(BigDecimal.valueOf(2))
        .maxScore(BigDecimal.valueOf(2))
        .gradingMode(GradingMode.DETERMINISTIC)
        .feedback("정답입니다.")
        .rubricBreakdownJson(null) // 객관식은 루브릭이 없다
        .confidence(BigDecimal.ONE)
        .needsManualReview(false)
        .build();
  }

  /** templateId를 포함한 ExamAttemptQuestion 스냅샷 픽스처. 프론트는 templateId로 문항을 렌더링한다. */
  private ExamAttemptQuestion buildSnapshotQuestion(
      String attemptId, String questionId, String templateId, int displayOrder) {
    ExamAttempt attempt =
        ExamAttempt.builder()
            .attemptId(attemptId)
            .examId("exam-test")
            .status(AttemptStatus.GRADED)
            .passPercentage(BigDecimal.valueOf(60))
            .answerKeySeal("test-seal")
            .createdAt(OffsetDateTime.now())
            .build();

    return ExamAttemptQuestion.builder()
        .attempt(attempt)
        .questionId(questionId)
        .templateId(templateId)
        .displayOrder(displayOrder)
        .stem("테스트 문항 본문 — " + questionId)
        .points(BigDecimal.valueOf(2))
        .build();
  }
}
