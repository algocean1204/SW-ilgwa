/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.ChapterStatus;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.lesson.repository.SlideRepository;

@DisplayName("ProgressiveLessonGenerationService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ProgressiveLessonGenerationServiceTest {

  @Mock private CourseRepository courseRepository;
  @Mock private ChapterRepository chapterRepository;
  @Mock private SlideRepository slideRepository;
  @Mock private RestTemplate restTemplate;

  @InjectMocks private ProgressiveLessonGenerationService progressiveLessonGenerationService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String COURSE_ID = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String CHAPTER_1_ID = "chp_01ARZ3NDEKTSV4RRFFQ69G5FA1";
  private static final String CHAPTER_2_ID = "chp_01ARZ3NDEKTSV4RRFFQ69G5FA2";
  private static final String CHAPTER_3_ID = "chp_01ARZ3NDEKTSV4RRFFQ69G5FA3";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        progressiveLessonGenerationService, "fastapiBaseUrl", "http://fastapi");
  }

  @Test
  @DisplayName("초기 생성은 order_index 기준 첫 2강만 generate-one으로 요청한다")
  void requestInitialLessons_callsOnlyFirstTwoLessons() {
    User user = makeUser();
    Course course = makeCourse(user);
    Chapter chapter1 = makeChapter(course, CHAPTER_1_ID, 1);
    Chapter chapter2 = makeChapter(course, CHAPTER_2_ID, 2);
    Chapter chapter3 = makeChapter(course, CHAPTER_3_ID, 3);

    given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
    given(chapterRepository.findByCourseIdOrderByOrderIndexAsc(COURSE_ID))
        .willReturn(List.of(chapter1, chapter2, chapter3));
    given(slideRepository.existsByChapterId(any())).willReturn(false);

    progressiveLessonGenerationService.requestInitialLessons(USER_ID, COURSE_ID);

    ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
    verify(restTemplate, times(2))
        .postForEntity(
            eq("http://fastapi/api/lessons/generate-one"), bodyCaptor.capture(), eq(Void.class));
    List<Map> bodies = bodyCaptor.getAllValues();
    assertThat(bodies)
        .extracting(body -> body.get("lessonId"))
        .containsExactly(CHAPTER_1_ID, CHAPTER_2_ID);
    assertThat(bodies.get(0).get("courseId")).isEqualTo(COURSE_ID);
    assertThat(bodies.get(0).get("audienceLevel")).isEqualTo("고등학교 3학년");
    assertThat(bodies.get(0).get("learningGoal")).isEqualTo("수능 대비");
    assertThat(bodies.get(0).get("tone")).isEqualTo(50);
    assertThat(bodies.get(0).get("pace")).isEqualTo(50);
    assertThat(bodies.get(0).get("tutorDepth")).isEqualTo(50);
    assertThat(bodies.get(0).get("socratic")).isEqualTo(50);
  }

  @Test
  @DisplayName("1강 완료 시 3강을 K+2 대상으로 generate-one 요청한다")
  void triggerNextLessonAfterCompletion_callsKPlusTwoLesson() {
    User user = makeUser();
    Course course = makeCourse(user);
    Chapter completedChapter = makeChapter(course, CHAPTER_1_ID, 1);
    Chapter nextChapter = makeChapter(course, CHAPTER_3_ID, 3);

    given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
    given(chapterRepository.findById(CHAPTER_1_ID)).willReturn(Optional.of(completedChapter));
    given(chapterRepository.findByCourseIdAndOrderIndex(COURSE_ID, 3))
        .willReturn(Optional.of(nextChapter));
    given(slideRepository.existsByChapterId(CHAPTER_3_ID)).willReturn(false);

    progressiveLessonGenerationService.triggerNextLessonAfterCompletion(
        USER_ID, COURSE_ID, CHAPTER_1_ID);

    ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
    verify(restTemplate)
        .postForEntity(
            eq("http://fastapi/api/lessons/generate-one"), bodyCaptor.capture(), eq(Void.class));
    assertThat(bodyCaptor.getValue().get("lessonId")).isEqualTo(CHAPTER_3_ID);
  }

  @Test
  @DisplayName("K+2 강의 슬라이드가 이미 있으면 중복 생성 요청을 건너뛴다")
  void triggerNextLessonAfterCompletion_skipsGeneratedLesson() {
    User user = makeUser();
    Course course = makeCourse(user);
    Chapter completedChapter = makeChapter(course, CHAPTER_1_ID, 1);
    Chapter nextChapter = makeChapter(course, CHAPTER_3_ID, 3);

    given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
    given(chapterRepository.findById(CHAPTER_1_ID)).willReturn(Optional.of(completedChapter));
    given(chapterRepository.findByCourseIdAndOrderIndex(COURSE_ID, 3))
        .willReturn(Optional.of(nextChapter));
    given(slideRepository.existsByChapterId(CHAPTER_3_ID)).willReturn(true);

    progressiveLessonGenerationService.triggerNextLessonAfterCompletion(
        USER_ID, COURSE_ID, CHAPTER_1_ID);

    verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(Void.class));
  }

  private User makeUser() {
    return User.builder()
        .id(USER_ID)
        .email("test@example.com")
        .nickname("테스터")
        .gradeLevel("고등학교 3학년")
        .learningGoal("수능 대비")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  private Course makeCourse(User user) {
    return Course.builder()
        .id(COURSE_ID)
        .user(user)
        .courseName("수학 기초")
        .subject("수학")
        .status(CourseStatus.ACTIVE)
        .totalChapters(3)
        .completedSlides(0)
        .progressPercent(BigDecimal.ZERO)
        .build();
  }

  private Chapter makeChapter(Course course, String chapterId, int orderIndex) {
    return Chapter.builder()
        .id(chapterId)
        .course(course)
        .chapterName(orderIndex + "강")
        .orderIndex(orderIndex)
        .totalSlides(0)
        .status(ChapterStatus.AVAILABLE)
        .xpEarned(0)
        .build();
  }
}
