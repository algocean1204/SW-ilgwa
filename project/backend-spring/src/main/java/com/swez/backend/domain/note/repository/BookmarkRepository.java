/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.note.entity.Bookmark;

public interface BookmarkRepository extends JpaRepository<Bookmark, String> {

  /** GET /tutoring/{id}/lessons/{lessonId}에서 쓰는, 사용자의 챕터 내 북마크 전체 조회. */
  List<Bookmark> findByUserIdAndChapterId(String userId, String chapterId);

  /** upsert 전에 존재 여부를 확인하는 슬라이드 단위 북마크 단건 조회. */
  Optional<Bookmark> findByUserIdAndChapterIdAndSlideIndex(
      String userId, String chapterId, Integer slideIndex);
}
