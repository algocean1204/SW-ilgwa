/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.exception.CourseErrorCode;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.note.dto.request.CreateBookmarkRequest;
import com.swez.backend.domain.note.dto.request.CreateNoteRequest;
import com.swez.backend.domain.note.dto.request.UpdateNoteRequest;
import com.swez.backend.domain.note.dto.response.BookmarkResponse;
import com.swez.backend.domain.note.dto.response.NoteListResponse;
import com.swez.backend.domain.note.dto.response.NoteResponse;
import com.swez.backend.domain.note.dto.response.TelegramSendResponse;
import com.swez.backend.domain.note.entity.Bookmark;
import com.swez.backend.domain.note.entity.Note;
import com.swez.backend.domain.note.exception.NoteErrorCode;
import com.swez.backend.domain.note.mapper.NoteMapper;
import com.swez.backend.domain.note.repository.BookmarkRepository;
import com.swez.backend.domain.note.repository.NoteRepository;
import com.swez.backend.domain.user.entity.TelegramAccount;
import com.swez.backend.domain.user.repository.TelegramAccountRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 노트와 북마크의 생성·조회·수정·삭제, 텔레그램 전송을 처리한다. */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NoteService {

  private final NoteRepository noteRepository;
  private final BookmarkRepository bookmarkRepository;
  private final CourseRepository courseRepository;
  private final ChapterRepository chapterRepository;
  private final UserRepository userRepository;
  private final TelegramAccountRepository telegramAccountRepository;
  private final NoteMapper noteMapper;
  private final TelegramNoteSender telegramNoteSender;

  /** 사용자 메모 노트를 생성한다. type은 항상 user_memo다. */
  @Transactional
  public NoteResponse createNote(
      String userId, String courseId, String chapterId, CreateNoteRequest request) {
    log.debug("[NoteService] createNote() - START | userId: {}, chapterId: {}", userId, chapterId);

    Chapter chapter = findChapterWithOwnership(userId, courseId, chapterId);

    Note note =
        Note.builder()
            .id(IdGenerator.note())
            .user(userRepository.getReferenceById(userId))
            .course(chapter.getCourse())
            .chapter(chapter)
            .type("user_memo")
            .content(request.content())
            .slideIndex(request.slideIndex())
            .timestampSec(request.timestamp())
            .build();
    noteRepository.save(note);

    NoteResponse result = noteMapper.toResponse(note);
    log.debug("[NoteService] createNote() - END | noteId: {}", note.getId());
    return result;
  }

  /** 노트 별표를 토글한다. */
  @Transactional
  public NoteResponse toggleStar(String userId, String noteId) {
    log.debug("[NoteService] toggleStar() - START | userId: {}, noteId: {}", userId, noteId);

    Note note =
        noteRepository
            .findById(noteId)
            .orElseThrow(() -> new CustomException(NoteErrorCode.NTE_001));
    if (!note.getUser().getId().equals(userId)) {
      throw new CustomException(NoteErrorCode.NTE_001);
    }

    note.toggleStar();
    noteRepository.save(note);

    NoteResponse result = noteMapper.toResponse(note);
    log.debug(
        "[NoteService] toggleStar() - END | noteId: {}, starred: {}", noteId, note.getIsStarred());
    return result;
  }

  /**
   * 슬라이드 북마크를 upsert 한다. 같은 (user, chapter, slideIndex)가 이미 있으면 memo만
   * 업데이트한다.
   */
  @Transactional
  public BookmarkResponse createBookmark(
      String userId, String courseId, String chapterId, CreateBookmarkRequest request) {
    log.debug(
        "[NoteService] createBookmark() - START | userId: {}, chapterId: {}, slideIndex: {}",
        userId,
        chapterId,
        request.slideIndex());

    Chapter chapter = findChapterWithOwnership(userId, courseId, chapterId);

    /*
        save()만 쓰면 UNIQUE INDEX(user_id, chapter_id, slide_index)와 충돌하므로
        기존 북마크를 먼저 찾아 있으면 갱신하고 없으면 새로 만든다.
    */
    Bookmark bookmark =
        bookmarkRepository
            .findByUserIdAndChapterIdAndSlideIndex(userId, chapterId, request.slideIndex())
            .map(
                existing -> {
                  existing.updateMemo(request.memo());
                  return existing;
                })
            .orElseGet(
                () ->
                    Bookmark.builder()
                        .id(IdGenerator.bookmark())
                        .user(userRepository.getReferenceById(userId))
                        .course(chapter.getCourse())
                        .chapter(chapter)
                        .slideIndex(request.slideIndex())
                        .memo(request.memo())
                        .build());
    bookmarkRepository.save(bookmark);

    BookmarkResponse result = noteMapper.toBookmarkResponse(bookmark);
    log.debug("[NoteService] createBookmark() - END | bookmarkId: {}", bookmark.getId());
    return result;
  }

  /**
   * 강의별 노트 목록(AI 자동생성 + 사용자 메모)을 조회한다.
   *
   * @param type 노트 타입 필터 (null이면 전체)
   */
  @Transactional(readOnly = true)
  public List<NoteResponse> getNotesByChapter(
      String userId, String courseId, String chapterId, String type) {
    log.debug(
        "[NoteService] getNotesByChapter() - START | userId: {}, chapterId: {}, type: {}",
        userId,
        chapterId,
        type);

    findChapterWithOwnership(userId, courseId, chapterId);

    List<Note> notes =
        (type != null && !type.isBlank())
            ? noteRepository.findByCourseIdAndChapterIdAndUserIdAndType(
                courseId, chapterId, userId, type)
            : noteRepository.findByCourseIdAndChapterIdAndUserId(courseId, chapterId, userId);

    log.debug("[NoteService] getNotesByChapter() - END | count: {}", notes.size());
    return notes.stream().map(noteMapper::toResponse).toList();
  }

  /** 노트를 수정한다. AI 생성 노트도 편집할 수 있다. */
  @Transactional
  public NoteResponse updateNote(String userId, String noteId, UpdateNoteRequest request) {
    log.debug("[NoteService] updateNote() - START | userId: {}, noteId: {}", userId, noteId);

    Note note = findNoteWithOwnership(userId, noteId);

    /* null이 아닌 필드만 갱신한다. */
    if (request.title() != null) {
      note.updateTitle(request.title());
    }
    if (request.content() != null) {
      note.updateContent(request.content());
    }
    noteRepository.save(note);

    NoteResponse result = noteMapper.toResponse(note);
    log.debug("[NoteService] updateNote() - END | noteId: {}", noteId);
    return result;
  }

  /** 노트를 삭제한다. */
  @Transactional
  public void deleteNote(String userId, String noteId) {
    log.debug("[NoteService] deleteNote() - START | userId: {}, noteId: {}", userId, noteId);

    Note note = findNoteWithOwnership(userId, noteId);
    noteRepository.delete(note);

    log.debug("[NoteService] deleteNote() - END | noteId: {}", noteId);
  }

  /**
   * MemoryNotes 뷰의 전체 노트 목록을 과목·검색어·별표 필터로 조회한다.
   *
   * @param subject 과목 필터 (null이면 전체)
   * @param search 검색어 (null이면 전체)
   * @param starred 별표 필터 (null이면 전체)
   * @param page 페이지 번호 (0부터)
   */
  @Transactional(readOnly = true)
  public Page<NoteListResponse> getAllNotes(
      String userId, String subject, String search, Boolean starred, int page, int size) {
    log.debug(
        "[NoteService] getAllNotes() - START | userId: {}, subject: {}, search: {}, starred: {}",
        userId,
        subject,
        search,
        starred);

    String normalizedSearch = normalizeSearch(search);
    PageRequest pageRequest = PageRequest.of(page, size);
    Page<Note> notes =
        normalizedSearch == null
            ? noteRepository.findAllByFilters(userId, subject, starred, pageRequest)
            : noteRepository.searchAllByFilters(
                userId, subject, normalizedSearch, starred, pageRequest);
    Page<NoteListResponse> result = notes.map(noteMapper::toListResponse);

    log.debug("[NoteService] getAllNotes() - END | totalElements: {}", notes.getTotalElements());
    return result;
  }

  private String normalizeSearch(String search) {
    if (search == null || search.isBlank()) {
      return null;
    }
    return search.trim();
  }

  /** 노트를 사용자의 활성 텔레그램 계정으로 전송한다. */
  @Transactional(readOnly = true)
  public TelegramSendResponse sendNoteToTelegram(String userId, String noteId) {
    log.debug(
        "[NoteService] sendNoteToTelegram() - START | userId: {}, noteId: {}", userId, noteId);

    Note note = findNoteWithOwnership(userId, noteId);

    TelegramAccount telegramAccount =
        telegramAccountRepository
            .findByUserId(userId)
            .filter(TelegramAccount::getIsActive)
            .orElseThrow(() -> new CustomException(NoteErrorCode.NTE_003));

    String messageText = buildTelegramMessage(note);
    telegramNoteSender.sendMessage(telegramAccount, messageText);

    log.debug("[NoteService] sendNoteToTelegram() - END | noteId: {}", noteId);
    return TelegramSendResponse.builder()
        .noteId(noteId)
        .sent(true)
        .message("텔레그램으로 노트를 전송했습니다.")
        .build();
  }

  // Private Helpers

  /** 챕터를 조회하고 소유권을 확인한다. 과외 쪽 문제면 CRS_001, 챕터 쪽 문제면 CRS_002를 던진다. */
  private Chapter findChapterWithOwnership(String userId, String courseId, String chapterId) {
    Course course =
        courseRepository
            .findById(courseId)
            .orElseThrow(() -> new CustomException(CourseErrorCode.CRS_001));
    if (!course.getUser().getId().equals(userId)) {
      throw new CustomException(CourseErrorCode.CRS_001);
    }
    Chapter chapter =
        chapterRepository
            .findById(chapterId)
            .orElseThrow(() -> new CustomException(CourseErrorCode.CRS_002));
    if (!chapter.getCourse().getId().equals(courseId)) {
      throw new CustomException(CourseErrorCode.CRS_002);
    }
    return chapter;
  }

  /** 노트를 조회하고 소유권을 확인한다. 노트가 없으면 NTE_001, 소유자가 다르면 NTE_002를 던진다. */
  private Note findNoteWithOwnership(String userId, String noteId) {
    Note note =
        noteRepository
            .findById(noteId)
            .orElseThrow(() -> new CustomException(NoteErrorCode.NTE_001));
    if (!note.getUser().getId().equals(userId)) {
      throw new CustomException(NoteErrorCode.NTE_002);
    }
    return note;
  }

  /** 텔레그램 전송용 HTML 메시지를 만든다. 사용자 입력 값은 HTML 이스케이프한다. */
  private String buildTelegramMessage(Note note) {
    StringBuilder sb = new StringBuilder();
    sb.append("<b>📝 노트</b>\n");
    if (note.getTitle() != null && !note.getTitle().isBlank()) {
      sb.append("<b>").append(escapeHtml(note.getTitle())).append("</b>\n\n");
    }
    sb.append(escapeHtml(note.getContent()));
    if (note.getCourse() != null) {
      sb.append("\n\n📚 ").append(escapeHtml(note.getCourse().getCourseName()));
    }
    return sb.toString();
  }

  /** 텔레그램 HTML 파싱 모드에서 깨지지 않도록 &, <, > 세 문자를 HTML 엔티티로 바꾼다. */
  private String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
