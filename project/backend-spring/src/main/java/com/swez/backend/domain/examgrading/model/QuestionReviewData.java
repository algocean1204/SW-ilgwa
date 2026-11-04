/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.examgrading.model;

/**
 * 복습 모드에서 문항 하나의 스냅샷 + 제출 답안 쌍을 담는 도메인 record.
 *
 * <p>ExamAttemptResultService가 문항별 복습 데이터를 조립할 때 사용한다.
 * 정답·해설은 GRADED 상태의 복습 경로에서만 공개되므로, 이 record를
 * 생성하는 쪽(어댑터)에서 상태 검증 없이 전달하고 서비스에서 게이팅한다.
 *
 * @param question           응시 시점 문항 스냅샷 (정답·해설 포함)
 * @param displayOrder       문항 표시 순번 (exam_attempt_question.display_order)
 * @param submittedAnswerJson 응시자가 제출한 답안 JSON 문자열 (미응답이면 null)
 */
public record QuestionReviewData(ExamQuestion question, int displayOrder, String submittedAnswerJson) {}
