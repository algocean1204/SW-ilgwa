/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.adapter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion;
import com.swez.backend.domain.examgrading.entity.ExamGradeResult;
import com.swez.backend.domain.examgrading.entity.ExamQuestionGrade;
import com.swez.backend.domain.examgrading.entity.ExamSubmittedAnswer;
import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.mapper.ExamGradingPersistenceMapper;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.QuestionReviewData;
import com.swez.backend.domain.examgrading.model.RubricCriterion;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;
import com.swez.backend.domain.examgrading.port.ExamAttemptRepositoryPort;
import com.swez.backend.domain.examgrading.repository.ExamAttemptQuestionRepository;
import com.swez.backend.domain.examgrading.repository.ExamAttemptRepository;
import com.swez.backend.domain.examgrading.repository.ExamGradeResultRepository;
import com.swez.backend.domain.examgrading.repository.ExamQuestionGradeRepository;
import com.swez.backend.domain.examgrading.repository.ExamSubmittedAnswerRepository;
import com.swez.backend.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ExamAttemptRepositoryPort의 JPA 구현체.
 *
 * <p>포트 연산별 DB 처리:
 *
 * <ul>
 *   <li>{@link #findGradingSnapshot}: exam_attempt + exam_attempt_question을 조인하여 채점기가 필요로 하는 모든 서버
 *       전용 필드를 포함한 ExamAttemptSnapshot을 반환한다.
 *   <li>{@link #saveGradingResult}: ExamGradeResult(집계) + ExamQuestionGrade(문항별)를
 *       exam_grade_result / exam_question_grade 테이블에 영속하고 ExamAttempt 상태를 전환한다.
 *   <li>{@link #findAttemptByOwner}: userId 소유권을 검증하여 ExamAttempt를 반환한다.
 *   <li>{@link #findQuestionsByAttemptId}: 응시에 속한 문항 목록을 displayOrder 오름차순으로 반환한다.
 *   <li>{@link #saveSubmittedAnswers}: 제출 답안을 exam_submitted_answer에 저장하고 상태를 SUBMITTED로 전환한다.
 *   <li>{@link #findGradeResult}: 채점 완료 결과(집계 + 문항별)를 도메인 record로 반환한다.
 * </ul>
 *
 * <p>enum 변환(enums ↔ model 패키지)은 모두 ExamGradingPersistenceMapper를 경유한다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ExamAttemptRepositoryJpaAdapter implements ExamAttemptRepositoryPort {

  private final ExamAttemptRepository attemptRepository;
  private final ExamAttemptQuestionRepository questionRepository;
  private final ExamGradeResultRepository gradeResultRepository;
  private final ExamQuestionGradeRepository questionGradeRepository;
  private final ExamSubmittedAnswerRepository submittedAnswerRepository;
  private final ExamGradingPersistenceMapper persistenceMapper;
  private final ObjectMapper objectMapper;

  // 포트 구현: findGradingSnapshot

  /**
   * attemptId의 채점 스냅샷을 읽는다. 문항은 displayOrder 순서이고 correctAnswer·explanation·options·
   * matchingPairs·points 같은 채점 서버 전용 필드까지 담는다.
   *
   * @return 채점 스냅샷 Optional (응시가 없으면 empty)
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<ExamAttemptSnapshot> findGradingSnapshot(String attemptId) {
    // User·Course를 fetch join해 N+1을 막는다
    Optional<ExamAttempt> attemptOpt =
        attemptRepository.findWithUserAndCourseByAttemptId(attemptId);
    if (attemptOpt.isEmpty()) {
      log.debug("[ExamAttemptRepositoryJpaAdapter] 응시 미발견 | attemptId: {}", attemptId);
      return Optional.empty();
    }
    ExamAttempt attempt = attemptOpt.get();

    List<ExamAttemptQuestion> questionEntities =
        questionRepository.findByAttemptAttemptIdOrderByDisplayOrderAsc(attemptId);

    // 채점에 필요한 서버 전용 필드까지 모두 도메인 record로 옮긴다
    List<ExamQuestion> questions =
        questionEntities.stream().map(persistenceMapper::toExamQuestion).toList();

    ExamAttemptSnapshot snapshot =
        new ExamAttemptSnapshot(
            attempt.getAttemptId(),
            attempt.getExamId(),
            attempt.getAnswerKeySeal(),
            questions,
            attempt.getPassPercentage());

    log.debug(
        "[ExamAttemptRepositoryJpaAdapter] 스냅샷 로드 완료 | attemptId: {}, 문항수: {}",
        attemptId,
        questions.size());
    return Optional.of(snapshot);
  }

  // 포트 구현: saveGradingResult

  /**
   * 채점 결과를 exam_grade_result·exam_question_grade에 저장하고, GradingStatus에 따라 ExamAttempt를 GRADED 또는
   * MANUAL_REVIEW_REQUIRED로 바꾼다.
   *
   * <p>@Transactional 안에서 호출해야 한다. GradingLeakGuard 검사는 서비스 계층에서 이 메서드보다 먼저 끝나야 한다.
   *
   * @throws CustomException attempt_id에 해당하는 응시가 없으면 ATTEMPT_NOT_FOUND
   */
  @Override
  @Transactional
  public void saveGradingResult(com.swez.backend.domain.examgrading.model.ExamGradeResult result) {
    // 공유 PK 바인딩에 ExamAttempt 엔티티가 필요하다
    ExamAttempt attempt =
        attemptRepository
            .findById(result.attemptId())
            .orElseThrow(() -> new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));

    ExamGradeResult gradeResultEntity = persistenceMapper.toExamGradeResultEntity(result, attempt);
    gradeResultRepository.save(gradeResultEntity);

    List<ExamQuestionGrade> questionGradeEntities =
        buildQuestionGradeEntities(result.results(), attempt);
    questionGradeRepository.saveAll(questionGradeEntities);

    updateAttemptStatus(attempt, result.gradingStatus());

    log.info(
        "[ExamAttemptRepositoryJpaAdapter] 채점 결과 저장 완료 | attemptId: {}, 상태: {}, 문항수: {}",
        result.attemptId(),
        result.gradingStatus(),
        result.results().size());
  }

  // 포트 구현: findAttemptByOwner

  /**
   * userId 소유권을 검증하여 ExamAttempt를 반환한다. 해당 userId가 소유하지 않으면 Optional.empty()를 반환한다.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<ExamAttempt> findAttemptByOwner(String attemptId, String userId) {
    return attemptRepository
        .findWithUserAndCourseByAttemptId(attemptId)
        .filter(attempt -> attempt.getUser() != null && userId.equals(attempt.getUser().getId()));
  }

  /**
   * 비관적 쓰기 락으로 소유권 검증 후 응시 엔티티를 조회한다.
   *
   * <p>FOR UPDATE 행 락을 잡아 같은 attempt 동시 제출을 직렬화한다. 루트 행만 잠그므로(fetch join 없음) user는 LAZY로
   * 로딩되지만 같은 트랜잭션 안이라 지연 로딩이 동작한다. 소유자 불일치면 empty를 반환한다(상위에서 ATTEMPT_NOT_FOUND로 처리).
   * 반드시 활성 트랜잭션 안에서 호출해야 락이 의미를 갖는다.
   */
  @Override
  @Transactional
  public Optional<ExamAttempt> findAttemptByOwnerForUpdate(String attemptId, String userId) {
    return attemptRepository
        .findByAttemptIdForUpdate(attemptId)
        .filter(attempt -> attempt.getUser() != null && userId.equals(attempt.getUser().getId()));
  }

  // 포트 구현: findQuestionsByAttemptId

  /** 응시에 속한 문항 목록을 displayOrder 오름차순으로 조회한다. */
  @Override
  @Transactional(readOnly = true)
  public List<ExamAttemptQuestion> findQuestionsByAttemptId(String attemptId) {
    return questionRepository.findByAttemptAttemptIdOrderByDisplayOrderAsc(attemptId);
  }

  // 포트 구현: saveSubmittedAnswers

  /**
   * 제출 답안을 exam_submitted_answer 테이블에 일괄 저장하고 attempt 상태를 SUBMITTED로 전환한다.
   *
   * @throws CustomException attempt_id에 해당하는 응시가 없으면 ATTEMPT_NOT_FOUND
   */
  @Override
  @Transactional
  public void saveSubmittedAnswers(String attemptId, List<SubmittedAnswer> answers) {
    ExamAttempt attempt =
        attemptRepository
            .findById(attemptId)
            .orElseThrow(() -> new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));

    List<ExamSubmittedAnswer> entities = new ArrayList<>();
    for (SubmittedAnswer answer : answers) {
      entities.add(toSubmittedAnswerEntity(attempt, answer));
    }
    submittedAnswerRepository.saveAll(entities);

    attempt.markSubmitted(OffsetDateTime.now());
    log.info(
        "[ExamAttemptRepositoryJpaAdapter] 제출 답안 저장 완료 | attemptId: {}, 답안수: {}",
        attemptId,
        entities.size());
  }

  // 포트 구현: findGradeResult

  /**
   * 채점 완료된 응시의 결과(집계 + 문항별)를 도메인 record로 반환한다.
   *
   * <p>templateId는 exam_question_grade 테이블에 미저장이므로, exam_attempt_question을 조회하여 questionId →
   * templateId 매핑 후 각 QuestionGrade에 주입한다. 마이그레이션 없이 기존 테이블 조인으로 해결한다.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<com.swez.backend.domain.examgrading.model.ExamGradeResult> findGradeResult(
      String attemptId) {
    Optional<ExamGradeResult> gradeResultOpt = gradeResultRepository.findByAttemptId(attemptId);
    if (gradeResultOpt.isEmpty()) {
      return Optional.empty();
    }
    ExamGradeResult gradeResult = gradeResultOpt.get();

    List<ExamQuestionGrade> questionGrades =
        questionGradeRepository.findByAttemptAttemptId(attemptId);

    // exam_question_grade에 templateId 컬럼이 없어 exam_attempt_question 스냅샷에서 읽어 채운다
    java.util.Map<String, String> templateIdByQuestionId = buildTemplateIdMap(attemptId);

    List<QuestionGrade> grades =
        questionGrades.stream()
            .map(entity -> toQuestionGradeWithTemplateId(entity, templateIdByQuestionId))
            .toList();

    com.swez.backend.domain.examgrading.model.ExamGradeResult result =
        new com.swez.backend.domain.examgrading.model.ExamGradeResult(
            attemptId,
            gradeResult.getAttempt() != null ? gradeResult.getAttempt().getExamId() : null,
            gradeResult.getTotalScore(),
            gradeResult.getMaxScore(),
            gradeResult.getPercentage(),
            gradeResult.getPassed() != null && gradeResult.getPassed(),
            persistenceMapper.toModelGradingStatus(gradeResult.getGradingStatus()),
            gradeResult.getGradedAt(),
            grades);

    return Optional.of(result);
  }

  // 포트 구현: saveAnalysisJson / findAnalysisJson (AI 총평)

  /**
   * 채점 결과 행에 AI 총평 JSON을 부착한다.
   *
   * <p>분석은 채점의 부가 기능이므로 결과 행이 없으면 경고 로깅 후 무시한다(예외 미발생).
   */
  @Override
  @Transactional
  public void saveAnalysisJson(String attemptId, String analysisJson) {
    Optional<ExamGradeResult> gradeResultOpt = gradeResultRepository.findByAttemptId(attemptId);
    if (gradeResultOpt.isEmpty()) {
      log.warn(
          "[ExamAttemptRepositoryJpaAdapter] 총평 부착 대상 채점 결과 없음 — 무시 | attemptId: {}", attemptId);
      return;
    }
    gradeResultOpt.get().attachAnalysis(analysisJson);
    gradeResultRepository.save(gradeResultOpt.get());
    log.info("[ExamAttemptRepositoryJpaAdapter] AI 총평 저장 완료 | attemptId: {}", attemptId);
  }

  /** 저장된 AI 총평 JSON을 조회한다. 미생성/실패 시 empty. */
  @Override
  @Transactional(readOnly = true)
  public Optional<String> findAnalysisJson(String attemptId) {
    return gradeResultRepository
        .findByAttemptId(attemptId)
        .map(ExamGradeResult::getAnalysis)
        .filter(json -> json != null && !json.isBlank());
  }

  // 내부 헬퍼

  /** QuestionGrade 리스트를 ExamQuestionGrade 엔티티 리스트로 변환한다. null이거나 빈 리스트이면 빈 리스트를 반환한다. */
  private List<ExamQuestionGrade> buildQuestionGradeEntities(
      List<QuestionGrade> grades, ExamAttempt attempt) {
    if (grades == null || grades.isEmpty()) {
      return List.of();
    }
    return grades.stream()
        .map(grade -> persistenceMapper.toExamQuestionGradeEntity(grade, attempt))
        .toList();
  }

  /**
   * GradingStatus에 따라 ExamAttempt 상태를 전환한다.
   *
   * <ul>
   *   <li>GRADED → markGraded(gradedAt)
   *   <li>MANUAL_REVIEW_REQUIRED → markManualReviewRequired()
   * </ul>
   *
   * 신규 GradingStatus 상수 추가 시 반드시 이 분기도 업데이트해야 한다.
   */
  private void updateAttemptStatus(
      ExamAttempt attempt, com.swez.backend.domain.examgrading.model.GradingStatus gradingStatus) {
    switch (gradingStatus) {
      case GRADED -> attempt.markGraded(OffsetDateTime.now());
      case MANUAL_REVIEW_REQUIRED -> attempt.markManualReviewRequired();
        // 새 GradingStatus를 GRADED로 처리하지 않고 분기 누락을 경고 로그로 남긴다
      default -> log.warn(
          "[ExamAttemptRepositoryJpaAdapter] 처리되지 않은 GradingStatus | status: {}, attemptId: {}",
          gradingStatus,
          attempt.getAttemptId());
    }
  }

  /** SubmittedAnswer 도메인 record를 ExamSubmittedAnswer 엔티티로 변환한다. */
  private ExamSubmittedAnswer toSubmittedAnswerEntity(ExamAttempt attempt, SubmittedAnswer answer) {
    // JsonNode → JSON 문자열 직렬화 (JSONB 컬럼 저장용)
    String answerJson = null;
    if (answer.answer() != null) {
      try {
        answerJson = objectMapper.writeValueAsString(answer.answer());
      } catch (JsonProcessingException e) {
        log.warn(
            "[ExamAttemptRepositoryJpaAdapter] 답안 직렬화 실패 | questionId: {}", answer.questionId());
        answerJson = "{}";
      }
    }
    return ExamSubmittedAnswer.builder()
        .attempt(attempt)
        .questionId(answer.questionId())
        .answerJson(answerJson)
        .submittedAt(OffsetDateTime.now())
        .build();
  }

  /**
   * attemptId로 exam_attempt_question 스냅샷을 조회하여 questionId → templateId Map을 빌드한다.
   *
   * <p>exam_question_grade 테이블에 templateId 컬럼이 없어 결과 조회 때 null이 된다. 마이그레이션 대신 스냅샷
   * 테이블을 한 번 더 읽는다. questionId는 attempt 안에서 유일해 Map 키로 쓴다.
   *
   * @return questionId → templateId 매핑 (templateId가 null인 문항은 null 값으로 포함)
   */
  private java.util.Map<String, String> buildTemplateIdMap(String attemptId) {
    List<ExamAttemptQuestion> snapshots =
        questionRepository.findByAttemptAttemptIdOrderByDisplayOrderAsc(attemptId);
    java.util.Map<String, String> map = new java.util.HashMap<>(snapshots.size() * 2);
    for (ExamAttemptQuestion q : snapshots) {
      map.put(q.getQuestionId(), q.getTemplateId());
    }
    log.debug(
        "[ExamAttemptRepositoryJpaAdapter] templateId 매핑 빌드 완료 | attemptId: {}, 문항수: {}",
        attemptId,
        map.size());
    return map;
  }

  // 포트 구현: findReviewData

  /**
   * 복습 모드용 데이터를 조회한다. 문항 스냅샷과 제출 답안(questionId → answerJson)을 묶는다.
   *
   * @return 문항별 복습 데이터 목록 (displayOrder 오름차순)
   */
  @Override
  @Transactional(readOnly = true)
  public List<QuestionReviewData> findReviewData(String attemptId) {
    List<ExamAttemptQuestion> snapshots =
        questionRepository.findByAttemptAttemptIdOrderByDisplayOrderAsc(attemptId);

    List<ExamSubmittedAnswer> submittedAnswers =
        submittedAnswerRepository.findByAttemptAttemptId(attemptId);
    java.util.Map<String, String> answerByQuestionId =
        new java.util.HashMap<>(submittedAnswers.size() * 2);
    for (ExamSubmittedAnswer sa : submittedAnswers) {
      answerByQuestionId.put(sa.getQuestionId(), sa.getAnswerJson());
    }

    // 스냅샷과 제출 답안을 묶는다. displayOrder는 엔티티에서 직접 읽는다
    List<QuestionReviewData> result = new ArrayList<>();
    for (ExamAttemptQuestion snapshot : snapshots) {
      ExamQuestion question = persistenceMapper.toExamQuestion(snapshot);
      String submittedAnswerJson = answerByQuestionId.get(snapshot.getQuestionId());
      int displayOrder = snapshot.getDisplayOrder() != null ? snapshot.getDisplayOrder() : 0;
      result.add(new QuestionReviewData(question, displayOrder, submittedAnswerJson));
    }

    log.debug(
        "[ExamAttemptRepositoryJpaAdapter] 복습 데이터 조회 완료 | attemptId: {}, 문항수: {}",
        attemptId,
        result.size());
    return result;
  }

  /**
   * ExamQuestionGrade 엔티티를 QuestionGrade 도메인 record로 변환한다. templateId는 exam_attempt_question 스냅샷에서
   * 조회한 Map으로 주입한다.
   */
  private QuestionGrade toQuestionGradeWithTemplateId(
      ExamQuestionGrade entity, java.util.Map<String, String> templateIdByQuestionId) {
    // rubric_breakdown_json이 비어 있으면 빈 리스트로 둔다
    List<RubricCriterion> rubricBreakdown = List.of();
    if (entity.getRubricBreakdownJson() != null && !entity.getRubricBreakdownJson().isBlank()) {
      try {
        rubricBreakdown =
            objectMapper.readValue(
                entity.getRubricBreakdownJson(),
                objectMapper
                    .getTypeFactory()
                    .constructCollectionType(List.class, RubricCriterion.class));
      } catch (JsonProcessingException e) {
        log.warn(
            "[ExamAttemptRepositoryJpaAdapter] rubric_breakdown_json 파싱 실패 | questionId: {}",
            entity.getQuestionId());
      }
    }
    double confidence = entity.getConfidence() != null ? entity.getConfidence().doubleValue() : 1.0;
    boolean needsManualReview =
        entity.getNeedsManualReview() != null && entity.getNeedsManualReview();

    // 스냅샷에 없는 문항이면 templateId가 null이므로 경고 로그로 남긴다
    String templateId = templateIdByQuestionId.get(entity.getQuestionId());
    if (templateId == null) {
      log.warn(
          "[ExamAttemptRepositoryJpaAdapter] templateId 조회 실패 | questionId: {} — 스냅샷에 없는 문항",
          entity.getQuestionId());
    }

    return new QuestionGrade(
        entity.getQuestionId(),
        templateId, // exam_attempt_question.template_id에서 조회한 값
        entity.getScore(),
        entity.getMaxScore(),
        entity.getScore() != null
            && entity.getMaxScore() != null
            && entity.getScore().compareTo(entity.getMaxScore()) >= 0
            && entity.getMaxScore().signum() > 0,
        persistenceMapper.toModelGradingMode(entity.getGradingMode()),
        entity.getFeedback(),
        rubricBreakdown,
        confidence,
        needsManualReview);
  }
}
