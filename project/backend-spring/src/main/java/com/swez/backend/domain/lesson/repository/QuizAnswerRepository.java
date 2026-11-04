/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.lesson.entity.QuizAnswer;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, String> {

  /** 퀴즈·사용자 조합으로 기존 답변이 있는지 확인한다. */
  boolean existsByQuizIdAndUserId(String quizId, String userId);

  /** 여러 퀴즈 ID에 대한 사용자의 답변 목록을 조회한다. */
  List<QuizAnswer> findByQuizIdInAndUserId(List<String> quizIds, String userId);

  /**
   * [start, end) 구간의 일별 퀴즈 수와 정답 수를 집계한다.
   *
   * @return [LocalDate, Long totalCount, Long correctCount] 형태의 배열 리스트
   */
  @Query(
      "SELECT CAST(qa.answeredAt AS LocalDate), COUNT(qa), SUM(CASE WHEN qa.isCorrect = true THEN 1 ELSE 0 END) "
          + "FROM QuizAnswer qa WHERE qa.user.id = :userId AND qa.answeredAt >= :start AND qa.answeredAt < :end "
          + "GROUP BY CAST(qa.answeredAt AS LocalDate) ORDER BY CAST(qa.answeredAt AS LocalDate)")
  List<Object[]> findDailyQuizStats(
      @Param("userId") String userId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /**
   * 코스에 속한 챕터별 퀴즈 정답률을 집계한다.
   *
   * @return [chapterId, chapterName, totalCount, correctCount] 형태의 배열 리스트
   */
  @Query(
      "SELECT q.chapter.id, q.chapter.chapterName, COUNT(qa), "
          + "SUM(CASE WHEN qa.isCorrect = true THEN 1 ELSE 0 END) "
          + "FROM QuizAnswer qa JOIN qa.quiz q "
          + "WHERE qa.user.id = :userId AND q.chapter.course.id = :courseId "
          + "GROUP BY q.chapter.id, q.chapter.chapterName "
          + "ORDER BY q.chapter.id")
  List<Object[]> findChapterQuizAccuracy(
      @Param("userId") String userId, @Param("courseId") String courseId);

  /** 챕터 내 사용자의 퀴즈 답변 목록을 조회한다. N+1이 나지 않게 quiz를 JOIN FETCH 한다. */
  @Query(
      "SELECT qa FROM QuizAnswer qa JOIN FETCH qa.quiz q "
          + "WHERE q.chapter.id = :chapterId AND qa.user.id = :userId")
  List<QuizAnswer> findByChapterIdAndUserId(
      @Param("chapterId") String chapterId, @Param("userId") String userId);
}
