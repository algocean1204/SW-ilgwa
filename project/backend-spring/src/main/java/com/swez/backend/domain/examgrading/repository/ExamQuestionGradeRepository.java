/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.examgrading.entity.ExamQuestionGrade;
import com.swez.backend.domain.examgrading.enums.GradingMode;

/** exam_question_grade 테이블 CRUD + 조회 리포지토리 */
public interface ExamQuestionGradeRepository extends JpaRepository<ExamQuestionGrade, Long> {

  /** 응시에 속한 모든 문항 채점 결과를 조회한다. 총점 계산 및 공개 응답 생성에 사용한다. */
  List<ExamQuestionGrade> findByAttemptAttemptId(String attemptId);

  /** 응시 내 특정 문항의 채점 결과를 조회한다. */
  Optional<ExamQuestionGrade> findByAttemptAttemptIdAndQuestionId(
      String attemptId, String questionId);

  /** 특정 채점 방식에 해당하는 문항 채점 결과 목록을 조회한다. AI 루브릭 문항만 FastAPI로 위임하는 필터링에 사용한다. */
  List<ExamQuestionGrade> findByAttemptAttemptIdAndGradingMode(
      String attemptId, GradingMode gradingMode);

  /** 응시 내 수동 검토 필요 문항 수를 반환한다. AttemptStatus를 MANUAL_REVIEW_REQUIRED로 전환할 조건 판단에 사용한다. */
  long countByAttemptAttemptIdAndNeedsManualReview(String attemptId, Boolean needsManualReview);
}
