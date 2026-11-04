/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.enums.ChapterStatus;

public interface ChapterRepository extends JpaRepository<Chapter, String> {

  /** 과외 내 챕터 전체를 order_index 오름차순(강의 순서)으로 조회한다. */
  List<Chapter> findByCourseIdOrderByOrderIndexAsc(String courseId);

  /** 과외 내 특정 순서의 챕터를 조회한다. */
  Optional<Chapter> findByCourseIdAndOrderIndex(String courseId, Integer orderIndex);

  /** N+1 을 피하려고 여러 과외의 챕터를 한 번에 조회한다. */
  @Query("SELECT ch FROM Chapter ch WHERE ch.course.id IN :courseIds")
  List<Chapter> findAllByCourseIdIn(@Param("courseIds") List<String> courseIds);

  /**
   * 특정 과외에 속한 챕터의 실제 개수를 조회한다.
   *
   * <p>course.totalChapters 는 커리큘럼 생성 때 채워지지 않아 0으로 남을 수 있어, 완강 판정에는 DB의
   * 실제 챕터 수를 센다.
   */
  long countByCourseId(String courseId);

  /**
   * 특정 사용자가 [start, end) 구간에 완료한 챕터(강의) 수를 집계한다.
   *
   * <p>'오늘 1강의 듣기' 미션 집계용. 완료 기준은 {@code Chapter.status == COMPLETED} 이며,
   * 완료 시각은 {@code completedAt} 컬럼을 사용한다(리포트/마이코스 통계와 동일 기준). 소유자
   * 필터는 챕터가 속한 과외의 사용자({@code course.user.id})로 건다.
   *
   * <p>"오늘" 경계는 나노초가 빠질 수 있는 {@code LocalTime.MAX} 대신 반열린 구간
   * {@code [start, end)} 으로 처리한다(start 포함, end 미포함).
   *
   * @param userId 과외 소유자 ID
   * @param status 완료로 판정할 상태 값 (보통 {@link ChapterStatus#COMPLETED})
   * @param start 집계 시작 시각 (포함)
   * @param end 집계 종료 시각 (미포함)
   */
  @Query(
      "SELECT COUNT(ch) FROM Chapter ch "
          + "WHERE ch.course.user.id = :userId "
          + "AND ch.status = :status "
          + "AND ch.completedAt >= :start AND ch.completedAt < :end")
  long countCompletedByUserInRange(
      @Param("userId") String userId,
      @Param("status") ChapterStatus status,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);
}
