/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.util.List;

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

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.calendar.dto.request.CreateCalendarEventRequest;
import com.swez.backend.domain.calendar.dto.response.CalendarResponse;
import com.swez.backend.domain.calendar.dto.response.MissionResponse;
import com.swez.backend.domain.calendar.dto.response.StreakResponse;
import com.swez.backend.domain.calendar.entity.CalendarEvent;
import com.swez.backend.domain.calendar.enums.CalendarEventStatus;
import com.swez.backend.domain.calendar.enums.CalendarEventType;
import com.swez.backend.domain.calendar.mapper.CalendarMapper;
import com.swez.backend.domain.calendar.repository.CalendarEventRepository;
import com.swez.backend.domain.course.enums.ChapterStatus;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.report.repository.StudyTimeLogRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@DisplayName("CalendarService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarServiceTest {

  @Mock private CalendarEventRepository calendarEventRepository;
  @Mock private UserRepository userRepository;
  @Mock private ChapterRepository chapterRepository;
  @Mock private StudyTimeLogRepository studyTimeLogRepository;
  @Mock private CalendarMapper calendarMapper;

  @InjectMocks private CalendarService calendarService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(calendarService, "calendarMapper", new CalendarMapper());
  }

  private User makeUser() {
    return User.builder()
        .id(USER_ID)
        .email("t@t.com")
        .nickname("테스터")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  private CalendarEvent makeCompletedEvent(LocalDate date, int durationMin) {
    return CalendarEvent.builder()
        .id("cev_" + date.toString().replace("-", ""))
        .user(makeUser())
        .title("강의")
        .type(CalendarEventType.LESSON)
        .eventDate(date)
        .durationMin(durationMin)
        .status(CalendarEventStatus.COMPLETED)
        .build();
  }

  @Nested
  @DisplayName("getCalendar()")
  class GetCalendar {

    @Test
    @DisplayName("완료 이벤트가 있는 날짜를 studyDays 로 반환한다")
    void getCalendar_completedEvents_returnsStudyDays() {
      // given
      LocalDate today = LocalDate.of(2026, 4, 27);
      CalendarEvent completed = makeCompletedEvent(today, 60);
      given(
              calendarEventRepository.findByUserIdAndDateBetween(
                  eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
          .willReturn(List.of(completed));
      given(calendarEventRepository.findCompletedByUserIdSince(eq(USER_ID), any(LocalDate.class)))
          .willReturn(List.of(completed));

      // when
      CalendarResponse result = calendarService.getCalendar(USER_ID, 2026, 4);

      // then
      assertThat(result.studyDays()).contains(27);
      assertThat(result.events()).hasSize(1);
    }

    @Test
    @DisplayName("이벤트 없으면 빈 결과를 반환한다")
    void getCalendar_noEvents_returnsEmpty() {
      // given
      given(
              calendarEventRepository.findByUserIdAndDateBetween(
                  eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
          .willReturn(List.of());
      given(calendarEventRepository.findCompletedByUserIdSince(eq(USER_ID), any(LocalDate.class)))
          .willReturn(List.of());

      // when
      CalendarResponse result = calendarService.getCalendar(USER_ID, 2026, 4);

      // then
      assertThat(result.events()).isEmpty();
      assertThat(result.studyDays()).isEmpty();
      assertThat(result.streakDays()).isZero();
    }
  }

  @Nested
  @DisplayName("createEvent()")
  class CreateEvent {

    @Test
    @DisplayName("이벤트를 저장하고 반환한다")
    void createEvent_success() {
      // given
      given(userRepository.getReferenceById(USER_ID)).willReturn(makeUser());
      given(calendarEventRepository.save(any(CalendarEvent.class)))
          .willAnswer(inv -> inv.getArgument(0));

      CreateCalendarEventRequest request =
          new CreateCalendarEventRequest(
              "1강 복습", CalendarEventType.LESSON, LocalDate.of(2026, 5, 1), null, 60, null, null);

      // when
      var result = calendarService.createEvent(USER_ID, request);

      // then
      assertThat(result.title()).isEqualTo("1강 복습");
      then(calendarEventRepository).should().save(any(CalendarEvent.class));
    }
  }

  @Nested
  @DisplayName("getStreak()")
  class GetStreak {

    @Test
    @DisplayName("intensity 계산 — 31분 이상은 intensity 2")
    void getStreak_intensityLevel2() {
      // given: 오늘 45분 완료 이벤트
      CalendarEvent event = makeCompletedEvent(LocalDate.now(), 45);
      given(calendarEventRepository.findCompletedByUserIdSince(eq(USER_ID), any(LocalDate.class)))
          .willReturn(List.of(event));

      // when
      StreakResponse result = calendarService.getStreak(USER_ID, 1);

      // then
      assertThat(result.totalStudyDays()).isEqualTo(1);
      assertThat(result.heatmap()).containsValue(2);
    }
  }

  @Nested
  @DisplayName("getMissionsToday()")
  class GetMissionsToday {

    @Test
    @DisplayName("미션 2개(1강의 듣기/학습 N분)를 실데이터로 반환한다")
    void getMissionsToday_returnsTwoMissionsWithRealData() {
      // given: 오늘 2강 완료(클램프되어 1/1), 오늘 학습 45분, 목표 60분
      User user = User.builder().id(USER_ID).dailyStudyGoalMinutes(60).build();
      given(
              chapterRepository.countCompletedByUserInRange(
                  eq(USER_ID),
                  eq(ChapterStatus.COMPLETED),
                  any(LocalDateTime.class),
                  any(LocalDateTime.class)))
          .willReturn(2L);
      given(
              studyTimeLogRepository.sumStudyMinutesInRange(
                  eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
          .willReturn(45);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

      // when
      MissionResponse result = calendarService.getMissionsToday(USER_ID);

      // then: 데일리 미션은 2개만 반환한다
      assertThat(result.missions()).hasSize(2);

      // 1강의 듣기: 2강을 들어도 0/1로 클램프 → current=1, isCompleted=true
      MissionResponse.MissionItem lesson = result.missions().get(0);
      assertThat(lesson.id()).isEqualTo("mission-daily-01");
      assertThat(lesson.current()).isEqualTo(1);
      assertThat(lesson.target()).isEqualTo(1);
      assertThat(lesson.isCompleted()).isTrue();

      // 학습 N분: current=45, target=사용자 설정값 60, title도 target 반영, 미달이므로 미완료
      MissionResponse.MissionItem study = result.missions().get(1);
      assertThat(study.id()).isEqualTo("mission-daily-03");
      assertThat(study.current()).isEqualTo(45);
      assertThat(study.target()).isEqualTo(60);
      assertThat(study.title()).isEqualTo("오늘 학습 60분");
      assertThat(study.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("오늘 완료 강의 0개면 1강의 미션은 current=0·미완료")
    void getMissionsToday_noCompletedLesson_returnsZero() {
      // given: 오늘 완료 강의 0개, 목표시간 미설정(null) → 기본 30분, 학습 30분으로 달성
      User user = User.builder().id(USER_ID).build();
      ReflectionTestUtils.setField(user, "dailyStudyGoalMinutes", null);
      given(
              chapterRepository.countCompletedByUserInRange(
                  eq(USER_ID),
                  eq(ChapterStatus.COMPLETED),
                  any(LocalDateTime.class),
                  any(LocalDateTime.class)))
          .willReturn(0L);
      given(
              studyTimeLogRepository.sumStudyMinutesInRange(
                  eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
          .willReturn(30);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

      // when
      MissionResponse result = calendarService.getMissionsToday(USER_ID);

      // then
      MissionResponse.MissionItem lesson = result.missions().get(0);
      assertThat(lesson.current()).isZero();
      assertThat(lesson.isCompleted()).isFalse();

      // 목표시간 null → 기본 30, 학습 30분이므로 달성
      MissionResponse.MissionItem study = result.missions().get(1);
      assertThat(study.target()).isEqualTo(30);
      assertThat(study.title()).isEqualTo("오늘 학습 30분");
      assertThat(study.isCompleted()).isTrue();
    }
  }
}
