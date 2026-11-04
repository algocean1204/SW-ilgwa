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

import com.swez.backend.domain.calendar.entity.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, String> {

  /** 특정 기간 내 제출된 건수를 센다. 대시보드 주간 제출률 계산에 쓴다. */
  long countBySubmittedAtBetween(LocalDateTime start, LocalDateTime end);

  List<Submission> findTop20ByUserIdOrderByCreatedAtDesc(String userId);

  /** 특정 과제에 대한 사용자의 최신 제출 기록을 조회한다. */
  Optional<Submission> findTopByAssignmentIdAndUserIdOrderByCreatedAtDesc(
      String assignmentId, String userId);

  /** 특정 과제에 대한 사용자의 제출 존재 여부를 확인한다. */
  boolean existsByAssignmentIdAndUserId(String assignmentId, String userId);

  /** 기간 내 완료(done) 상태인 사용자의 제출 목록을 조회한다. */
  @Query(
      "SELECT s FROM Submission s JOIN FETCH s.assignment "
          + "WHERE s.user.id = :userId AND s.status = 'done' "
          + "AND s.gradedAt >= :start AND s.gradedAt < :end")
  List<Submission> findDoneSubmissionsInRange(
      @Param("userId") String userId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);
}
