/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.calendar.entity.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, String> {

  /** 특정 기간 내 마감인 과제 수를 센다. 대시보드 주간 제출률 계산에 쓴다. */
  long countByDueDateBetween(LocalDateTime start, LocalDateTime end);

  /** 특정 사용자의 지정 상태 과제 중 가장 먼저 생성된 1건을 조회한다. 텔레그램 수신 제출 시 활성 과제를 자동 매핑하는 용도로 사용한다. */
  Optional<Assignment> findFirstByUserIdAndStatusOrderByCreatedAtAsc(String userId, String status);

  /** 특정 과외의 과제 목록을 마감일 오름차순으로 조회한다. status 필터가 null이면 전체 조회. */
  @Query(
      """
      SELECT a FROM Assignment a
      WHERE a.course.id = :courseId
        AND (:status IS NULL OR a.status = :status)
      ORDER BY a.dueDate ASC NULLS LAST, a.createdAt DESC
      """)
  List<Assignment> findByCourseIdAndOptionalStatus(
      @Param("courseId") String courseId, @Param("status") String status);
}
