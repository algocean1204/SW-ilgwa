/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.ChapterStatus;
import com.swez.backend.domain.course.enums.CourseStatus;
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

@DisplayName("NoteService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoteServiceTest {

  @Mock private NoteRepository noteRepository;
  @Mock private BookmarkRepository bookmarkRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private ChapterRepository chapterRepository;
  @Mock private UserRepository userRepository;
  @Mock private TelegramAccountRepository telegramAccountRepository;
  @Mock private NoteMapper noteMapper;
  @Mock private TelegramNoteSender telegramNoteSender;

  @InjectMocks private NoteService noteService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String OTHER_ID = "usr_OTHERUSERIDIDIDIDIDIDIDID";
  private static final String COURSE_ID = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String CHAPTER_ID = "chp_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String NOTE_ID = "nte_01ARZ3NDEKTSV4RRFFQ69G5FAV";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(noteService, "noteMapper", new NoteMapper());
  }

  private User makeUser(String id) {
    return User.builder()
        .id(id)
        .email("t@t.com")
        .nickname("테스터")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  private Course makeCourse(User owner) {
    return Course.builder()
        .id(COURSE_ID)
        .user(owner)
        .courseName("수학")
        .status(CourseStatus.ACTIVE)
        .totalChapters(1)
        .completedSlides(0)
        .progressPercent(BigDecimal.ZERO)
        .build();
  }

  private Chapter makeChapter(Course course) {
    return Chapter.builder()
        .id(CHAPTER_ID)
        .course(course)
        .chapterName("1강")
        .orderIndex(1)
        .totalSlides(5)
        .status(ChapterStatus.AVAILABLE)
        .xpEarned(0)
        .build();
  }

  private Note makeNote(User user, Chapter chapter) {
    return Note.builder()
        .id(NOTE_ID)
        .user(user)
        .course(chapter.getCourse())
        .chapter(chapter)
        .type("user_memo")
        .title("테스트 제목")
        .content("테스트 노트")
        .isStarred(false)
        .build();
  }

  @Nested
  @DisplayName("createNote()")
  class CreateNote {

    @Test
    @DisplayName("노트 생성 시 type이 user_memo로 고정되고 응답을 반환한다")
    void createNote_success_typeIsUserMemo() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findById(CHAPTER_ID)).willReturn(Optional.of(chapter));
      given(userRepository.getReferenceById(USER_ID)).willReturn(user);
      given(noteRepository.save(any(Note.class))).willAnswer(inv -> inv.getArgument(0));

      CreateNoteRequest request = new CreateNoteRequest("집합의 정의", 2, 30.5);

      // when
      NoteResponse result = noteService.createNote(USER_ID, COURSE_ID, CHAPTER_ID, request);

      // then
      assertThat(result.type()).isEqualTo("user_memo");
      assertThat(result.content()).isEqualTo("집합의 정의");
      then(noteRepository).should().save(any(Note.class));
    }

    @Test
    @DisplayName("타인 과외의 챕터에 노트 생성 시 CRS_001 예외를 던진다")
    void createNote_otherUserCourse_throwsCrs001() {
      // given
      User owner = makeUser(OTHER_ID);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(makeCourse(owner)));

      // when & then
      assertThatThrownBy(
              () ->
                  noteService.createNote(
                      USER_ID, COURSE_ID, CHAPTER_ID, new CreateNoteRequest("내용", null, null)))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_001));
    }
  }

  @Nested
  @DisplayName("toggleStar()")
  class ToggleStar {

    @Test
    @DisplayName("false -> true 토글 성공")
    void toggleStar_success_toTrue() {
      // given
      User user = makeUser(USER_ID);
      Note note = makeNote(user, makeChapter(makeCourse(user)));
      given(noteRepository.findById(NOTE_ID)).willReturn(Optional.of(note));
      given(noteRepository.save(any(Note.class))).willReturn(note);

      // when
      NoteResponse result = noteService.toggleStar(USER_ID, NOTE_ID);

      // then
      assertThat(result.starred()).isTrue();
    }

    @Test
    @DisplayName("타인 노트 별표 토글 시 NTE_001 예외를 던진다")
    void toggleStar_otherUserNote_throwsNte001() {
      // given
      User other = makeUser(OTHER_ID);
      Note note = makeNote(other, makeChapter(makeCourse(other)));
      given(noteRepository.findById(NOTE_ID)).willReturn(Optional.of(note));

      // when & then
      assertThatThrownBy(() -> noteService.toggleStar(USER_ID, NOTE_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(NoteErrorCode.NTE_001));
    }
  }

  @Nested
  @DisplayName("createBookmark()")
  class CreateBookmark {

    @Test
    @DisplayName("새 북마크 생성 시 저장한다")
    void createBookmark_new_saves() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findById(CHAPTER_ID)).willReturn(Optional.of(chapter));
      given(userRepository.getReferenceById(USER_ID)).willReturn(user);
      given(bookmarkRepository.findByUserIdAndChapterIdAndSlideIndex(USER_ID, CHAPTER_ID, 3))
          .willReturn(Optional.empty());
      given(bookmarkRepository.save(any(Bookmark.class))).willAnswer(inv -> inv.getArgument(0));

      // when
      BookmarkResponse result =
          noteService.createBookmark(
              USER_ID, COURSE_ID, CHAPTER_ID, new CreateBookmarkRequest(3, "중요!"));

      // then
      assertThat(result.slideIndex()).isEqualTo(3);
      assertThat(result.memo()).isEqualTo("중요!");
    }

    @Test
    @DisplayName("기존 북마크에 재호출 시 memo만 업데이트한다 (upsert)")
    void createBookmark_existing_updatesMemo() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Bookmark existing =
          Bookmark.builder()
              .id("bkm_EXISTINGBOOKMARKIDIDIDID")
              .user(user)
              .course(course)
              .chapter(chapter)
              .slideIndex(3)
              .memo("이전 메모")
              .build();

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findById(CHAPTER_ID)).willReturn(Optional.of(chapter));
      given(bookmarkRepository.findByUserIdAndChapterIdAndSlideIndex(USER_ID, CHAPTER_ID, 3))
          .willReturn(Optional.of(existing));
      given(bookmarkRepository.save(any(Bookmark.class))).willReturn(existing);

      // when
      BookmarkResponse result =
          noteService.createBookmark(
              USER_ID, COURSE_ID, CHAPTER_ID, new CreateBookmarkRequest(3, "새 메모"));

      // then: 기존 ID 유지, memo 변경
      assertThat(result.id()).isEqualTo("bkm_EXISTINGBOOKMARKIDIDIDID");
      assertThat(result.memo()).isEqualTo("새 메모");
    }
  }

  @Nested
  @DisplayName("getNotesByChapter()")
  class GetNotesByChapter {

    @Test
    @DisplayName("강의별 노트 목록을 정상 조회한다")
    void getNotesByChapter_success() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Note note = makeNote(user, chapter);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findById(CHAPTER_ID)).willReturn(Optional.of(chapter));
      given(noteRepository.findByCourseIdAndChapterIdAndUserId(COURSE_ID, CHAPTER_ID, USER_ID))
          .willReturn(List.of(note));

      // when
      List<NoteResponse> result =
          noteService.getNotesByChapter(USER_ID, COURSE_ID, CHAPTER_ID, null);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).id()).isEqualTo(NOTE_ID);
      assertThat(result.get(0).content()).isEqualTo("테스트 노트");
    }
  }

  @Nested
  @DisplayName("updateNote()")
  class UpdateNote {

    @Test
    @DisplayName("노트 제목과 내용을 정상 수정한다")
    void updateNote_success() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Note note = makeNote(user, chapter);

      given(noteRepository.findById(NOTE_ID)).willReturn(Optional.of(note));
      given(noteRepository.save(any(Note.class))).willAnswer(inv -> inv.getArgument(0));

      UpdateNoteRequest request = new UpdateNoteRequest("수정된 제목", "수정된 내용");

      // when
      NoteResponse result = noteService.updateNote(USER_ID, NOTE_ID, request);

      // then
      assertThat(result.title()).isEqualTo("수정된 제목");
      assertThat(result.content()).isEqualTo("수정된 내용");
      then(noteRepository).should().save(any(Note.class));
    }

    @Test
    @DisplayName("존재하지 않는 노트 수정 시 NTE_001 예외를 던진다")
    void updateNote_notFound() {
      // given
      given(noteRepository.findById(NOTE_ID)).willReturn(Optional.empty());

      UpdateNoteRequest request = new UpdateNoteRequest("제목", "내용");

      // when & then
      assertThatThrownBy(() -> noteService.updateNote(USER_ID, NOTE_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(NoteErrorCode.NTE_001));
    }
  }

  @Nested
  @DisplayName("deleteNote()")
  class DeleteNote {

    @Test
    @DisplayName("본인 노트를 정상 삭제한다")
    void deleteNote_success() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Note note = makeNote(user, chapter);

      given(noteRepository.findById(NOTE_ID)).willReturn(Optional.of(note));

      // when
      noteService.deleteNote(USER_ID, NOTE_ID);

      // then
      then(noteRepository).should().delete(note);
    }

    @Test
    @DisplayName("타인 노트 삭제 시 NTE_002 예외를 던진다")
    void deleteNote_notOwner() {
      // given
      User other = makeUser(OTHER_ID);
      Course course = makeCourse(other);
      Chapter chapter = makeChapter(course);
      Note note = makeNote(other, chapter);

      given(noteRepository.findById(NOTE_ID)).willReturn(Optional.of(note));

      // when & then
      assertThatThrownBy(() -> noteService.deleteNote(USER_ID, NOTE_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(NoteErrorCode.NTE_002));
    }
  }

  @Nested
  @DisplayName("getAllNotes()")
  class GetAllNotes {

    @Test
    @DisplayName("전체 노트 목록을 페이징 조회한다")
    void getAllNotes_success() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Note note = makeNote(user, chapter);

      Page<Note> notePage = new PageImpl<>(List.of(note), PageRequest.of(0, 20), 1);
      given(
              noteRepository.findAllByFilters(
                  eq(USER_ID), eq(null), eq(null), any(PageRequest.class)))
          .willReturn(notePage);

      // when
      Page<NoteListResponse> result = noteService.getAllNotes(USER_ID, null, null, null, 0, 20);

      // then
      assertThat(result.getTotalElements()).isEqualTo(1);
      assertThat(result.getContent().get(0).id()).isEqualTo(NOTE_ID);
      assertThat(result.getContent().get(0).courseName()).isEqualTo("수학");
    }

    @Test
    @DisplayName("검색어가 있으면 검색 전용 쿼리를 사용한다")
    void getAllNotes_withSearch() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Note note = makeNote(user, chapter);

      Page<Note> notePage = new PageImpl<>(List.of(note), PageRequest.of(0, 20), 1);
      given(
              noteRepository.searchAllByFilters(
                  eq(USER_ID), eq(null), eq("집합"), eq(null), any(PageRequest.class)))
          .willReturn(notePage);

      // when
      Page<NoteListResponse> result = noteService.getAllNotes(USER_ID, null, "  집합  ", null, 0, 20);

      // then
      assertThat(result.getTotalElements()).isEqualTo(1);
      then(noteRepository)
          .should()
          .searchAllByFilters(eq(USER_ID), eq(null), eq("집합"), eq(null), any(PageRequest.class));
    }
  }

  @Nested
  @DisplayName("sendNoteToTelegram()")
  class SendNoteToTelegram {

    @Test
    @DisplayName("텔레그램 전송 성공 시 결과를 반환한다")
    void sendToTelegram_success() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Note note = makeNote(user, chapter);

      TelegramAccount account =
          TelegramAccount.builder()
              .id("tga_01ARZ3NDEKTSV4RRFFQ69G5FAV")
              .user(user)
              .telegramChatId(123456789L)
              .isActive(true)
              .build();

      given(noteRepository.findById(NOTE_ID)).willReturn(Optional.of(note));
      given(telegramAccountRepository.findByUserId(USER_ID)).willReturn(Optional.of(account));
      doNothing().when(telegramNoteSender).sendMessage(any(TelegramAccount.class), anyString());

      // when
      TelegramSendResponse result = noteService.sendNoteToTelegram(USER_ID, NOTE_ID);

      // then
      assertThat(result.noteId()).isEqualTo(NOTE_ID);
      assertThat(result.sent()).isTrue();
      then(telegramNoteSender).should().sendMessage(eq(account), anyString());
    }
  }
}
