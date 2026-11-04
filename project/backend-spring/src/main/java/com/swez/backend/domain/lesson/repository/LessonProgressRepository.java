/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.lesson.entity.LessonProgress;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, String> {

  /** 챕터·사용자 조합으로 진행률을 조회한다. */
  Optional<LessonProgress> findByChapterIdAndUserId(String chapterId, String userId);

  /** 여러 챕터의 사용자별 진행률을 한 번에 조회한다. */
  @Query(
      "SELECT lp FROM LessonProgress lp "
          + "WHERE lp.user.id = :userId AND lp.chapter.id IN :chapterIds")
  List<LessonProgress> findByUserIdAndChapterIdIn(
      @Param("userId") String userId, @Param("chapterIds") List<String> chapterIds);

  /**
   * 여러 과외에서 사용자가 학습을 시작했거나 완료한 챕터 수를 한 번에 집계한다.
   *
   * <p>홈/내 과외의 강의 수 표시는 "완강 수"보다 "실제로 들어간 강의 수"에 가깝다. 그래서
   * LessonProgress가 있고, 완료됐거나 한 장 이상 진행된 챕터를 센다.
   *
   * @return Object[] = {courseId, studiedCount}
   */
  @Query(
      "SELECT lp.chapter.course.id, COUNT(lp) FROM LessonProgress lp "
          + "WHERE lp.user.id = :userId "
          + "AND lp.chapter.course.id IN :courseIds "
          + "AND (lp.isCompleted = true OR lp.currentSlideIdx > 0) "
          + "GROUP BY lp.chapter.course.id")
  List<Object[]> countStudiedByUserIdAndCourseIdIn(
      @Param("userId") String userId, @Param("courseIds") List<String> courseIds);

  /** 특정 과외에서 사용자가 학습을 시작했거나 완료한 챕터 수를 센다. */
  @Query(
      "SELECT COUNT(lp) FROM LessonProgress lp "
          + "WHERE lp.chapter.course.id = :courseId "
          + "AND lp.user.id = :userId "
          + "AND (lp.isCompleted = true OR lp.currentSlideIdx > 0)")
  long countStudiedByCourseIdAndUserId(
      @Param("courseId") String courseId, @Param("userId") String userId);

  /**
   * 특정 과외에서 사용자가 완료한 챕터 수를 센다.
   *
   * <p>완강(Course→COMPLETED) 전이를 판단하려면 이 과외에서 isCompleted=true인 LessonProgress 수가
   * 필요하다. chapter 테이블을 거쳐 course_id로 거른다.
   */
  @Query(
      "SELECT COUNT(lp) FROM LessonProgress lp "
          + "WHERE lp.chapter.course.id = :courseId "
          + "AND lp.user.id = :userId "
          + "AND lp.isCompleted = true")
  long countCompletedByCourseIdAndUserId(
      @Param("courseId") String courseId, @Param("userId") String userId);

  /**
   * N+1이 나지 않게 여러 과외의 사용자별 완료(isCompleted=true) 챕터 수를 한 번에 집계한다.
   *
   * <p>completedLessons(완료 강의 수)는 완료 기준인 진행률(progress)과 맞아야 해서 진행 중
   * (currentSlideIdx>0)은 빼고 완료만 센다. 진행 포함 집계는 countStudiedByUserIdAndCourseIdIn을 쓴다.
   *
   * @return Object[] = {courseId, completedCount}
   */
  @Query(
      "SELECT lp.chapter.course.id, COUNT(lp) FROM LessonProgress lp "
          + "WHERE lp.user.id = :userId "
          + "AND lp.chapter.course.id IN :courseIds "
          + "AND lp.isCompleted = true "
          + "GROUP BY lp.chapter.course.id")
  List<Object[]> countCompletedByUserIdAndCourseIdIn(
      @Param("userId") String userId, @Param("courseIds") List<String> courseIds);
}
