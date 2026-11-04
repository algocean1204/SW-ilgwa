/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.mapper;

import org.springframework.stereotype.Component;

import com.swez.backend.domain.note.dto.response.BookmarkResponse;
import com.swez.backend.domain.note.dto.response.NoteListResponse;
import com.swez.backend.domain.note.dto.response.NoteResponse;
import com.swez.backend.domain.note.entity.Bookmark;
import com.swez.backend.domain.note.entity.Note;

/** 노트·북마크 도메인 엔티티를 DTO로 변환한다. */
@Component
public class NoteMapper {

  /** Note 엔티티를 NoteResponse로 변환한다. */
  public NoteResponse toResponse(Note note) {
    return NoteResponse.builder()
        .id(note.getId())
        .title(note.getTitle())
        .content(note.getContent())
        .type(note.getType())
        .slideIndex(note.getSlideIndex())
        .timestamp(note.getTimestampSec())
        .starred(Boolean.TRUE.equals(note.getIsStarred()))
        .createdAt(note.getCreatedAt())
        .build();
  }

  /**
   * 전체 노트 목록용으로 Note 엔티티를 NoteListResponse로 변환한다.
   *
   * @param note course, chapter를 미리 fetch한 Note 엔티티
   */
  public NoteListResponse toListResponse(Note note) {
    return NoteListResponse.builder()
        .id(note.getId())
        .title(note.getTitle())
        .content(note.getContent())
        .type(note.getType())
        .starred(Boolean.TRUE.equals(note.getIsStarred()))
        .courseId(note.getCourse().getId())
        .courseName(note.getCourse().getCourseName())
        .subject(note.getCourse().getSubject())
        .chapterId(note.getChapter() != null ? note.getChapter().getId() : null)
        .chapterTitle(note.getChapter() != null ? note.getChapter().getChapterName() : null)
        .createdAt(note.getCreatedAt())
        .updatedAt(note.getUpdatedAt())
        .build();
  }

  /** Bookmark 엔티티를 BookmarkResponse로 변환한다. */
  public BookmarkResponse toBookmarkResponse(Bookmark bookmark) {
    return BookmarkResponse.builder()
        .id(bookmark.getId())
        .slideIndex(bookmark.getSlideIndex())
        .memo(bookmark.getMemo())
        .createdAt(bookmark.getCreatedAt())
        .build();
  }
}
