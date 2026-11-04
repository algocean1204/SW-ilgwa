/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.mapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.examgrading.dto.request.GenerationCallbackOptionRequest;
import com.swez.backend.domain.examgrading.dto.request.GenerationCallbackQuestionRequest;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion;
import com.swez.backend.domain.examgrading.entity.ExamGradeResult;
import com.swez.backend.domain.examgrading.entity.ExamQuestionGrade;
import com.swez.backend.domain.examgrading.model.ExamQuestion;
import com.swez.backend.domain.examgrading.model.GradingMode;
import com.swez.backend.domain.examgrading.model.GradingStatus;
import com.swez.backend.domain.examgrading.model.MatchingPair;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.QuestionOption;
import com.swez.backend.domain.examgrading.model.RubricCriterion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 채점 영속 레이어 ↔ 도메인 record 변환 매퍼.
 *
 * <p>하는 일:
 *
 * <ul>
 *   <li>엔티티(ExamAttemptQuestion, ExamQuestionGrade) → 도메인 record 변환
 *   <li>도메인 record(QuestionGrade, ExamGradeResult) → 엔티티 변환
 *   <li>enum 매핑: enums.GradingMode ↔ model.GradingMode, enums.GradingStatus ↔ model.GradingStatus
 *   <li>JSONB 컬럼 직렬화/역직렬화 (옵션 목록, 루브릭 기준 등)
 * </ul>
 *
 * <p>같은 이름의 enum이 enums/와 model/ 두 패키지에 있어 두 패키지 enum 변환은 이 매퍼에서만 한다. 어댑터 밖에서
 * enums ↔ model을 직접 변환하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamGradingPersistenceMapper {

  private final ObjectMapper objectMapper;

  // enum 변환: enums 패키지 ↔ model 패키지

  /** enums.GradingMode → model.GradingMode 변환. 두 패키지의 enum 상수명이 동일하므로 이름 기반으로 매핑한다. */
  public GradingMode toModelGradingMode(
      com.swez.backend.domain.examgrading.enums.GradingMode entityEnum) {
    if (entityEnum == null) {
      return GradingMode.DETERMINISTIC;
    }
    // 두 enum의 상수명이 같아 name()으로 변환한다. 상수를 추가하면 양쪽을 함께 바꿔야 한다
    return GradingMode.valueOf(entityEnum.name());
  }

  /** model.GradingMode → enums.GradingMode 변환. */
  public com.swez.backend.domain.examgrading.enums.GradingMode toEntityGradingMode(
      GradingMode modelEnum) {
    if (modelEnum == null) {
      return com.swez.backend.domain.examgrading.enums.GradingMode.DETERMINISTIC;
    }
    return com.swez.backend.domain.examgrading.enums.GradingMode.valueOf(modelEnum.name());
  }

  /** enums.GradingStatus → model.GradingStatus 변환. */
  public GradingStatus toModelGradingStatus(
      com.swez.backend.domain.examgrading.enums.GradingStatus entityEnum) {
    if (entityEnum == null) {
      return GradingStatus.GRADED;
    }
    return GradingStatus.valueOf(entityEnum.name());
  }

  /** model.GradingStatus → enums.GradingStatus 변환. */
  public com.swez.backend.domain.examgrading.enums.GradingStatus toEntityGradingStatus(
      GradingStatus modelEnum) {
    if (modelEnum == null) {
      return com.swez.backend.domain.examgrading.enums.GradingStatus.GRADED;
    }
    return com.swez.backend.domain.examgrading.enums.GradingStatus.valueOf(modelEnum.name());
  }

  // ExamAttemptQuestion 엔티티 → ExamQuestion 도메인 record

  /**
   * ExamAttemptQuestion 엔티티를 채점에 필요한 모든 서버 전용 필드를 포함한 ExamQuestion 도메인 record로 변환한다.
   *
   * <p>correctAnswer·explanation·options·matchingPairs·points 등 채점에 필요한 서버 전용 필드를 모두 채운다. 이 필드가
   * 컨트롤러 응답에 섞이면 GradingLeakGuard가 차단한다.
   */
  public ExamQuestion toExamQuestion(ExamAttemptQuestion entity) {
    GradingMetadata meta = parseGradingMetadata(entity.getGradingMetadataJson());

    return new ExamQuestion(
        entity.getQuestionId(),
        null, // draftId: 스냅샷에 없는 필드, 채점 불필요
        entity.getTemplateId(),
        null, // topic: 채점 불필요
        null, // difficulty: 채점 불필요
        null, // bloomLevel: 채점 불필요
        entity.getStem(),
        parseOptions(entity.getOptionsJson()),
        meta.matchingPairs(),
        meta.orderingItems(),
        meta.correctOrdering(),
        meta.blankPositions(),
        meta.blankAnswers(),
        null, // codeSnippet: grading_metadata_json 미사용 템플릿
        entity.getCorrectAnswer(),
        entity.getExplanation(),
        entity.getSourceReference(),
        entity.getPoints(),
        null // distractorRationale: 채점 불필요
        );
  }

  // QuestionGrade 도메인 record → ExamQuestionGrade 엔티티

  /**
   * QuestionGrade 도메인 record를 ExamQuestionGrade 영속 엔티티로 변환한다.
   *
   * @param attempt 소속 응시 엔티티 참조 (FK 바인딩용)
   */
  public ExamQuestionGrade toExamQuestionGradeEntity(QuestionGrade grade, ExamAttempt attempt) {
    return ExamQuestionGrade.builder()
        .attempt(attempt)
        .questionId(grade.questionId())
        .score(grade.score() != null ? grade.score() : BigDecimal.ZERO)
        .maxScore(grade.maxScore() != null ? grade.maxScore() : BigDecimal.ZERO)
        .gradingMode(toEntityGradingMode(grade.gradingMode()))
        .feedback(grade.feedback())
        .rubricBreakdownJson(serializeRubricBreakdown(grade.rubricBreakdown()))
        .confidence(BigDecimal.valueOf(grade.confidence()))
        .needsManualReview(grade.needsManualReview())
        .build();
  }

  /**
   * 도메인 ExamGradeResult record를 ExamGradeResult 영속 엔티티로 변환한다.
   *
   * @param attempt 소속 응시 엔티티 참조 (공유 PK 바인딩용)
   */
  public ExamGradeResult toExamGradeResultEntity(
      com.swez.backend.domain.examgrading.model.ExamGradeResult result, ExamAttempt attempt) {
    return ExamGradeResult.builder()
        .attempt(attempt)
        .totalScore(result.totalScore() != null ? result.totalScore() : BigDecimal.ZERO)
        .maxScore(result.maxScore() != null ? result.maxScore() : BigDecimal.ZERO)
        .percentage(result.percentage() != null ? result.percentage() : BigDecimal.ZERO)
        .passed(result.passed())
        .gradingStatus(toEntityGradingStatus(result.gradingStatus()))
        .gradedAt(result.gradedAt())
        .build();
  }

  // JSONB 직렬화 / 역직렬화

  // ExamForge 생성 콜백 → ExamAttemptQuestion 엔티티 변환

  /**
   * FastAPI 생성 완료 콜백의 문항 DTO를 ExamAttemptQuestion 영속 엔티티로 변환한다.
   *
   * <p>options는 QuestionOption[] JSONB로 직렬화하며, gradingMetadata는 Object를 JSON 문자열로 직렬화한다.
   * correct_answer·explanation·grading_metadata_json은 서버 전용 민감 필드로 DB에만 저장한다.
   *
   * @param attempt 소속 응시 엔티티 (FK 바인딩용)
   */
  public ExamAttemptQuestion toExamAttemptQuestionEntity(
      GenerationCallbackQuestionRequest dto, ExamAttempt attempt) {
    String optionsJson = serializeCallbackOptions(dto.options());

    String gradingMetadataJson = serializeGradingMetadata(dto.gradingMetadata());

    return ExamAttemptQuestion.builder()
        .attempt(attempt)
        .questionId(dto.questionId())
        .templateId(dto.templateId())
        .displayOrder(dto.displayOrder() != null ? dto.displayOrder() : 0)
        .stem(dto.stem())
        .optionsJson(optionsJson)
        .points(dto.points() != null ? dto.points() : BigDecimal.ONE)
        .correctAnswer(dto.correctAnswer())
        .explanation(dto.explanation())
        .sourceReference(dto.sourceReference())
        .gradingMetadataJson(gradingMetadataJson)
        .build();
  }

  /**
   * FastAPI 생성 콜백 options 목록을 QuestionOption JSONB 직렬화 문자열로 변환한다. null이거나 비어 있으면 null을 반환한다.
   *
   * <p>is_correct 필드는 직렬화에 포함되며 DB에 저장된다. 응답 DTO 변환 시 SafeOptionResponse로 치환해야 한다.
   */
  private String serializeCallbackOptions(List<GenerationCallbackOptionRequest> options) {
    if (options == null || options.isEmpty()) {
      return null;
    }
    List<QuestionOption> questionOptions =
        options.stream()
            .map(opt -> new QuestionOption(opt.label(), opt.text(), opt.isCorrect()))
            .toList();
    try {
      return objectMapper.writeValueAsString(questionOptions);
    } catch (JsonProcessingException e) {
      log.warn("[ExamGradingPersistenceMapper] options 직렬화 실패: {}", e.getMessage());
      return null;
    }
  }

  /**
   * gradingMetadata Object를 JSONB 문자열로 직렬화한다. null이면 null을 반환한다.
   *
   * <p>FastAPI는 문항 유형별 채점 추가 데이터(연결형 우항, 순서형 정답 등)를 이 필드로 전달한다.
   */
  private String serializeGradingMetadata(Object metadata) {
    if (metadata == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      log.warn("[ExamGradingPersistenceMapper] gradingMetadata 직렬화 실패: {}", e.getMessage());
      return null;
    }
  }

  /**
   * JSONB options_json 컬럼 문자열을 QuestionOption 리스트로 역직렬화한다. 공개 DTO 변환용이며 호출자가 is_correct 필드를 직접
   * 다루도록 public으로 두지만, 응답 DTO 매핑 시 SafeOptionResponse로 변환하여 is_correct를 제거해야 한다.
   *
   * @return 파싱된 QuestionOption 리스트. 파싱 실패 시 빈 리스트
   */
  public List<QuestionOption> parseOptionsForPublic(String optionsJson) {
    return parseOptions(optionsJson);
  }

  /**
   * grading_metadata_json 컬럼 문자열을 GradingMetadata record로 파싱한다.
   *
   * <p>공개 DTO 변환용이다. ExamAttemptDetailService가 비객관식 문항의 표시용 데이터(빈칸 수, 순서 항목, 연결 좌항/우항)를 추출할 때 사용한다.
   * 정답 정보(blank_answers, correct_ordering)도 GradingMetadata에 들어 있으므로 호출자가 이를 DTO에 매핑하지 않아야 한다.
   *
   * @return 파싱된 GradingMetadata record. 파싱 실패 시 empty 인스턴스
   */
  public GradingMetadata parseGradingMetadataForPublic(String gradingMetadataJson) {
    return parseGradingMetadata(gradingMetadataJson);
  }

  /** JSONB options_json 컬럼 문자열을 QuestionOption 리스트로 역직렬화한다. 파싱 실패 시 빈 리스트를 반환하고 경고 로그를 남긴다. */
  private List<QuestionOption> parseOptions(String optionsJson) {
    if (optionsJson == null || optionsJson.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return objectMapper.readValue(optionsJson, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.warn("[ExamGradingPersistenceMapper] options_json 파싱 실패: {}", e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * RubricCriterion 리스트를 JSONB rubric_breakdown_json 컬럼 문자열로 직렬화한다. null이거나 비어 있으면 null을
   * 반환한다(AI_RUBRIC 이외 방식에서 null 허용).
   */
  private String serializeRubricBreakdown(List<RubricCriterion> breakdown) {
    if (breakdown == null || breakdown.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(breakdown);
    } catch (JsonProcessingException e) {
      log.warn("[ExamGradingPersistenceMapper] rubric_breakdown 직렬화 실패: {}", e.getMessage());
      return null;
    }
  }

  /**
   * grading_metadata_json 컬럼 문자열에서 연결형·순서형·빈칸형 채점에 필요한 추가 데이터를 파싱한다. 파싱 실패 또는 null이면 모든 필드가 빈 리스트인
   * 기본 인스턴스를 반환한다.
   */
  private GradingMetadata parseGradingMetadata(String json) {
    if (json == null || json.isBlank()) {
      return GradingMetadata.empty();
    }
    try {
      return objectMapper.readValue(json, GradingMetadata.class);
    } catch (JsonProcessingException e) {
      log.warn("[ExamGradingPersistenceMapper] grading_metadata_json 파싱 실패: {}", e.getMessage());
      return GradingMetadata.empty();
    }
  }

  // 내부 VO: grading_metadata_json 파싱용

  /**
   * grading_metadata_json 컬럼에 저장되는 문항 유형별 추가 채점 데이터. 연결형(matchingPairs), 순서형(orderingItems +
   * correctOrdering), 빈칸형(blankPositions + blankAnswers) 를 수용한다.
   *
   * <p>DB에는 snake_case JSON으로 저장되므로 @JsonProperty 로 명시적으로 매핑해야 역직렬화가 정상 동작한다. 누락 시 Jackson이 필드를
   * null로 처리하여 순서형·빈칸형·연결형 detail 표시가 깨진다.
   */
  public record GradingMetadata(
      @JsonProperty("matching_pairs") List<MatchingPair> matchingPairs,
      @JsonProperty("ordering_items") List<String> orderingItems,
      @JsonProperty("correct_ordering") List<String> correctOrdering,
      @JsonProperty("blank_positions") List<Integer> blankPositions,
      @JsonProperty("blank_answers") List<String> blankAnswers) {

    /** 모든 필드가 빈 리스트인 기본 인스턴스를 반환한다. */
    static GradingMetadata empty() {
      return new GradingMetadata(
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList());
    }
  }
}
