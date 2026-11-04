/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion;

/** exam_attempt_question 테이블 CRUD + 조회 리포지토리 */
public interface ExamAttemptQuestionRepository extends JpaRepository<ExamAttemptQuestion, Long> {

  /** 응시에 속한 모든 스냅샷 문항을 표시 순서 오름차순으로 조회한다. 채점 시 서버 스냅샷 전체를 로드하는 기본 진입점이다. */
  List<ExamAttemptQuestion> findByAttemptAttemptIdOrderByDisplayOrderAsc(String attemptId);

  /** 응시 내 특정 문항 ID에 해당하는 스냅샷을 조회한다. */
  Optional<ExamAttemptQuestion> findByAttemptAttemptIdAndQuestionId(
      String attemptId, String questionId);

  /** 응시에 속한 문항 수를 반환한다. 제출 답안 수와 대조할 때 쓴다. */
  long countByAttemptAttemptId(String attemptId);
}
