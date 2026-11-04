/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.swez.backend.domain.examgrading.dto.response.AttemptDetailResponse;
import com.swez.backend.domain.examgrading.dto.response.AttemptQuestionResponse;
import com.swez.backend.domain.examgrading.dto.response.SafeOptionResponse;
import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion;
import com.swez.backend.domain.examgrading.exception.ExamForgeGradingErrorCode;
import com.swez.backend.domain.examgrading.mapper.ExamGradingPersistenceMapper.GradingMetadata;
import com.swez.backend.domain.examgrading.model.MatchingPair;
import com.swez.backend.domain.examgrading.model.QuestionOption;
import com.swez.backend.domain.examgrading.port.ExamAttemptRepositoryPort;
import com.swez.backend.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 응시 상세 조회 서비스.
 *
 * <p>exam_attempt + exam_attempt_question 데이터를 조회하여 프론트엔드 응시 화면에 필요한 공개 DTO를 반환한다.
 *
 * <p>민감 필드는 세 겹으로 막는다:
 *
 * <ol>
 *   <li>엔티티 → DTO 변환 시 correctAnswer·explanation·sourceReference·gradingMetadata 필드를 의도적으로 매핑하지
 *       않는다.
 *   <li>보기 목록에서 is_correct 필드를 SafeOptionResponse(label/text만 포함)로 치환한다.
 *   <li>GradingLeakGuard.assertPublicResponseSafe()로 직렬화 후 차단 필드명 존재 여부를 2차 검증한다.
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAttemptDetailService {

  private final ExamAttemptRepositoryPort attemptRepository;
  private final GradingLeakGuard leakGuard;
  private final com.swez.backend.domain.examgrading.mapper.ExamGradingPersistenceMapper
      persistenceMapper;

  /**
   * 응시 상세를 조회한다. 본인 attempt가 아니면 ATTEMPT_NOT_FOUND를 던지고, 민감 필드를 뺀 문항을
   * displayOrder 순서로 돌려준다. 응답은 GradingLeakGuard로 한 번 더 검사한다.
   *
   * @param userId 요청자 userId (소유권 검증용)
   * @return 민감 필드가 제거된 응시 상세 DTO
   */
  public AttemptDetailResponse getAttemptDetail(String attemptId, String userId) {
    ExamAttempt attempt =
        attemptRepository
            .findAttemptByOwner(attemptId, userId)
            .orElseThrow(() -> new CustomException(ExamForgeGradingErrorCode.ATTEMPT_NOT_FOUND));

    List<ExamAttemptQuestion> questionEntities =
        attemptRepository.findQuestionsByAttemptId(attemptId);

    List<AttemptQuestionResponse> questions =
        questionEntities.stream().map(this::toSafeQuestionResponse).toList();

    AttemptDetailResponse response =
        AttemptDetailResponse.builder()
            .attemptId(attempt.getAttemptId())
            .examId(attempt.getExamId())
            .status(attempt.getStatus())
            .passPercentage(attempt.getPassPercentage())
            .createdAt(attempt.getCreatedAt())
            .questions(questions)
            .build();

    // 직렬화 결과에 차단 필드명이 있는지 2차로 확인한다
    leakGuard.assertPublicResponseSafe(response);

    log.info(
        "[ExamAttemptDetailService] 응시 상세 조회 완료 | attemptId: {}, 문항수: {}",
        attemptId,
        questions.size());
    return response;
  }

  /**
   * ExamAttemptQuestion 엔티티를 AttemptQuestionResponse로 변환한다.
   *
   * <p>correctAnswer·explanation·sourceReference·gradingMetadataJson·옵션의 is_correct를 의도적으로 매핑하지
   * 않는다. 비객관식 문항의 표시용 데이터(빈칸 수·순서 항목·연결 좌우항)는 grading_metadata_json에서 추출하되, 정답 정보(
   * blank_answers·correct_ordering·matching 연결 결과)는 포함하지 않는다.
   */
  private AttemptQuestionResponse toSafeQuestionResponse(ExamAttemptQuestion entity) {
    // options_json을 파싱하여 is_correct가 제거된 SafeOptionResponse 목록으로 변환
    List<QuestionOption> options = persistenceMapper.parseOptionsForPublic(entity.getOptionsJson());
    List<SafeOptionResponse> safeOptions =
        options.stream()
            .map(opt -> SafeOptionResponse.builder().label(opt.label()).text(opt.text()).build())
            .toList();

    // grading_metadata_json에서 비객관식 문항 표시용 데이터를 추출한다
    // 정답 정보(blank_answers, correct_ordering, 연결 결과)는 넣지 않는다
    GradingMetadata meta =
        persistenceMapper.parseGradingMetadataForPublic(entity.getGradingMetadataJson());

    // 빈칸채우기: blank_positions 개수 = 빈칸 수 (정답인 blank_answers는 제외)
    Integer blankCount = null;
    List<String> orderingItems = null;
    List<String> matchingLeftItems = null;
    List<String> matchingRightItems = null;

    String templateId = entity.getTemplateId() != null ? entity.getTemplateId() : "";

    if (templateId.contains("fill_blank") || templateId.contains("cloze")) {
      // stem을 파싱하지 않고 blank_positions 크기를 빈칸 수로 쓴다
      List<Integer> positions = meta.blankPositions();
      blankCount = (positions != null && !positions.isEmpty()) ? positions.size() : 1;
    } else if (templateId.contains("ordering")) {
      // correct_ordering은 정답이라 섞인 항목 목록만 전달한다
      List<String> items = meta.orderingItems();
      orderingItems =
          (items != null && !items.isEmpty()) ? Collections.unmodifiableList(items) : null;
    } else if (templateId.contains("matching")) {
      // 좌항과 섞인 우항을 나눠 전달하고 연결 결과(정답 매핑)는 넣지 않는다
      List<MatchingPair> pairs = meta.matchingPairs();
      if (pairs != null && !pairs.isEmpty()) {
        matchingLeftItems = pairs.stream().map(MatchingPair::left).toList();
        // 우항은 섞어서 전달하여 순서 단서를 제거한다
        matchingRightItems = pairs.stream().map(MatchingPair::right).toList();
      }
    }

    return AttemptQuestionResponse.builder()
        .questionId(entity.getQuestionId())
        .templateId(entity.getTemplateId())
        .displayOrder(entity.getDisplayOrder())
        .stem(entity.getStem())
        .points(entity.getPoints())
        .options(safeOptions.isEmpty() ? null : safeOptions)
        .blankCount(blankCount)
        .orderingItems(orderingItems)
        .matchingLeftItems(matchingLeftItems)
        .matchingRightItems(matchingRightItems)
        .build();
  }
}
