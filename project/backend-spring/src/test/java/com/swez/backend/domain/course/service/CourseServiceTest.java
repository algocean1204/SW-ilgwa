/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.course.dto.request.CreateTutoringRequest;
import com.swez.backend.domain.course.dto.response.ChapterDetailResponse;
import com.swez.backend.domain.course.dto.response.ChapterSummaryResponse;
import com.swez.backend.domain.course.dto.response.CourseDetailResponse;
import com.swez.backend.domain.course.dto.response.CourseSummaryResponse;
import com.swez.backend.domain.course.dto.response.CurriculumResponse;
import com.swez.backend.domain.course.dto.response.RecentTutoringResponse;
import com.swez.backend.domain.course.dto.response.SourcePdfUploadResponse;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.ChapterStatus;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.exception.CourseErrorCode;
import com.swez.backend.domain.course.mapper.CourseMapper;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.lesson.entity.LessonProgress;
import com.swez.backend.domain.lesson.repository.LessonProgressRepository;
import com.swez.backend.domain.lesson.service.ProgressiveLessonGenerationService;
import com.swez.backend.domain.note.repository.BookmarkRepository;
import com.swez.backend.domain.subscription.repository.SubscriptionPlanRepository;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.domain.tutor.exception.TutorErrorCode;
import com.swez.backend.domain.tutor.repository.TutorRepository;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.page.OffsetPageResponse;
import com.swez.backend.global.service.S3Service;

@DisplayName("CourseService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseServiceTest {

  @Mock private CourseRepository courseRepository;
  @Mock private ChapterRepository chapterRepository;
  @Mock private BookmarkRepository bookmarkRepository;
  @Mock private LessonProgressRepository lessonProgressRepository;
  @Mock private UserRepository userRepository;
  @Mock private CourseMapper courseMapper;
  @Mock private RestTemplate restTemplate;
  @Mock private ProgressiveLessonGenerationService progressiveLessonGenerationService;
  @Mock private S3Service s3Service;
  @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
  @Mock private TutorRepository tutorRepository;

  @InjectMocks private CourseService courseService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String OTHER_ID = "usr_OTHERUSERIDIDIDIDIDIDIDID";
  private static final String COURSE_ID = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String CHAPTER_ID = "chp_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String CHAPTER_2_ID = "chp_01ARZ3NDEKTSV4RRFFQ69G5FA2";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(courseService, "courseMapper", new CourseMapper());
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
        .progressPercent(BigDecimal.valueOf(30))
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
        .totalSlides(10)
        .status(status)
        .xpEarned(0)
        .build();
  }

  private LessonProgress makeProgress(Chapter chapter, User user, int currentSlideIdx) {
    return makeProgress(chapter, user, currentSlideIdx, false);
  }

  private LessonProgress makeProgress(
      Chapter chapter, User user, int currentSlideIdx, boolean isCompleted) {
    return LessonProgress.builder()
        .id("lpg_01ARZ3NDEKTSV4RRFFQ69G5FAV")
        .chapter(chapter)
        .user(user)
        .currentSlideIdx(currentSlideIdx)
        .playbackSec(BigDecimal.ZERO)
        .isCompleted(isCompleted)
        .build();
  }

  private UserPrincipal makePrincipal() {
    return UserPrincipal.builder().userId(USER_ID).email("test@example.com").build();
  }

  @Nested
  @DisplayName("createTutoring()")
  class CreateTutoring {

    private static final String CUSTOM_TUTOR_ID = "tut_01ARZ3NDEKTSV4RRFFQ69G5FAV";
    private static final String PRESET_TUTOR_ID = "tut_00000000000000PRESET_OWL01";

    /** 사용자 소유 커스텀 튜터를 생성한다 */
    private Tutor makeCustomTutor(User owner) {
      return Tutor.builder()
          .tutorId(CUSTOM_TUTOR_ID)
          .user(owner)
          .name("스피커2 QA")
          .tagline("커스텀 튜터")
          .isDefault(false)
          .build();
    }

    /** 기본 프리셋 튜터를 생성한다 (user=null) */
    private Tutor makePresetTutor() {
      return Tutor.builder()
          .tutorId(PRESET_TUTOR_ID)
          .user(null)
          .name("올빼미 교수")
          .tagline("차분하고 단단한 설명")
          .isDefault(true)
          .build();
    }

    private CreateTutoringRequest makeRequest(String tutorId) {
      return new CreateTutoringRequest(
          "파이썬 입문", "함수와 반복문", "프로그래밍", "topic", null, 7, "보통", tutorId, 1, null, null);
    }

    @Test
    @DisplayName("커스텀 튜터 ID로 생성하면 과외에 해당 튜터가 배정된다")
    void createTutoring_customTutor_assignsTutor() {
      // given
      User user = makeUser(USER_ID);
      Tutor customTutor = makeCustomTutor(user);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(tutorRepository.findById(CUSTOM_TUTOR_ID)).willReturn(Optional.of(customTutor));
      given(subscriptionPlanRepository.findById(anyString())).willReturn(Optional.empty());

      // when
      CourseDetailResponse result =
          courseService.createTutoring(makePrincipal(), makeRequest(CUSTOM_TUTOR_ID));

      // then: 저장된 Course 엔티티에 튜터가 배정되고 응답에도 튜터 이름이 노출된다
      ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
      verify(courseRepository).save(captor.capture());
      assertThat(captor.getValue().getTutor()).isNotNull();
      assertThat(captor.getValue().getTutor().getTutorId()).isEqualTo(CUSTOM_TUTOR_ID);
      assertThat(result.tutorName()).isEqualTo("스피커2 QA");
    }

    @Test
    @DisplayName("기본 프리셋 튜터 ID로 생성하면 프리셋 튜터가 배정된다")
    void createTutoring_presetTutor_assignsTutor() {
      // given
      User user = makeUser(USER_ID);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(tutorRepository.findById(PRESET_TUTOR_ID)).willReturn(Optional.of(makePresetTutor()));
      given(subscriptionPlanRepository.findById(anyString())).willReturn(Optional.empty());

      // when
      CourseDetailResponse result =
          courseService.createTutoring(makePrincipal(), makeRequest(PRESET_TUTOR_ID));

      // then
      ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
      verify(courseRepository).save(captor.capture());
      assertThat(captor.getValue().getTutor().getTutorId()).isEqualTo(PRESET_TUTOR_ID);
      assertThat(result.tutorName()).isEqualTo("올빼미 교수");
    }

    @Test
    @DisplayName("다른 사용자의 커스텀 튜터 ID면 TUT_001 예외가 발생한다")
    void createTutoring_otherUsersTutor_throwsTut001() {
      // given
      User user = makeUser(USER_ID);
      Tutor otherUsersTutor = makeCustomTutor(makeUser(OTHER_ID));
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(tutorRepository.findById(CUSTOM_TUTOR_ID)).willReturn(Optional.of(otherUsersTutor));
      given(subscriptionPlanRepository.findById(anyString())).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
              () -> courseService.createTutoring(makePrincipal(), makeRequest(CUSTOM_TUTOR_ID)))
          .isInstanceOf(CustomException.class)
          .extracting("errorCode")
          .isEqualTo(TutorErrorCode.TUT_001);
      verify(courseRepository, never()).save(any(Course.class));
    }

    @Test
    @DisplayName("존재하지 않는 튜터 ID면 TUT_001 예외가 발생한다")
    void createTutoring_unknownTutor_throwsTut001() {
      // given
      User user = makeUser(USER_ID);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(tutorRepository.findById(anyString())).willReturn(Optional.empty());
      given(subscriptionPlanRepository.findById(anyString())).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
              () -> courseService.createTutoring(makePrincipal(), makeRequest(CUSTOM_TUTOR_ID)))
          .isInstanceOf(CustomException.class)
          .extracting("errorCode")
          .isEqualTo(TutorErrorCode.TUT_001);
      verify(courseRepository, never()).save(any(Course.class));
    }

    @Test
    @DisplayName("튜터 ID 미지정이면 튜터 없이 과외가 생성된다")
    void createTutoring_withoutTutorId_savesNullTutor() {
      // given
      User user = makeUser(USER_ID);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(subscriptionPlanRepository.findById(anyString())).willReturn(Optional.empty());

      // when
      CourseDetailResponse result = courseService.createTutoring(makePrincipal(), makeRequest(null));

      // then
      ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
      verify(courseRepository).save(captor.capture());
      assertThat(captor.getValue().getTutor()).isNull();
      assertThat(result.tutorName()).isNull();
    }
  }

  @Nested
  @DisplayName("getMyCourses()")
  class GetMyCourses {

    @Test
    @DisplayName("과외 목록을 OffsetPageResponse 로 반환한다")
    void getMyCourses_returnsList() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findByUserId(eq(USER_ID), isNull(), isNull(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(course)));
      given(
              lessonProgressRepository.countCompletedByUserIdAndCourseIdIn(
                  USER_ID, List.of(COURSE_ID)))
          .willReturn(List.<Object[]>of(new Object[] {COURSE_ID, 2L}));

      // when
      OffsetPageResponse<CourseSummaryResponse> result =
          courseService.getMyCourses(USER_ID, null, null, 0, 20);

      // then
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).id()).isEqualTo(COURSE_ID);
      assertThat(result.getContent().get(0).completedLessons()).isEqualTo(2);
    }

    @Test
    @DisplayName("완료한 강의가 없으면(진행중만 존재) completedLessons는 0이다")
    void getMyCourses_onlyInProgress_completedIsZero() {
      // given: 완료 집계 쿼리가 빈 결과(키 없음)인 진행중 코스. 0%인데 1강으로 표시되면 안 된다
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findByUserId(eq(USER_ID), isNull(), isNull(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(course)));
      given(
              lessonProgressRepository.countCompletedByUserIdAndCourseIdIn(
                  USER_ID, List.of(COURSE_ID)))
          .willReturn(List.<Object[]>of());

      // when
      OffsetPageResponse<CourseSummaryResponse> result =
          courseService.getMyCourses(USER_ID, null, null, 0, 20);

      // then
      assertThat(result.getContent().get(0).completedLessons()).isZero();
    }
  }

  @Nested
  @DisplayName("getCourse()")
  class GetCourse {

    @Test
    @DisplayName("소유자가 과외를 조회하면 CourseDetailResponse 를 반환한다")
    void getCourse_owner_returnsDetail() {
      // given
      User user = makeUser(USER_ID);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(makeCourse(user)));
      given(lessonProgressRepository.countCompletedByCourseIdAndUserId(COURSE_ID, USER_ID))
          .willReturn(2L);

      // when
      CourseDetailResponse result = courseService.getCourse(USER_ID, COURSE_ID);

      // then
      assertThat(result.id()).isEqualTo(COURSE_ID);
      assertThat(result.subject()).isEqualTo("수학");
      assertThat(result.completedLessons()).isEqualTo(2);
    }

    @Test
    @DisplayName("타인 과외 조회 시 CRS_001 예외를 던진다")
    void getCourse_otherUser_throwsCrs001() {
      // given
      User owner = makeUser(USER_ID);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(makeCourse(owner)));

      // when & then
      assertThatThrownBy(() -> courseService.getCourse(OTHER_ID, COURSE_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_001));
    }

    @Test
    @DisplayName("존재하지 않는 과외 조회 시 CRS_001 예외를 던진다")
    void getCourse_notFound_throwsCrs001() {
      // given
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> courseService.getCourse(USER_ID, COURSE_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_001));
    }
  }

  @Nested
  @DisplayName("getChapters()")
  class GetChapters {

    @Test
    @DisplayName("과외 내 챕터 목록을 순서대로 반환한다")
    void getChapters_returnsList() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findByCourseIdOrderByOrderIndexAsc(COURSE_ID))
          .willReturn(List.of(chapter));
      given(lessonProgressRepository.findByUserIdAndChapterIdIn(USER_ID, List.of(CHAPTER_ID)))
          .willReturn(List.of(makeProgress(chapter, user, 3)));

      // when
      List<ChapterSummaryResponse> result = courseService.getChapters(USER_ID, COURSE_ID);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).id()).isEqualTo(CHAPTER_ID);
      assertThat(result.get(0).order()).isEqualTo(1);
      assertThat(result.get(0).completedSlides()).isEqualTo(3);
    }

    @Test
    @DisplayName("선생성된 다음 강의는 이전 강의 완료 전까지 LOCKED로 마스킹한다")
    void getChapters_masksPrefetchedNextChapterUntilPreviousCompletion() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter1 = makeChapter(course, CHAPTER_ID, 1, ChapterStatus.AVAILABLE);
      Chapter chapter2 = makeChapter(course, CHAPTER_2_ID, 2, ChapterStatus.AVAILABLE);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findByCourseIdOrderByOrderIndexAsc(COURSE_ID))
          .willReturn(List.of(chapter1, chapter2));
      given(
              lessonProgressRepository.findByUserIdAndChapterIdIn(
                  USER_ID, List.of(CHAPTER_ID, CHAPTER_2_ID)))
          .willReturn(List.of());

      // when
      List<ChapterSummaryResponse> result = courseService.getChapters(USER_ID, COURSE_ID);

      // then
      assertThat(result).extracting(ChapterSummaryResponse::status)
          .containsExactly(ChapterStatus.AVAILABLE, ChapterStatus.LOCKED);
    }

    @Test
    @DisplayName("이전 강의 완료 후 선생성된 다음 강의 1개만 AVAILABLE로 노출한다")
    void getChapters_unlocksOnlyNextChapterAfterCompletion() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter1 = makeChapter(course, CHAPTER_ID, 1, ChapterStatus.AVAILABLE);
      Chapter chapter2 = makeChapter(course, CHAPTER_2_ID, 2, ChapterStatus.AVAILABLE);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findByCourseIdOrderByOrderIndexAsc(COURSE_ID))
          .willReturn(List.of(chapter1, chapter2));
      given(
              lessonProgressRepository.findByUserIdAndChapterIdIn(
                  USER_ID, List.of(CHAPTER_ID, CHAPTER_2_ID)))
          .willReturn(List.of(makeProgress(chapter1, user, 10, true)));

      // when
      List<ChapterSummaryResponse> result = courseService.getChapters(USER_ID, COURSE_ID);

      // then
      assertThat(result).extracting(ChapterSummaryResponse::status)
          .containsExactly(ChapterStatus.COMPLETED, ChapterStatus.AVAILABLE);
    }
  }

  @Nested
  @DisplayName("getCurriculum()")
  class GetCurriculum {

    @Test
    @DisplayName("커리큘럼 조회 시 사용자별 완료 슬라이드 수를 포함한다")
    void getCurriculum_includesCompletedSlides() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findByCourseIdOrderByOrderIndexAsc(COURSE_ID))
          .willReturn(List.of(chapter));
      given(lessonProgressRepository.findByUserIdAndChapterIdIn(USER_ID, List.of(CHAPTER_ID)))
          .willReturn(List.of(makeProgress(chapter, user, 4)));

      // when
      CurriculumResponse result = courseService.getCurriculum(makePrincipal(), COURSE_ID);

      // then
      assertThat(result.courseId()).isEqualTo(COURSE_ID);
      assertThat(result.chapters()).hasSize(1);
      assertThat(result.chapters().get(0).completedSlides()).isEqualTo(4);
    }
  }

  @Nested
  @DisplayName("confirmCurriculum()")
  class ConfirmCurriculum {

    @Test
    @DisplayName("커리큘럼 확정 시 초기 1·2강 생성 트리거를 호출한다")
    void confirmCurriculum_triggersInitialLessons() {
      User user = makeUser(USER_ID);
      Course course =
          Course.builder()
              .id(COURSE_ID)
              .user(user)
              .courseName("수학 기초")
              .subject("수학")
              .status(CourseStatus.CURRICULUM_READY)
              .totalChapters(3)
              .completedSlides(0)
              .progressPercent(BigDecimal.ZERO)
              .build();
      Chapter chapter = makeChapter(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findByCourseIdOrderByOrderIndexAsc(COURSE_ID))
          .willReturn(List.of(chapter));
      given(lessonProgressRepository.findByUserIdAndChapterIdIn(USER_ID, List.of(CHAPTER_ID)))
          .willReturn(List.of());

      CurriculumResponse result = courseService.confirmCurriculum(makePrincipal(), COURSE_ID);

      assertThat(result.courseId()).isEqualTo(COURSE_ID);
      assertThat(course.getStatus()).isEqualTo(CourseStatus.ACTIVE);
      verify(progressiveLessonGenerationService).requestInitialLessons(USER_ID, COURSE_ID);
    }
  }

  @Nested
  @DisplayName("uploadSourcePdf()")
  class UploadSourcePdf {

    @Test
    @DisplayName("application/pdf 파일을 S3에 업로드하고 프론트 계약 응답을 반환한다")
    void uploadSourcePdf_validPdf_returnsResponse() {
      // given
      MockMultipartFile file =
          new MockMultipartFile(
              "file", "교재.pdf", "application/pdf", "%PDF-1.4\n".getBytes(StandardCharsets.UTF_8));
      String url = "https://bucket.s3.ap-northeast-2.amazonaws.com/source-pdfs/user/file.pdf";
      given(
              s3Service.upload(
                  eq("source-pdfs/" + USER_ID), anyString(), eq(file), eq("application/pdf")))
          .willReturn(url);

      // when
      SourcePdfUploadResponse result = courseService.uploadSourcePdf(USER_ID, file);

      // then
      assertThat(result.url()).isEqualTo(url);
      assertThat(result.originalFileName()).isEqualTo("교재.pdf");
      assertThat(result.size()).isEqualTo(file.getSize());
      verify(s3Service)
          .upload(eq("source-pdfs/" + USER_ID), anyString(), eq(file), eq("application/pdf"));
    }

    @Test
    @DisplayName("빈 파일이면 CRS_007 예외를 던지고 업로드하지 않는다")
    void uploadSourcePdf_emptyFile_throwsCrs007() {
      // given
      MockMultipartFile file =
          new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

      // when & then
      assertThatThrownBy(() -> courseService.uploadSourcePdf(USER_ID, file))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_007));
      verify(s3Service, never())
          .upload(anyString(), anyString(), any(MultipartFile.class), anyString());
    }

    @Test
    @DisplayName("Content-Type 이 application/pdf 가 아니면 CRS_008 예외를 던진다")
    void uploadSourcePdf_invalidContentType_throwsCrs008() {
      // given
      MockMultipartFile file =
          new MockMultipartFile(
              "file", "source.txt", "text/plain", "%PDF-1.4\n".getBytes(StandardCharsets.UTF_8));

      // when & then
      assertThatThrownBy(() -> courseService.uploadSourcePdf(USER_ID, file))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_008));
    }

    @Test
    @DisplayName("PDF Magic Bytes 가 없으면 CRS_008 예외를 던진다")
    void uploadSourcePdf_invalidMagicBytes_throwsCrs008() {
      // given
      MockMultipartFile file =
          new MockMultipartFile(
              "file", "source.pdf", "application/pdf", "not-pdf".getBytes(StandardCharsets.UTF_8));

      // when & then
      assertThatThrownBy(() -> courseService.uploadSourcePdf(USER_ID, file))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_008));
    }

    @Test
    @DisplayName("100MB 초과 파일이면 CRS_009 예외를 던진다")
    void uploadSourcePdf_tooLarge_throwsCrs009() {
      // given
      MultipartFile file = mock(MultipartFile.class);
      given(file.isEmpty()).willReturn(false);
      given(file.getContentType()).willReturn("application/pdf");
      given(file.getSize()).willReturn(100L * 1024 * 1024 + 1);

      // when & then
      assertThatThrownBy(() -> courseService.uploadSourcePdf(USER_ID, file))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_009));
    }
  }

  @Nested
  @DisplayName("getChapter()")
  class GetChapter {

    @Test
    @DisplayName("강의 상세 조회 시 북마크 빈 목록을 포함해 반환한다")
    void getChapter_returnsDetailWithBookmarks() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      Chapter chapter = makeChapter(course);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(chapterRepository.findById(CHAPTER_ID)).willReturn(Optional.of(chapter));
      given(bookmarkRepository.findByUserIdAndChapterId(USER_ID, CHAPTER_ID)).willReturn(List.of());

      // when
      ChapterDetailResponse result = courseService.getChapter(USER_ID, COURSE_ID, CHAPTER_ID);

      // then
      assertThat(result.id()).isEqualTo(CHAPTER_ID);
      assertThat(result.bookmarks()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 챕터 조회 시 CRS_002 예외를 던진다")
    void getChapter_notFound_throwsCrs002() {
      // given
      User user = makeUser(USER_ID);
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(makeCourse(user)));
      given(chapterRepository.findById(CHAPTER_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> courseService.getChapter(USER_ID, COURSE_ID, CHAPTER_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(CourseErrorCode.CRS_002));
    }
  }

  @Nested
  @DisplayName("getRecentTutoring()")
  class GetRecentTutoring {

    @Test
    @DisplayName("최근 학습 과외 목록을 최근 접근순으로 반환한다")
    void getRecentTutoring_returnsSortedList() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findByUserId(eq(USER_ID), isNull(), isNull(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(course)));

      EntityManager em = mock(EntityManager.class);
      ReflectionTestUtils.setField(courseService, "entityManager", em);

      /* study_time_log 최근 접근 시각 쿼리 목 */
      Query lastAccessQuery = mock(Query.class);
      given(em.createNativeQuery(anyString())).willReturn(lastAccessQuery);
      given(lastAccessQuery.setParameter(eq("userId"), eq(USER_ID))).willReturn(lastAccessQuery);
      Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 5, 15, 10, 30));
      Object[] lastAccessRow = new Object[] {COURSE_ID, ts};
      doReturn(List.<Object[]>of(lastAccessRow)).when(lastAccessQuery).getResultList();

      /* 챕터 통계 JPQL 쿼리 목 */
      Query chapterQuery = mock(Query.class);
      given(em.createQuery(anyString())).willReturn(chapterQuery);
      given(chapterQuery.setParameter(eq("courseIds"), any())).willReturn(chapterQuery);
      Object[] completedRow = new Object[] {COURSE_ID, ChapterStatus.COMPLETED, 1L};
      Object[] availableRow = new Object[] {COURSE_ID, ChapterStatus.AVAILABLE, 2L};
      doReturn(List.of(completedRow, availableRow)).when(chapterQuery).getResultList();

      // when
      RecentTutoringResponse result = courseService.getRecentTutoring(USER_ID);

      // then
      assertThat(result.items()).hasSize(1);
      RecentTutoringResponse.RecentTutoringItem item = result.items().get(0);
      assertThat(item.courseId()).isEqualTo(COURSE_ID);
      assertThat(item.courseName()).isEqualTo("수학 기초");
      assertThat(item.status()).isEqualTo("ACTIVE");
      assertThat(item.progressPercent()).isEqualTo(33); // 1/3 = 33%
      assertThat(item.lastAccessedAt()).isEqualTo(LocalDateTime.of(2026, 5, 15, 10, 30));
    }

    @Test
    @DisplayName("과외가 없을 때 빈 목록을 반환한다")
    void getRecentTutoring_noCourses_returnsEmpty() {
      // given
      given(courseRepository.findByUserId(eq(USER_ID), isNull(), isNull(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of()));

      // when
      RecentTutoringResponse result = courseService.getRecentTutoring(USER_ID);

      // then
      assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("study_time_log 기록이 없는 과외는 updatedAt을 대체 시각으로 사용한다")
    void getRecentTutoring_noStudyLog_usesUpdatedAt() {
      // given
      User user = makeUser(USER_ID);
      Course course = makeCourse(user);
      given(courseRepository.findByUserId(eq(USER_ID), isNull(), isNull(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(course)));

      EntityManager em = mock(EntityManager.class);
      ReflectionTestUtils.setField(courseService, "entityManager", em);

      /* study_time_log 빈 결과 */
      Query lastAccessQuery = mock(Query.class);
      given(em.createNativeQuery(anyString())).willReturn(lastAccessQuery);
      given(lastAccessQuery.setParameter(eq("userId"), eq(USER_ID))).willReturn(lastAccessQuery);
      given(lastAccessQuery.getResultList()).willReturn(List.of());

      /* 챕터 통계 JPQL 빈 결과 */
      Query chapterQuery = mock(Query.class);
      given(em.createQuery(anyString())).willReturn(chapterQuery);
      given(chapterQuery.setParameter(eq("courseIds"), any())).willReturn(chapterQuery);
      given(chapterQuery.getResultList()).willReturn(List.of());

      // when
      RecentTutoringResponse result = courseService.getRecentTutoring(USER_ID);

      // then
      assertThat(result.items()).hasSize(1);
      /* updatedAt은 Course 빌더의 기본값 = LocalDateTime.now() 이므로 null이 아니어야 한다 */
      assertThat(result.items().get(0).lastAccessedAt()).isNotNull();
      assertThat(result.items().get(0).progressPercent()).isZero();
    }
  }
}
