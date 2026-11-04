/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.CourseStatus;

public interface CourseRepository extends JpaRepository<Course, String> {

  /** N+1 을 피하려고 여러 사용자의 특정 상태 과외를 한 번에 조회한다. */
  List<Course> findByUserIdInAndStatus(List<String> userIds, CourseStatus status);

  /**
   * 지정 시각 이전에 생성된 CURRICULUM_GENERATING 코스를 조회한다.
   * timeout 스케줄러가 장기 지연 코스를 CURRICULUM_FAILED로 전이하는 데 사용한다.
   */
  @Query(
      "SELECT c FROM Course c "
          + "WHERE c.status = com.swez.backend.domain.course.enums.CourseStatus.CURRICULUM_GENERATING "
          + "AND c.createdAt < :cutoff")
  List<Course> findStuckGeneratingCourses(@Param("cutoff") LocalDateTime cutoff);

  /** 사용자의 활성 과외 수를 조회한다 (DELETED 제외) */
  @Query(
      "SELECT COUNT(c) FROM Course c "
          + "WHERE c.user.id = :userId "
          + "AND c.status <> com.swez.backend.domain.course.enums.CourseStatus.DELETED")
  long countActiveByUserId(@Param("userId") String userId);

  /** 특정 상태 과외의 평균 진행률을 조회한다 */
  @Query("SELECT COALESCE(AVG(c.progressPercent), 0) FROM Course c WHERE c.status = :status")
  double findAverageProgressByStatus(@Param("status") CourseStatus status);

  /**
   * 사용자의 과외 목록을 조회한다. status·subject는 null이면 전체 조회.
   *
   * @param status 과외 상태 필터 (null이면 전체)
   * @param subject 과목 필터 (null이면 전체)
   */
  @Query(
      value =
          """
          SELECT c FROM Course c
          LEFT JOIN FETCH c.tutor
          WHERE c.user.id = :userId
            AND c.status <> com.swez.backend.domain.course.enums.CourseStatus.DELETED
            AND (:status IS NULL OR c.status = :status)
            AND (:subject IS NULL OR c.subject = :subject)
          ORDER BY c.createdAt DESC
          """,
      countQuery =
          """
          SELECT COUNT(c) FROM Course c
          WHERE c.user.id = :userId
            AND c.status <> com.swez.backend.domain.course.enums.CourseStatus.DELETED
            AND (:status IS NULL OR c.status = :status)
            AND (:subject IS NULL OR c.subject = :subject)
          """)
  Page<Course> findByUserId(
      @Param("userId") String userId,
      @Param("status") CourseStatus status,
      @Param("subject") String subject,
      Pageable pageable);
}
