/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.examgrading.entity.ExamSubmittedAnswer;

/** exam_submitted_answer 테이블 CRUD + 조회 리포지토리 */
public interface ExamSubmittedAnswerRepository extends JpaRepository<ExamSubmittedAnswer, Long> {

  /** 응시에 속한 모든 제출 답안을 조회한다. 채점 로직에서 스냅샷 문항과 매핑하는 기본 진입점이다. */
  List<ExamSubmittedAnswer> findByAttemptAttemptId(String attemptId);

  /** 응시 내 특정 문항의 제출 답안을 조회한다. 단일 문항 재채점 또는 수동 검토 시 사용한다. */
  Optional<ExamSubmittedAnswer> findByAttemptAttemptIdAndQuestionId(
      String attemptId, String questionId);

  /** 응시에 제출된 답안 수를 반환한다. 빈 제출(submittedAnswers: []) 여부 확인에 사용한다. */
  long countByAttemptAttemptId(String attemptId);
}
