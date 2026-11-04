/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.report.entity.StudyTimeLog;

/** 리포트 통계 집계에 쓰는 학습 시간 로그 레포지토리 */
public interface StudyTimeLogRepository extends JpaRepository<StudyTimeLog, String> {

  /**
   * 특정 사용자의 [start, end) 구간 일별 학습 시간 합계를 조회한다. createdAt은 레코드 생성 시각이라
   * 학습 시작 시각인 startedAt을 기준으로 집계한다.
   *
   * @return [날짜(LocalDate), 학습 시간 합계(Long)] 형태의 Object 배열 리스트
   */
  @Query(
      "SELECT CAST(s.startedAt AS LocalDate), SUM(s.durationMinutes) "
          + "FROM StudyTimeLog s "
          + "WHERE s.user.id = :userId "
          + "AND s.startedAt >= :start AND s.startedAt < :end "
          + "GROUP BY CAST(s.startedAt AS LocalDate) "
          + "ORDER BY CAST(s.startedAt AS LocalDate)")
  List<Object[]> findDailyStudyMinutes(
      @Param("userId") String userId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /**
   * streakDays 계산에 쓰려고 학습 기록이 있는 날짜 목록을 최신순으로 조회한다. 기준은 학습 시작 시각
   * startedAt이다.
   *
   * @return 학습 기록이 있는 날짜 목록 (내림차순)
   */
  @Query(
      "SELECT DISTINCT CAST(s.startedAt AS LocalDate) "
          + "FROM StudyTimeLog s "
          + "WHERE s.user.id = :userId "
          + "ORDER BY CAST(s.startedAt AS LocalDate) DESC")
  List<java.time.LocalDate> findDistinctStudyDates(@Param("userId") String userId);

  /**
   * 특정 사용자의 [start, end) 구간 학습 시간 합계(분)를 단일 스칼라로 반환한다. 기록이 없으면 0.
   *
   * <p>'오늘 학습 N분' 미션 집계에 쓴다. {@link #findDailyStudyMinutes}는 일자별로 묶은 Object[]
   * 리스트를 반환해 단일 합계가 필요한 미션 집계에 맞지 않으므로 전용 메서드를 둔다. 집계 기준은
   * {@code startedAt}(학습 시작 시각)이다. {@code createdAt}은 레코드 생성 시각이라 쓰지 않는다.
   *
   * <p>"오늘" 경계는 나노초가 빠질 수 있는 {@code LocalTime.MAX} 대신 반열린 구간
   * {@code [start, end)}로 처리한다(start 포함, end 미포함).
   *
   * @param start 집계 시작 시각 (포함)
   * @param end 집계 종료 시각 (미포함)
   * @return 학습 시간 합계(분). 기록이 없으면 0
   */
  @Query(
      "SELECT COALESCE(SUM(s.durationMinutes), 0) FROM StudyTimeLog s "
          + "WHERE s.user.id = :userId "
          + "AND s.startedAt >= :start AND s.startedAt < :end")
  int sumStudyMinutesInRange(
      @Param("userId") String userId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);
}
