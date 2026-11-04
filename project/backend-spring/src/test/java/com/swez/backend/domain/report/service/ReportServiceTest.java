/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.calendar.entity.Assignment;
import com.swez.backend.domain.calendar.entity.Submission;
import com.swez.backend.domain.calendar.repository.SubmissionRepository;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.ChapterStatus;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.lesson.repository.QuizAnswerRepository;
import com.swez.backend.domain.report.dto.response.ConceptUnderstandingResponse;
import com.swez.backend.domain.report.dto.response.ProgressReportResponse;
import com.swez.backend.domain.report.dto.response.StudyTimeReportResponse;
import com.swez.backend.domain.report.dto.response.WeeklyReportResponse;
import com.swez.backend.domain.report.exception.ReportErrorCode;
import com.swez.backend.domain.report.repository.StudyTimeLogRepository;
import com.swez.backend.global.exception.CustomException;

@DisplayName("ReportService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private StudyTimeLogRepository studyTimeLogRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private ChapterRepository chapterRepository;
  @Mock private QuizAnswerRepository quizAnswerRepository;
  @Mock private SubmissionRepository submissionRepository;

  @InjectMocks private ReportService reportService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String COURSE_ID = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String CHAPTER_ID_1 = "chp_01ARZ3NDEKTSV4RRFFQ69G5FA1";
  private static final String CHAPTER_ID_2 = "chp_01ARZ3NDEKTSV4RRFFQ69G5FA2";

  @BeforeEach
  void setUp() {
    given(quizAnswerRepository.findDailyQuizStats(eq(USER_ID), any(), any())).willReturn(List.of());
    given(submissionRepository.findDoneSubmissionsInRange(eq(USER_ID), any(), any()))
        .willReturn(List.of());
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(makeUser()));
  }

  private User makeUser() {
    return User.builder()
        .id(USER_ID)
        .email("test@example.com")
        .nickname("테스터")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .dailyStudyGoalMinutes(30)
        .build();
  }

  private Course makeCourse(User owner, String subject, CourseStatus status) {
    return Course.builder()
        .id(COURSE_ID)
        .user(owner)
        .courseName(subject + " 기초")
        .subject(subject)
        .status(status)
        .totalChapters(2)
        .build();
  }

  private Chapter makeChapter(
      Course course, String chapterId, ChapterStatus status, LocalDateTime completedAt) {
    return Chapter.builder()
        .id(chapterId)
        .course(course)
        .chapterName("챕터")
        .orderIndex(1)
        .totalSlides(10)
        .status(status)
        .xpEarned(0)
        .completedAt(completedAt)
        .build();
  }

  @Nested
  @DisplayName("getWeeklyReport()")
  class GetWeeklyReport {

    @Test
    @DisplayName("week가 null이면 이번 주 기준으로 통계를 반환한다")
    void weeklyReport_nullWeek_returnsCurrentWeek() {
      // given
      LocalDate today = LocalDate.now();
      LocalDate monday = today.with(DayOfWeek.MONDAY);

      given(studyTimeLogRepository.findDailyStudyMinutes(eq(USER_ID), any(), any()))
          .willReturn(List.of(new Object[] {monday, 30L}, new Object[] {monday.plusDays(1), 45L}));

      User user = makeUser();
      Course course = makeCourse(user, "수학", CourseStatus.ACTIVE);
      given(courseRepository.findByUserId(eq(USER_ID), any(), any(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(course)));
      given(chapterRepository.findAllByCourseIdIn(anyList())).willReturn(List.of());

      given(studyTimeLogRepository.findDistinctStudyDates(USER_ID))
          .willReturn(List.of(today, today.minusDays(1)));

      // when
      WeeklyReportResponse result = reportService.getWeeklyReport(USER_ID, null);

      // then
      assertThat(result.weekLabel()).isNotNull();
      assertThat(result.totalStudyMinutes()).isEqualTo(75);
      assertThat(result.dailyStats()).hasSize(7);
      assertThat(result.streakDays()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("ISO 주 형식으로 특정 주의 통계를 반환한다")
    void weeklyReport_specificWeek_returnsStats() {
      // given
      given(studyTimeLogRepository.findDailyStudyMinutes(eq(USER_ID), any(), any()))
          .willReturn(List.of());

      User user = makeUser();
      Course course = makeCourse(user, "수학", CourseStatus.ACTIVE);
      given(courseRepository.findByUserId(eq(USER_ID), any(), any(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(course)));
      given(chapterRepository.findAllByCourseIdIn(anyList())).willReturn(List.of());

      given(studyTimeLogRepository.findDistinctStudyDates(USER_ID)).willReturn(List.of());

      // when
      WeeklyReportResponse result = reportService.getWeeklyReport(USER_ID, "2026-W20");

      // then
      assertThat(result.weekLabel()).isEqualTo("2026-W20");
      assertThat(result.totalStudyMinutes()).isZero();
      assertThat(result.dailyStats()).hasSize(7);
    }

    @Test
    @DisplayName("잘못된 주 형식이면 RPT_001 예외를 던진다")
    void weeklyReport_invalidWeek_throwsRpt001() {
      // when & then
      assertThatThrownBy(() -> reportService.getWeeklyReport(USER_ID, "invalid"))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(ReportErrorCode.RPT_001));
    }

    @Test
    @DisplayName("완료한 챕터가 해당 주에 있으면 completedLessons에 포함된다")
    void weeklyReport_withCompletedLessons_countsCorrectly() {
      // given
      LocalDate monday = LocalDate.of(2026, 5, 11);
      LocalDateTime completedAt = monday.plusDays(2).atTime(14, 0);

      given(studyTimeLogRepository.findDailyStudyMinutes(eq(USER_ID), any(), any()))
          .willReturn(List.of());

      User user = makeUser();
      Course course = makeCourse(user, "수학", CourseStatus.ACTIVE);
      Chapter completedChapter =
          makeChapter(course, CHAPTER_ID_1, ChapterStatus.COMPLETED, completedAt);
      Chapter inProgressChapter =
          makeChapter(course, CHAPTER_ID_2, ChapterStatus.IN_PROGRESS, null);

      given(courseRepository.findByUserId(eq(USER_ID), any(), any(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(course)));
      given(chapterRepository.findAllByCourseIdIn(anyList()))
          .willReturn(List.of(completedChapter, inProgressChapter));

      given(studyTimeLogRepository.findDistinctStudyDates(USER_ID)).willReturn(List.of());

      // when
      WeeklyReportResponse result = reportService.getWeeklyReport(USER_ID, "2026-W20");

      // then
      assertThat(result.completedLessons()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("getProgressReport()")
  class GetProgressReport {

    @Test
    @DisplayName("과목별 진행률을 정확히 계산한다")
    void progressReport_calculatesCorrectly() {
      // given
      User user = makeUser();
      Course mathCourse = makeCourse(user, "수학", CourseStatus.ACTIVE);

      Chapter completedChapter =
          makeChapter(
              mathCourse, CHAPTER_ID_1, ChapterStatus.COMPLETED, LocalDateTime.now().minusDays(1));
      Chapter availableChapter =
          makeChapter(mathCourse, CHAPTER_ID_2, ChapterStatus.AVAILABLE, null);

      given(courseRepository.findByUserId(eq(USER_ID), any(), any(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(mathCourse)));
      given(chapterRepository.findAllByCourseIdIn(anyList()))
          .willReturn(List.of(completedChapter, availableChapter));

      // when
      ProgressReportResponse result = reportService.getProgressReport(USER_ID);

      // then
      assertThat(result.totalCourses()).isEqualTo(1);
      assertThat(result.completedCourses()).isZero();
      assertThat(result.subjects()).hasSize(1);
      assertThat(result.subjects().get(0).subject()).isEqualTo("수학");
      assertThat(result.subjects().get(0).totalChapters()).isEqualTo(2);
      assertThat(result.subjects().get(0).completedChapters()).isEqualTo(1);
      assertThat(result.subjects().get(0).progressPercent()).isEqualTo(50);
    }

    @Test
    @DisplayName("과외가 없으면 빈 결과를 반환한다")
    void progressReport_noCourses_returnsEmpty() {
      // given
      given(courseRepository.findByUserId(eq(USER_ID), any(), any(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of()));

      // when
      ProgressReportResponse result = reportService.getProgressReport(USER_ID);

      // then
      assertThat(result.totalCourses()).isZero();
      assertThat(result.completedCourses()).isZero();
      assertThat(result.subjects()).isEmpty();
    }

    @Test
    @DisplayName("완료된 Course를 completedCourses에 포함시킨다")
    void progressReport_completedCourse_counted() {
      // given
      User user = makeUser();
      Course completedCourse =
          Course.builder()
              .id(COURSE_ID)
              .user(user)
              .courseName("영어 기초")
              .subject("영어")
              .status(CourseStatus.COMPLETED)
              .totalChapters(1)
              .build();

      given(courseRepository.findByUserId(eq(USER_ID), any(), any(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(completedCourse)));
      given(chapterRepository.findAllByCourseIdIn(anyList())).willReturn(List.of());

      // when
      ProgressReportResponse result = reportService.getProgressReport(USER_ID);

      // then
      assertThat(result.completedCourses()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("getStudyTimeReport()")
  class GetStudyTimeReport {

    @Test
    @DisplayName("기본 30d 기간의 학습 시간을 반환한다")
    void studyTimeReport_default30d_returnsData() {
      // given
      LocalDate today = LocalDate.now();
      given(studyTimeLogRepository.findDailyStudyMinutes(eq(USER_ID), any(), any()))
          .willReturn(List.of(new Object[] {today, 60L}, new Object[] {today.minusDays(1), 30L}));

      // when
      StudyTimeReportResponse result = reportService.getStudyTimeReport(USER_ID, null);

      // then
      assertThat(result.period()).isEqualTo("30d");
      assertThat(result.totalMinutes()).isEqualTo(90);
      assertThat(result.dailyData()).hasSize(30);
      assertThat(result.averageMinutesPerDay()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("7d 기간으로 학습 시간을 반환한다")
    void studyTimeReport_7d_returnsCorrectDays() {
      // given
      given(studyTimeLogRepository.findDailyStudyMinutes(eq(USER_ID), any(), any()))
          .willReturn(List.of());

      // when
      StudyTimeReportResponse result = reportService.getStudyTimeReport(USER_ID, "7d");

      // then
      assertThat(result.period()).isEqualTo("7d");
      assertThat(result.dailyData()).hasSize(7);
      assertThat(result.totalMinutes()).isZero();
    }

    @Test
    @DisplayName("90d 기간으로 학습 시간을 반환한다")
    void studyTimeReport_90d_returnsCorrectDays() {
      // given
      given(studyTimeLogRepository.findDailyStudyMinutes(eq(USER_ID), any(), any()))
          .willReturn(List.of());

      // when
      StudyTimeReportResponse result = reportService.getStudyTimeReport(USER_ID, "90d");

      // then
      assertThat(result.period()).isEqualTo("90d");
      assertThat(result.dailyData()).hasSize(90);
    }

    @Test
    @DisplayName("잘못된 기간이면 RPT_002 예외를 던진다")
    void studyTimeReport_invalidPeriod_throwsRpt002() {
      // when & then
      assertThatThrownBy(() -> reportService.getStudyTimeReport(USER_ID, "15d"))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(ReportErrorCode.RPT_002));
    }
  }

  @Nested
  @DisplayName("주간 리포트 — 과제 통계 + 목표 시간")
  class WeeklyReportAssignment {

    @Test
    @DisplayName("완료된 과제가 있으면 통계에 포함된다")
    void weeklyReport_withDoneSubmissions_calculatesStats() {
      // given
      given(studyTimeLogRepository.findDailyStudyMinutes(eq(USER_ID), any(), any()))
          .willReturn(List.of());
      User user = makeUser();
      Course course = makeCourse(user, "수학", CourseStatus.ACTIVE);
      given(courseRepository.findByUserId(eq(USER_ID), any(), any(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(course)));
      given(chapterRepository.findAllByCourseIdIn(anyList())).willReturn(List.of());
      given(studyTimeLogRepository.findDistinctStudyDates(USER_ID)).willReturn(List.of());

      Assignment assignment = Assignment.builder().id("asg_01").user(user).maxScore(100).build();
      Submission sub1 =
          Submission.builder()
              .id("sub_01")
              .assignment(assignment)
              .user(user)
              .status("done")
              .officialScore(80)
              .build();
      Submission sub2 =
          Submission.builder()
              .id("sub_02")
              .assignment(assignment)
              .user(user)
              .status("done")
              .officialScore(60)
              .build();
      given(submissionRepository.findDoneSubmissionsInRange(eq(USER_ID), any(), any()))
          .willReturn(List.of(sub1, sub2));

      // when
      WeeklyReportResponse result = reportService.getWeeklyReport(USER_ID, null);

      // then
      assertThat(result.completedAssignmentCount()).isEqualTo(2);
      assertThat(result.assignmentAvgScore()).isEqualTo(70);
    }

    @Test
    @DisplayName("dailyStudyGoalMinutes가 주간 리포트에 포함된다")
    void weeklyReport_includesDailyStudyGoalMinutes() {
      // given
      User user =
          User.builder()
              .id(USER_ID)
              .email("test@example.com")
              .nickname("테스터")
              .subscriptionStatus(SubscriptionStatus.FREE)
              .dailyStudyGoalMinutes(60)
              .build();
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

      given(studyTimeLogRepository.findDailyStudyMinutes(eq(USER_ID), any(), any()))
          .willReturn(List.of());
      Course course = makeCourse(user, "수학", CourseStatus.ACTIVE);
      given(courseRepository.findByUserId(eq(USER_ID), any(), any(), any(Pageable.class)))
          .willReturn(new PageImpl<>(List.of(course)));
      given(chapterRepository.findAllByCourseIdIn(anyList())).willReturn(List.of());
      given(studyTimeLogRepository.findDistinctStudyDates(USER_ID)).willReturn(List.of());

      // when
      WeeklyReportResponse result = reportService.getWeeklyReport(USER_ID, null);

      // then
      assertThat(result.dailyStudyGoalMinutes()).isEqualTo(60);
    }
  }

  @Nested
  @DisplayName("getConceptUnderstanding()")
  class GetConceptUnderstanding {

    @Test
    @DisplayName("챕터별 퀴즈 정답률을 정확히 계산한다")
    void conceptUnderstanding_calculatesAccuracy() {
      // given
      given(quizAnswerRepository.findChapterQuizAccuracy(USER_ID, COURSE_ID))
          .willReturn(
              List.of(
                  new Object[] {CHAPTER_ID_1, "1단원", 10L, 8L},
                  new Object[] {CHAPTER_ID_2, "2단원", 5L, 3L}));

      // when
      ConceptUnderstandingResponse result =
          reportService.getConceptUnderstanding(USER_ID, COURSE_ID);

      // then
      assertThat(result.chapters()).hasSize(2);
      assertThat(result.chapters().get(0).accuracyPercent()).isEqualTo(80);
      assertThat(result.chapters().get(1).accuracyPercent()).isEqualTo(60);
      assertThat(result.overallAccuracy()).isEqualTo(73);
    }

    @Test
    @DisplayName("퀴즈 답변이 없으면 빈 결과를 반환한다")
    void conceptUnderstanding_noAnswers_returnsEmpty() {
      // given
      given(quizAnswerRepository.findChapterQuizAccuracy(USER_ID, COURSE_ID)).willReturn(List.of());

      // when
      ConceptUnderstandingResponse result =
          reportService.getConceptUnderstanding(USER_ID, COURSE_ID);

      // then
      assertThat(result.chapters()).isEmpty();
      assertThat(result.overallAccuracy()).isZero();
    }
  }
}
