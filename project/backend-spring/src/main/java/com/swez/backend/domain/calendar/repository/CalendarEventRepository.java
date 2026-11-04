/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.calendar.entity.CalendarEvent;
import com.swez.backend.domain.calendar.enums.CalendarEventType;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, String> {

  /**
   * 사용자의 특정 기간 내 이벤트를 조회한다.
   *
   * @param startDate 시작 날짜 (포함)
   * @param endDate 종료 날짜 (포함)
   */
  @Query(
      """
      SELECT e FROM CalendarEvent e
      LEFT JOIN FETCH e.course
      WHERE e.user.id = :userId
        AND e.eventDate >= :startDate
        AND e.eventDate <= :endDate
      ORDER BY e.eventDate ASC, e.scheduledTime ASC
      """)
  List<CalendarEvent> findByUserIdAndDateBetween(
      @Param("userId") String userId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /** 당일 특정 유형의 이벤트를 조회한다. 학습 스트릭 upsert 에 쓴다. */
  Optional<CalendarEvent> findTopByUser_IdAndEventDateAndType(
      String userId, LocalDate eventDate, CalendarEventType type);

  /** 사용자의 특정 날짜(포함) 이후 완료된 이벤트를 조회한다. streak 히트맵 계산에 쓴다. */
  @Query(
      """
      SELECT e FROM CalendarEvent e
      WHERE e.user.id = :userId
        AND e.status = 'completed'
        AND e.eventDate >= :since
      ORDER BY e.eventDate ASC
      """)
  List<CalendarEvent> findCompletedByUserIdSince(
      @Param("userId") String userId, @Param("since") LocalDate since);
}
