/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.note.entity.Note;

public interface NoteRepository extends JpaRepository<Note, String> {

  /** 특정 과외·챕터·사용자의 노트 목록 조회 (타입 필터 없음). */
  List<Note> findByCourseIdAndChapterIdAndUserId(String courseId, String chapterId, String userId);

  /**
   * 특정 과외·챕터·사용자의 노트 목록 조회 (타입 필터 포함).
   *
   * @param type 노트 타입 (user_memo / ai_auto)
   */
  List<Note> findByCourseIdAndChapterIdAndUserIdAndType(
      String courseId, String chapterId, String userId, String type);

  /**
   * 과목/별표 필터를 조합한 동적 쿼리로 전체 노트 목록을 조회한다.
   *
   * @param subject 과목 필터 (null이면 무시)
   * @param starred 별표 필터 (null이면 무시)
   */
  @Query(
      """
      SELECT n FROM Note n
      JOIN FETCH n.course c
      LEFT JOIN FETCH n.chapter ch
      WHERE n.user.id = :userId
        AND (:subject IS NULL OR c.subject = :subject)
        AND (:starred IS NULL OR n.isStarred = :starred)
      ORDER BY n.createdAt DESC
      """)
  Page<Note> findAllByFilters(
      @Param("userId") String userId,
      @Param("subject") String subject,
      @Param("starred") Boolean starred,
      Pageable pageable);

  /**
   * 과목/검색어/별표 필터를 조합한 동적 쿼리로 전체 노트를 검색한다.
   *
   * @param subject 과목 필터 (null이면 무시)
   * @param search 제목+본문 검색어
   * @param starred 별표 필터 (null이면 무시)
   */
  @Query(
      """
      SELECT n FROM Note n
      JOIN FETCH n.course c
      LEFT JOIN FETCH n.chapter ch
      WHERE n.user.id = :userId
        AND (:subject IS NULL OR c.subject = :subject)
        AND (:starred IS NULL OR n.isStarred = :starred)
        AND (LOWER(n.content) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')))
      ORDER BY n.createdAt DESC
      """)
  Page<Note> searchAllByFilters(
      @Param("userId") String userId,
      @Param("subject") String subject,
      @Param("search") String search,
      @Param("starred") Boolean starred,
      Pageable pageable);
}
