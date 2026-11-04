/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.calendar.repository.CalendarEventRepository;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.ChapterStatus;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.exception.CourseErrorCode;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.lesson.dto.request.ProgressRequest;
import com.swez.backend.domain.lesson.dto.request.QuizAnswerRequest;
import com.swez.backend.domain.lesson.dto.response.QuizListResponse;
import com.swez.backend.domain.lesson.dto.response.QuizResultResponse;
import com.swez.backend.domain.lesson.dto.response.SlideListResponse;
import com.swez.backend.domain.lesson.entity.LessonProgress;
import com.swez.backend.domain.lesson.entity.Quiz;
import com.swez.backend.domain.lesson.entity.QuizAnswer;
import com.swez.backend.domain.lesson.entity.Slide;
import com.swez.backend.domain.lesson.exception.LessonErrorCode;
import com.swez.backend.domain.lesson.mapper.LessonMapper;
import com.swez.backend.domain.lesson.repository.LessonProgressRepository;
import com.swez.backend.domain.lesson.repository.QuizAnswerRepository;
import com.swez.backend.domain.lesson.repository.QuizRepository;
import com.swez.backend.domain.lesson.repository.SlideRepository;
import com.swez.backend.domain.report.repository.StudyTimeLogRepository;
import com.swez.backend.global.exception.CustomException;

@DisplayName("LessonService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LessonServiceTest {

  @Mock private CourseRepository courseRepository;
  @Mock private ChapterRepository chapterRepository;
  @Mock private SlideRepository slideRepository;
  @Mock private LessonProgressRepository lessonProgressRepository;
  @Mock private QuizRepository quizRepository;
  @Mock private QuizAnswerRepository quizAnswerRepository;
  @Mock private UserRepository userRepository;
  @Mock private LessonMapper lessonMapper;
  @Mock private ProgressiveLessonGenerationService progressiveLessonGenerationService;
  @Mock private CalendarEventRepository calendarEventRepository;
  @Mock private StudyTimeLogRepository studyTimeLogRepository;

  @InjectMocks private LessonService lessonService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String OTHER_ID = "usr_OTHERUSERIDIDIDIDIDIDIDID";
  private static final String COURSE_ID = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String CHAPTER_ID = "chp_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String CHAPTER_2_ID = "chp_01ARZ3NDEKTSV4RRFFQ69G5FA2";
  private static final String SLIDE_ID = "sld_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String QUIZ_ID = "qz_01ARZ3NDEKTSV4RRFFQ69G5FAVV";

  @BeforeEach
  void setUp() {
    /* 매퍼 로직(slideId·quizId·options 파싱)까지 검증하도록 LessonMapper 실제 인스턴스를 넣는다.
    @Mock 으로 두면 toSlideResponse·toQuizResponse 가 null 을 반환해 목록 단언이 NPE 로 깨진다.
    ChatServiceTest 의 ChatMapper 주입과 동일한 패턴이다. */
    ReflectionTestUtils.setField(lessonService, "lessonMapper", new LessonMapper(new ObjectMapper()));
    given(calendarEventRepository.findTopByUser_IdAndEventDateAndType(any(), any(), any()))
        .willReturn(Optional.empty());
  }

  private User makeUser(String id) {
    return User.builder()
        .id(id)
        .email("test@example.com")
        .nickname("테스터")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  private Course makeCourse(User owner) {
    return Course.builder()
        .id(COURSE_ID)
        .user(owner)
        .courseName("수학 기초")
        .subject("수학")
        .status(CourseStatus.ACTIVE)
        .totalChapters(3)
        .completedSlides(0)
        .progressPercent(BigDecimal.ZERO)
        .build();
  }

  private Chapter makeChapter(Course course) {
    return makeChapter(course, CHAPTER_ID, 1, ChapterStatus.AVAILABLE);
  }

  private Chapter makeChapter(
      Course course, String chapterId, int orderIndex, ChapterStatus status) {
    return Chapter.builder()
        .id(chapterId)
        .course(course)
        .chapterName(orderIndex + "강")
        .orderIndex(orderIndex)
        .totalSlides(3)
        .status(status)
        .xpEarned(0)
        .build();
  }

  private Slide makeSlide(Chapter chapter, int idx) {
    return Slide.builder()
        .id(SLIDE_ID)
        .chapter(chapter)
        .slideIdx(idx)
        .title("슬라이드 " + idx)
        .content("내용 " + idx)
        .audioUrl("https://audio.example.com/slide" + idx + ".mp3")
        .durationSec(BigDecimal.valueOf(60))
        .build();
  }

  private Quiz makeQuiz(Chapter chapter, Slide slide) {
    return Quiz.builder()
        .id(QUIZ_ID)
        .chapter(chapter)
        .slide(slide)
        .quizIdx(1)
        .questionText("1 + 1 = ?")
        .options("[\"1\",\"2\",\"3\",\"4\"]")
        .correctOption(1)
        .explanation("1 + 1은 2입니다.")
        .build();
  }

  /**
   * 과외·챕터 소유권 검증을 위한 공통 mock 설정.
   * 반환된 Course 인스턴스는 레포지토리 mock이 반환하는 것과 동일한 객체다.
   * 완강 전이 검증처럼 Course 상태 변경을 확인할 때 이 메서드를 통해 공유 인스턴스를 가져온다.
   */
  private void stubCourseAndChapter() {
    User user = makeUser(USER_ID);
    Course course = makeCourse(user);
    Chapter chapter = makeChapter(course);
    given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
    given(chapterRepository.findById(CHAPTER_ID)).willReturn(Optional.of(chapter));
    given(chapterRepository.findByCourseIdOrderByOrderIndexAsc(COURSE_ID))
        .willReturn(List.of(chapter));
    given(lessonProgressRepository.findByUserIdAndChapterIdIn(USER_ID, List.of(CHAPTER_ID)))
        .willReturn(List.of());
  }

  /**
   * 레포지토리 mock이 반환하는 공유 Course 인스턴스를 구성한다.
   * 반환된 course / chapter 객체는 stubCourseAndChapter() 없이 독립적으로 mock을 설정하며,
   * chapter.getCourse()가 같은 course 참조를 가리키게 한다.
   */
  private Course[] stubCourseAndChapterReturningCourse() {
    User user = makeUser(USER_ID);
    Course course = makeCourse(user);
    Chapter chapter = makeChapter(course);
    given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
    given(chapterRepository.findById(CHAPTER_ID)).willReturn(Optional.of(chapter));
    given(chapterRepository.findByCourseIdOrderByOrderIndexAsc(COURSE_ID))
        .willReturn(List.of(chapter));
    given(lessonProgressRepository.findByUserIdAndChapterIdIn(USER_ID, List.of(CHAPTER_ID)))
        .willReturn(List.of());
    return new Course[]{course};
  }

  @Nested
  @DisplayName("getSlides()")
  class GetSlides {

    @Test
    @DisplayName("슬라이드 목록을 반환한다")
    void getSlides_returnsList() {
      // given
      stubCourseAndChapter();
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Slide slide = makeSlide(chapter, 0);

      given(slideRepository.findByChapterIdOrderBySlideIdxAsc(CHAPTER_ID))
          .willReturn(List.of(slide));
      given(lessonProgressRepository.findByChapterIdAndUserId(CHAPTER_ID, USER_ID))
          .willReturn(Optional.empty());

      // when
      SlideListResponse result = lessonService.getSlides(USER_ID, COURSE_ID, CHAPTER_ID);

      // then
      assertThat(result.totalSlides()).isEqualTo(1);
      assertThat(result.completedSlides()).isZero();
      assertThat(result.slides()).hasSize(1);
      assertThat(result.slides().get(0).slideId()).isEqualTo(SLIDE_ID);
    }

    @Test
    @DisplayName("진행률이 있으면 completedSlides를 반환한다")
    void getSlides_withProgress_returnsCompletedSlides() {
      // given
      stubCourseAndChapter();
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);

      given(slideRepository.findByChapterIdOrderBySlideIdxAsc(CHAPTER_ID))
          .willReturn(List.of(makeSlide(chapter, 0), makeSlide(chapter, 1)));

      LessonProgress progress =
          LessonProgress.builder()
              .id("lpg_TEST000000000000000000000")
              .chapter(chapter)
              .user(user)
              .currentSlideIdx(2)
              .playbackSec(BigDecimal.valueOf(120))
              .isCompleted(false)
              .build();
      given(lessonProgressRepository.findByChapterIdAndUserId(CHAPTER_ID, USER_ID))
          .willReturn(Optional.of(progress));

      // when
      SlideListResponse result = lessonService.getSlides(USER_ID, COURSE_ID, CHAPTER_ID);

      // then
      assertThat(result.completedSlides()).isEqualTo(2);
    }

    @Test
    @DisplayName("완료된 진행률이면 completedSlides를 전체 슬라이드 수로 반환한다")
    void getSlides_completedProgress_returnsTotalSlides() {
      // given
      stubCourseAndChapter();
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);

      given(slideRepository.findByChapterIdOrderBySlideIdxAsc(CHAPTER_ID))
          .willReturn(List.of(makeSlide(chapter, 0), makeSlide(chapter, 1), makeSlide(chapter, 2)));

      LessonProgress progress =
          LessonProgress.builder()
              .id("lpg_TEST000000000000000000000")
              .chapter(chapter)
              .user(user)
              .currentSlideIdx(1)
              .playbackSec(BigDecimal.valueOf(120))
              .isCompleted(true)
              .build();
      given(lessonProgressRepository.findByChapterIdAndUserId(CHAPTER_ID, USER_ID))
          .willReturn(Optional.of(progress));

      // when
      SlideListResponse result = lessonService.getSlides(USER_ID, COURSE_ID, CHAPTER_ID);

      // then
      assertThat(result.completedSlides()).isEqualTo(3);
    }

    @Test
    @DisplayName("타인 과외 접근 시 CRS_001 예외를 던진다")
    void getSlides_otherUser_throwsCrs001() {
      // given
      User owner = makeUser(USER_ID);
      Course course = makeCourse(owner);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

      // when & then
      assertThatThrownBy(() -> lessonService.getSlides(OTHER_ID, COURSE_ID, CHAPTER_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_001));
    }

    @Test
    @DisplayName("존재하지 않는 과외 조회 시 CRS_001 예외를 던진다")
    void getSlides_courseNotFound_throwsCrs001() {
      // given
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> lessonService.getSlides(USER_ID, COURSE_ID, CHAPTER_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_001));
    }

    @Test
    @DisplayName("존재하지 않는 챕터 조회 시 LSN_004 예외를 던진다")
    void getSlides_chapterNotFound_throwsLsn004() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findById(CHAPTER_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> lessonService.getSlides(USER_ID, COURSE_ID, CHAPTER_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(LessonErrorCode.LSN_004));
    }

    @Test
    @DisplayName("다음 강의가 생성되어 있어도 이전 강의 미완료면 LSN_005 예외를 던진다")
    void getSlides_prefetchedNextChapterWithoutCompletion_throwsLsn005() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter1 = makeChapter(course, CHAPTER_ID, 1, ChapterStatus.AVAILABLE);
      Chapter chapter2 = makeChapter(course, CHAPTER_2_ID, 2, ChapterStatus.AVAILABLE);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findById(CHAPTER_2_ID)).willReturn(Optional.of(chapter2));
      given(chapterRepository.findByCourseIdOrderByOrderIndexAsc(COURSE_ID))
          .willReturn(List.of(chapter1, chapter2));
      given(
              lessonProgressRepository.findByUserIdAndChapterIdIn(
                  USER_ID, List.of(CHAPTER_ID, CHAPTER_2_ID)))
          .willReturn(List.of());

      // when & then
      assertThatThrownBy(() -> lessonService.getSlides(USER_ID, COURSE_ID, CHAPTER_2_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(LessonErrorCode.LSN_005));
    }
  }

  @Nested
  @DisplayName("recordProgress()")
  class RecordProgress {

    @Test
    @DisplayName("신규 진행률을 생성한다")
    void recordProgress_new_createsProgress() {
      // given
      stubCourseAndChapter();
      User user = makeUser(USER_ID);
      given(userRepository.getReferenceById(USER_ID)).willReturn(user);
      given(lessonProgressRepository.findByChapterIdAndUserId(CHAPTER_ID, USER_ID))
          .willReturn(Optional.empty());
      given(lessonProgressRepository.save(any(LessonProgress.class)))
          .willAnswer(inv -> inv.getArgument(0));

      ProgressRequest request = new ProgressRequest(2, BigDecimal.valueOf(90), false);

      // when
      lessonService.recordProgress(USER_ID, COURSE_ID, CHAPTER_ID, request);

      // then
      verify(lessonProgressRepository).save(any(LessonProgress.class));
      verify(studyTimeLogRepository).save(any());
      verify(calendarEventRepository).save(any());
      verify(progressiveLessonGenerationService, never())
          .triggerNextLessonAfterCompletion(USER_ID, COURSE_ID, CHAPTER_ID);
    }

    @Test
    @DisplayName("기존 진행률을 갱신한다 — 완강 조건 미충족 시 Course 상태 변경 없음")
    void recordProgress_existing_updatesProgress() {
      // given: 레포 mock이 반환하는 공유 Course 인스턴스를 가져온다
      Course course = stubCourseAndChapterReturningCourse()[0];
      User user = makeUser(USER_ID);

      LessonProgress existing =
          LessonProgress.builder()
              .id("lpg_TEST000000000000000000000")
              .chapter(makeChapter(course)) // chapter.getCourse() == course
              .user(user)
              .currentSlideIdx(1)
              .playbackSec(BigDecimal.valueOf(30))
              .isCompleted(false)
              .build();
      given(lessonProgressRepository.findByChapterIdAndUserId(CHAPTER_ID, USER_ID))
          .willReturn(Optional.of(existing));
      given(lessonProgressRepository.save(any(LessonProgress.class)))
          .willAnswer(inv -> inv.getArgument(0));
      // 완료된 챕터가 1개뿐이라 totalChapters(3)에 못 미쳐 완강으로 바뀌지 않는다
      given(lessonProgressRepository.countCompletedByCourseIdAndUserId(COURSE_ID, USER_ID))
          .willReturn(1L);

      ProgressRequest request = new ProgressRequest(3, BigDecimal.valueOf(180), true);

      // when
      lessonService.recordProgress(USER_ID, COURSE_ID, CHAPTER_ID, request);

      // then
      assertThat(existing.getCurrentSlideIdx()).isEqualTo(3);
      assertThat(existing.getIsCompleted()).isTrue();
      assertThat(existing.getCompletedAt()).isNotNull();
      verify(lessonProgressRepository).save(existing);
      verify(progressiveLessonGenerationService)
          .triggerNextLessonAfterCompletion(USER_ID, COURSE_ID, CHAPTER_ID);
      // 완강 조건 미충족이라 Course는 여전히 ACTIVE
      assertThat(course.getStatus()).isEqualTo(com.swez.backend.domain.course.enums.CourseStatus.ACTIVE);
    }

    @Test
    @DisplayName("마지막 챕터 완료 시 Course → COMPLETED 전이")
    void recordProgress_lastChapter_courseBecomesCompleted() {
      // given: 레포 mock이 반환하는 공유 Course 인스턴스를 가져온다
      Course course = stubCourseAndChapterReturningCourse()[0]; // totalChapters=3
      User user = makeUser(USER_ID);

      LessonProgress existing =
          LessonProgress.builder()
              .id("lpg_TEST000000000000000000000")
              .chapter(makeChapter(course)) // chapter.getCourse() == course
              .user(user)
              .currentSlideIdx(2)
              .playbackSec(BigDecimal.valueOf(120))
              .isCompleted(false)
              .build();
      given(lessonProgressRepository.findByChapterIdAndUserId(CHAPTER_ID, USER_ID))
          .willReturn(Optional.of(existing));
      given(lessonProgressRepository.save(any(LessonProgress.class)))
          .willAnswer(inv -> inv.getArgument(0));
      // 완강 판정 분모. 프로덕션은 course.totalChapters 대신 chapter 테이블의 실제 챕터 수를 쓴다
      given(chapterRepository.countByCourseId(COURSE_ID)).willReturn(3L);
      // 이번 완료로 completedCount가 실제 챕터 수(3)와 같아져 완강 조건을 충족한다
      given(lessonProgressRepository.countCompletedByCourseIdAndUserId(COURSE_ID, USER_ID))
          .willReturn(3L);

      ProgressRequest request = new ProgressRequest(3, BigDecimal.valueOf(180), true);

      // when
      lessonService.recordProgress(USER_ID, COURSE_ID, CHAPTER_ID, request);

      // then: 레포 mock이 반환한 공유 course 인스턴스에서 상태를 확인한다
      assertThat(course.getStatus())
          .isEqualTo(com.swez.backend.domain.course.enums.CourseStatus.COMPLETED);
      assertThat(course.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 완료된 진행률은 다음 강의 트리거를 반복하지 않는다")
    void recordProgress_alreadyCompleted_doesNotTriggerAgain() {
      stubCourseAndChapter();
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);

      LessonProgress existing =
          LessonProgress.builder()
              .id("lpg_TEST000000000000000000000")
              .chapter(chapter)
              .user(user)
              .currentSlideIdx(3)
              .playbackSec(BigDecimal.valueOf(180))
              .isCompleted(true)
              .build();
      given(lessonProgressRepository.findByChapterIdAndUserId(CHAPTER_ID, USER_ID))
          .willReturn(Optional.of(existing));
      given(lessonProgressRepository.save(any(LessonProgress.class)))
          .willAnswer(inv -> inv.getArgument(0));

      ProgressRequest request = new ProgressRequest(3, BigDecimal.valueOf(210), true);

      lessonService.recordProgress(USER_ID, COURSE_ID, CHAPTER_ID, request);

      // wasCompleted=true 이므로 트리거·완강 판정 모두 실행되지 않는다
      verify(progressiveLessonGenerationService, never())
          .triggerNextLessonAfterCompletion(USER_ID, COURSE_ID, CHAPTER_ID);
      verify(lessonProgressRepository, never())
          .countCompletedByCourseIdAndUserId(COURSE_ID, USER_ID);
    }
  }

  @Nested
  @DisplayName("getQuizzes()")
  class GetQuizzes {

    @Test
    @DisplayName("퀴즈 목록을 반환한다 (정답 미포함, slideId 포함)")
    void getQuizzes_returnsList() {
      // given
      stubCourseAndChapter();
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Slide slide = makeSlide(chapter, 1);
      Quiz quiz = makeQuiz(chapter, slide);

      given(quizRepository.findByChapterIdOrderByQuizIdxAsc(CHAPTER_ID)).willReturn(List.of(quiz));

      // when
      QuizListResponse result = lessonService.getQuizzes(USER_ID, COURSE_ID, CHAPTER_ID);

      // then
      assertThat(result.totalQuizzes()).isEqualTo(1);
      assertThat(result.quizzes()).hasSize(1);
      assertThat(result.quizzes().get(0).quizId()).isEqualTo(QUIZ_ID);
      assertThat(result.quizzes().get(0).slideId()).isEqualTo(SLIDE_ID);
      assertThat(result.quizzes().get(0).options()).containsExactly("1", "2", "3", "4");
    }
  }

  @Nested
  @DisplayName("getQuizAnswers()")
  class GetQuizAnswers {

    @Test
    @DisplayName("기존 답변이 있으면 채점 결과를 반환한다")
    void getQuizAnswers_withAnswers_returnsResult() {
      // given
      stubCourseAndChapter();
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Slide slide = makeSlide(chapter, 1);
      Quiz quiz = makeQuiz(chapter, slide);

      QuizAnswer answer =
          QuizAnswer.builder()
              .id("qa_TEST000000000000000000000")
              .quiz(quiz)
              .user(user)
              .selectedOption(1)
              .isCorrect(true)
              .build();

      given(quizAnswerRepository.findByChapterIdAndUserId(CHAPTER_ID, USER_ID))
          .willReturn(List.of(answer));

      // when
      QuizResultResponse result = lessonService.getQuizAnswers(USER_ID, COURSE_ID, CHAPTER_ID);

      // then
      assertThat(result).isNotNull();
      assertThat(result.totalQuestions()).isEqualTo(1);
      assertThat(result.correctCount()).isEqualTo(1);
      assertThat(result.score()).isEqualTo(100);
      assertThat(result.results()).hasSize(1);
      assertThat(result.results().get(0).quizId()).isEqualTo(QUIZ_ID);
      assertThat(result.results().get(0).isCorrect()).isTrue();
      assertThat(result.results().get(0).explanation()).isEqualTo("1 + 1은 2입니다.");
    }

    @Test
    @DisplayName("답변이 없으면 null을 반환한다")
    void getQuizAnswers_noAnswers_returnsNull() {
      // given
      stubCourseAndChapter();
      given(quizAnswerRepository.findByChapterIdAndUserId(CHAPTER_ID, USER_ID))
          .willReturn(List.of());

      // when
      QuizResultResponse result = lessonService.getQuizAnswers(USER_ID, COURSE_ID, CHAPTER_ID);

      // then
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("submitQuizAnswers()")
  class SubmitQuizAnswers {

    @Test
    @DisplayName("퀴즈 답변을 채점하고 결과를 반환한다")
    void submitQuizAnswers_gradesCorrectly() {
      // given
      stubCourseAndChapter();
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Slide slide = makeSlide(chapter, 1);
      Quiz quiz = makeQuiz(chapter, slide);

      given(quizRepository.findAllByIdInAndChapterId(List.of(QUIZ_ID), CHAPTER_ID))
          .willReturn(List.of(quiz));
      given(quizAnswerRepository.findByQuizIdInAndUserId(List.of(QUIZ_ID), USER_ID))
          .willReturn(List.of());
      given(userRepository.getReferenceById(USER_ID)).willReturn(user);
      given(quizAnswerRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

      /* correctOption=1이므로 selectedOption=1을 보내면 정답 */
      QuizAnswerRequest request =
          new QuizAnswerRequest(List.of(new QuizAnswerRequest.SingleAnswer(QUIZ_ID, 1)));

      // when
      QuizResultResponse result =
          lessonService.submitQuizAnswers(USER_ID, COURSE_ID, CHAPTER_ID, request);

      // then
      assertThat(result.totalQuestions()).isEqualTo(1);
      assertThat(result.correctCount()).isEqualTo(1);
      assertThat(result.score()).isEqualTo(100);
      assertThat(result.results()).hasSize(1);
      assertThat(result.results().get(0).isCorrect()).isTrue();
    }

    @Test
    @DisplayName("오답 시 isCorrect=false를 반환한다")
    void submitQuizAnswers_wrongAnswer() {
      // given
      stubCourseAndChapter();
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Slide slide = makeSlide(chapter, 1);
      Quiz quiz = makeQuiz(chapter, slide);

      given(quizRepository.findAllByIdInAndChapterId(List.of(QUIZ_ID), CHAPTER_ID))
          .willReturn(List.of(quiz));
      given(quizAnswerRepository.findByQuizIdInAndUserId(List.of(QUIZ_ID), USER_ID))
          .willReturn(List.of());
      given(userRepository.getReferenceById(USER_ID)).willReturn(user);
      given(quizAnswerRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

      /* correctOption=1인데 selectedOption=0을 보내면 오답 */
      QuizAnswerRequest request =
          new QuizAnswerRequest(List.of(new QuizAnswerRequest.SingleAnswer(QUIZ_ID, 0)));

      // when
      QuizResultResponse result =
          lessonService.submitQuizAnswers(USER_ID, COURSE_ID, CHAPTER_ID, request);

      // then
      assertThat(result.correctCount()).isZero();
      assertThat(result.score()).isZero();
      assertThat(result.results().get(0).isCorrect()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 퀴즈 ID로 제출 시 LSN_002 예외를 던진다")
    void submitQuizAnswers_quizNotFound_throwsLsn002() {
      // given
      stubCourseAndChapter();
      String unknownQuizId = "qz_UNKNOWN00000000000000000";
      given(quizRepository.findAllByIdInAndChapterId(List.of(unknownQuizId), CHAPTER_ID))
          .willReturn(List.of());
      given(quizAnswerRepository.findByQuizIdInAndUserId(List.of(unknownQuizId), USER_ID))
          .willReturn(List.of());

      QuizAnswerRequest request =
          new QuizAnswerRequest(List.of(new QuizAnswerRequest.SingleAnswer(unknownQuizId, 1)));

      // when & then
      assertThatThrownBy(
              () -> lessonService.submitQuizAnswers(USER_ID, COURSE_ID, CHAPTER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(LessonErrorCode.LSN_002));
    }

    @Test
    @DisplayName("이미 답변한 퀴즈 재제출 시 LSN_003 예외를 던진다")
    void submitQuizAnswers_alreadyAnswered_throwsLsn003() {
      // given
      stubCourseAndChapter();
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      Slide slide = makeSlide(chapter, 1);
      Quiz quiz = makeQuiz(chapter, slide);

      given(quizRepository.findAllByIdInAndChapterId(List.of(QUIZ_ID), CHAPTER_ID))
          .willReturn(List.of(quiz));

      QuizAnswer existingAnswer =
          QuizAnswer.builder()
              .id("qa_TEST000000000000000000000")
              .quiz(quiz)
              .user(user)
              .selectedOption(1)
              .isCorrect(true)
              .build();
      given(quizAnswerRepository.findByQuizIdInAndUserId(List.of(QUIZ_ID), USER_ID))
          .willReturn(List.of(existingAnswer));

      QuizAnswerRequest request =
          new QuizAnswerRequest(List.of(new QuizAnswerRequest.SingleAnswer(QUIZ_ID, 2)));

      // when & then
      assertThatThrownBy(
              () -> lessonService.submitQuizAnswers(USER_ID, COURSE_ID, CHAPTER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(LessonErrorCode.LSN_003));
    }
  }
}
