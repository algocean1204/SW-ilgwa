/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.dto.response;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 응시 상세 조회용 문항 공개 DTO.
 *
 * <p>프론트엔드에 노출되는 응시용 문항 정보만 담는다. 정답(correctAnswer/correct_answer), 해설(explanation),
 * 출처(sourceReference/source_reference), 채점 메타데이터(gradingMetadata/grading_metadata), 보기의 정답
 * 여부(is_correct), answerKeySeal은 절대 포함하지 않는다.
 *
 * <p>GradingLeakGuard가 직렬화 결과에서 차단 필드명 누수를 2차 검증한다.
 *
 * <p>다유형 필드 설명:
 *
 * <ul>
 *   <li>객관식 4지/5지: options만 사용
 *   <li>OX: options만 사용 (label=O/X)
 *   <li>단답형: 나머지 필드 null (text input)
 *   <li>빈칸채우기: blankCount(빈칸 수), blankMarker("___" 표기 규약)
 *   <li>순서배열: orderingItems(섞인 항목 목록)
 *   <li>연결형: matchingLeftItems(좌항 목록), matchingRightItems(섞인 우항 목록)
 *   <li>서술형: 나머지 필드 null (textarea input)
 * </ul>
 */
@Builder
@Schema(description = "응시용 공개 문항 DTO — 정답·해설·출처·채점 메타 제외, 다유형 표시 데이터 포함")
public record AttemptQuestionResponse(
    @Schema(description = "문항 ID", example = "q_0001") String questionId,
    @Schema(description = "문항 템플릿 ID", example = "ko_multiple_choice_4") String templateId,
    @Schema(description = "프론트 표시 순서", example = "1") Integer displayOrder,
    @Schema(description = "문항 본문") String stem,
    @Schema(description = "배점", example = "2.0") BigDecimal points,
    @Schema(description = "보기 목록 — 객관식/OX 전용, is_correct 필드 제외. 그 외 null")
        List<SafeOptionResponse> options,
    @Schema(
            description =
                "빈칸채우기 전용 — 빈칸 개수. stem 내 빈칸은 ___(밑줄 2개 이상)으로 표기된다."
                    + " 프론트는 blankCount 수 만큼 text input을 렌더링한다.",
            example = "2",
            nullable = true)
        Integer blankCount,
    @Schema(
            description = "순서배열 전용 — 섞인 항목 목록(정답 순서 미포함). " + "프론트는 이 목록을 드래그·앤·드롭 UI로 렌더링한다.",
            nullable = true)
        List<String> orderingItems,
    @Schema(
            description =
                "연결형 전용 — 좌항 목록 (표시 순서 유지). "
                    + "프론트는 좌항과 matchingRightItems를 각각 렌더링하고 연결 UI를 제공한다.",
            nullable = true)
        List<String> matchingLeftItems,
    @Schema(
            description = "연결형 전용 — 섞인 우항 목록 (정답 매핑 미포함). " + "프론트는 이 목록을 선택 가능한 항목으로 렌더링한다.",
            nullable = true)
        List<String> matchingRightItems) {}
