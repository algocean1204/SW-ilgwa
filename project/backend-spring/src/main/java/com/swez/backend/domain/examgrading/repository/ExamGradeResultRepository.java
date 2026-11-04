/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.examgrading.entity.ExamGradeResult;
import com.swez.backend.domain.examgrading.enums.GradingStatus;

/** exam_grade_result 테이블 CRUD + 조회 리포지토리 */
public interface ExamGradeResultRepository extends JpaRepository<ExamGradeResult, String> {

  /** attempt_id로 최종 채점 결과를 조회한다. PK 조회이므로 findById와 동일하지만 도메인 의도를 명시한다. */
  Optional<ExamGradeResult> findByAttemptId(String attemptId);

  /** 특정 채점 상태의 결과 목록을 조회한다. 수동 검토 대기 응시를 일괄 처리하는 배치 진입점이다. */
  List<ExamGradeResult> findByGradingStatus(GradingStatus gradingStatus);

  /** 합격/불합격 여부로 필터링해 결과 목록을 조회한다. */
  List<ExamGradeResult> findByPassed(Boolean passed);
}
