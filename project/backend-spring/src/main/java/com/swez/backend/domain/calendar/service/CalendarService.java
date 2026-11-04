/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.exception.AuthErrorCode;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.calendar.dto.request.CreateCalendarEventRequest;
import com.swez.backend.domain.calendar.dto.request.UpdateCalendarEventRequest;
import com.swez.backend.domain.calendar.dto.response.CalendarEventResponse;
import com.swez.backend.domain.calendar.dto.response.CalendarResponse;
import com.swez.backend.domain.calendar.dto.response.MissionResponse;
import com.swez.backend.domain.calendar.dto.response.StreakResponse;
import com.swez.backend.domain.calendar.entity.CalendarEvent;
import com.swez.backend.domain.calendar.enums.CalendarEventStatus;
import com.swez.backend.domain.calendar.exception.CalendarErrorCode;
import com.swez.backend.domain.calendar.mapper.CalendarMapper;
import com.swez.backend.domain.calendar.repository.CalendarEventRepository;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.ChapterStatus;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.report.repository.StudyTimeLogRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 캘린더 도메인 비즈니스 로직 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CalendarService {

  /** 목표 학습시간이 미설정(null)인 사용자의 기본값(분). ReportService 와 같은 값이다. */
  private static final int DEFAULT_DAILY_STUDY_GOAL_MINUTES = 30;

  private final CalendarEventRepository calendarEventRepository;
  private final CourseRepository courseRepository;
  private final UserRepository userRepository;
  private final ChapterRepository chapterRepository;
  private final StudyTimeLogRepository studyTimeLogRepository;
  private final CalendarMapper calendarMapper;

  /**
   * 월별 캘린더를 조회한다. 이벤트 목록·학습일·연속일을 포함한다.
   *
   * @param month 월 (1~12)
   */
  @Transactional(readOnly = true)
  public CalendarResponse getCalendar(String userId, int year, int month) {
    log.debug(
        "[CalendarService] getCalendar() - START | userId: {}, year: {}, month: {}",
        userId,
        year,
        month);

    LocalDate startDate = LocalDate.of(year, month, 1);
    LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

    List<CalendarEvent> events =
        calendarEventRepository.findByUserIdAndDateBetween(userId, startDate, endDate);
    List<CalendarEventResponse> eventResponses =
        events.stream().map(calendarMapper::toEventResponse).toList();

    /*
        studyDays: 완료 이벤트가 있는 날짜의 일(day) 목록.
    */
    List<Integer> studyDays =
        events.stream()
            .filter(e -> CalendarEventStatus.COMPLETED.equals(e.getStatus()))
            .map(e -> e.getEventDate().getDayOfMonth())
            .distinct()
            .sorted()
            .toList();

    /*
        연속 학습일은 오늘부터 거꾸로 센다.
    */
    int streakDays = calculateStreak(userId);

    CalendarResponse result =
        CalendarResponse.builder()
            .year(year)
            .month(month)
            .events(eventResponses)
            .studyDays(studyDays)
            .streakDays(streakDays)
            .build();

    log.debug("[CalendarService] getCalendar() - END | events: {}", eventResponses.size());
    return result;
  }

  /** 캘린더 이벤트를 생성한다. */
  @Transactional
  public CalendarEventResponse createEvent(String userId, CreateCalendarEventRequest request) {
    log.debug(
        "[CalendarService] createEvent() - START | userId: {}, type: {}", userId, request.type());

    Course course = null;
    if (request.courseId() != null) {
      course =
          courseRepository
              .findById(request.courseId())
              .filter(c -> c.getUser().getId().equals(userId))
              .orElse(null); // 남의 과외이거나 없으면 무시한다. 이벤트는 course 없이도 저장된다.
    }

    CalendarEvent event =
        CalendarEvent.builder()
            .id(IdGenerator.calendarEvent())
            .user(userRepository.getReferenceById(userId))
            .title(request.title())
            .type(request.type())
            .eventDate(request.date())
            .scheduledTime(request.scheduledTime())
            .durationMin(request.durationMin())
            .description(request.description())
            .course(course)
            .build();
    calendarEventRepository.save(event);

    CalendarEventResponse result = calendarMapper.toEventResponse(event);
    log.debug("[CalendarService] createEvent() - END | eventId: {}", event.getId());
    return result;
  }

  /** 캘린더 이벤트를 부분 수정한다. null 필드는 기존 값을 유지한다. */
  @Transactional
  public CalendarEventResponse updateEvent(
      String userId, String eventId, UpdateCalendarEventRequest request) {
    log.debug("[CalendarService] updateEvent() - START | userId: {}, eventId: {}", userId, eventId);

    CalendarEvent event =
        calendarEventRepository
            .findById(eventId)
            .orElseThrow(() -> new CustomException(CalendarErrorCode.CEV_001));
    if (!event.getUser().getId().equals(userId)) {
      throw new CustomException(CalendarErrorCode.CEV_001);
    }

    event.update(
        request.title(),
        request.date(),
        request.scheduledTime(),
        request.durationMin(),
        request.status());
    calendarEventRepository.save(event);

    CalendarEventResponse result = calendarMapper.toEventResponse(event);
    log.debug("[CalendarService] updateEvent() - END | eventId: {}", eventId);
    return result;
  }

  /**
   * 학습 스트릭 히트맵을 조회한다.
   *
   * @param months 조회 기간 (개월 수, 1~12)
   */
  @Transactional(readOnly = true)
  public StreakResponse getStreak(String userId, int months) {
    log.debug("[CalendarService] getStreak() - START | userId: {}, months: {}", userId, months);

    LocalDate since = LocalDate.now().minusMonths(months);
    List<CalendarEvent> completed =
        calendarEventRepository.findCompletedByUserIdSince(userId, since);

    Map<String, Integer> durationByDate = new LinkedHashMap<>();
    DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
    for (CalendarEvent event : completed) {
      String dateKey = event.getEventDate().format(fmt);
      int dur = event.getDurationMin() != null ? event.getDurationMin() : 0;
      durationByDate.merge(dateKey, dur, Integer::sum);
    }
    Map<String, Integer> heatmap =
        durationByDate.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> toIntensity(e.getValue()),
                    (a, b) -> b,
                    LinkedHashMap::new));

    Set<LocalDate> studyDates =
        completed.stream().map(CalendarEvent::getEventDate).collect(Collectors.toSet());
    int currentStreak = calculateStreakFromDates(studyDates, LocalDate.now());
    int longestStreak = calculateLongestStreak(studyDates);

    StreakResponse result =
        StreakResponse.builder()
            .currentStreak(currentStreak)
            .longestStreak(longestStreak)
            .totalStudyDays(studyDates.size())
            .heatmap(heatmap)
            .build();

    log.debug("[CalendarService] getStreak() - END | totalStudyDays: {}", studyDates.size());
    return result;
  }

  /**
   * 오늘의 데일리 미션을 실데이터 기반으로 반환한다. 미션은 2개다.
   *
   * <ul>
   *   <li>mission-daily-01 '오늘 1강의 듣기': 오늘 완료(Chapter.status==COMPLETED, completedAt 기준)한 강의가
   *       1개 이상이면 달성으로 본다. target=1 이므로 current는 0/1로 클램프한다(3강을 들어도 1/1).
   *   <li>mission-daily-03 '오늘 학습 N분': current=오늘 학습시간(study_time_log SUM, 분),
   *       target=User.dailyStudyGoalMinutes(사용자 설정값, null이면 30). title도 target을 반영한다.
   * </ul>
   *
   * <p>"오늘" 경계는 §3 컨벤션대로 반열린 구간 {@code [today 00:00, 익일 00:00)} 으로 처리한다.
   *
   * @return 오늘의 미션 응답 DTO (미션 2개)
   */
  @Transactional(readOnly = true)
  public MissionResponse getMissionsToday(String userId) {
    log.debug("[CalendarService] getMissionsToday() - START | userId: {}", userId);

    /*
        "오늘"은 반열린 구간 [start, end) 이다(start 포함, end 미포함).
        LocalDate.now() 를 쓰므로 서버 시스템 타임존 기준이다.
    */
    LocalDate today = LocalDate.now();
    LocalDateTime start = today.atStartOfDay();
    LocalDateTime end = today.plusDays(1).atStartOfDay();

    /*
        '오늘 1강의 듣기'는 target=1 이라 오늘 완료한 강의 수를 0/1 로 클램프해 표시한다.
    */
    long completedLessonCount =
        chapterRepository.countCompletedByUserInRange(
            userId, ChapterStatus.COMPLETED, start, end);
    boolean lessonMissionCompleted = completedLessonCount >= 1;
    int lessonCurrent = lessonMissionCompleted ? 1 : 0;

    /*
        '오늘 학습 N분'은 current=오늘 학습시간 합(분), target=사용자 목표 학습시간이다.
        목표가 null 이면 기본값 30 을 쓴다. target<=0 이면 이미 달성으로 보고 isCompleted=true 로
        둔다. 진행바의 0 나누기는 프론트가 target>0 인지 확인해 막는다.
    */
    int studyMinutes = studyTimeLogRepository.sumStudyMinutesInRange(userId, start, end);
    User user = findUserById(userId);
    int studyGoalMinutes =
        user.getDailyStudyGoalMinutes() != null
            ? user.getDailyStudyGoalMinutes()
            : DEFAULT_DAILY_STUDY_GOAL_MINUTES;
    boolean studyMissionCompleted =
        studyGoalMinutes <= 0 || studyMinutes >= studyGoalMinutes;

    List<MissionResponse.MissionItem> missions =
        List.of(
            MissionResponse.MissionItem.builder()
                .id("mission-daily-01")
                .title("오늘 1강의 듣기")
                .description("강의를 1개 이상 완료하세요")
                .type("daily")
                .current(lessonCurrent)
                .target(1)
                .xpReward(50)
                .isCompleted(lessonMissionCompleted)
                .build(),
            MissionResponse.MissionItem.builder()
                .id("mission-daily-03")
                .title("오늘 학습 " + studyGoalMinutes + "분")
                .description("총 " + studyGoalMinutes + "분 이상 학습하세요")
                .type("daily")
                .current(studyMinutes)
                .target(studyGoalMinutes)
                .xpReward(30)
                .isCompleted(studyMissionCompleted)
                .build());

    MissionResponse result =
        MissionResponse.builder().date(today.toString()).missions(missions).build();

    log.debug(
        "[CalendarService] getMissionsToday() - END | lessonCurrent: {}, studyMinutes: {}, goal: {}",
        lessonCurrent,
        studyMinutes,
        studyGoalMinutes);
    return result;
  }

  // 내부 헬퍼

  /** userId 로 사용자를 조회한다. 없으면 USER_001 예외를 발생시킨다. */
  private User findUserById(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(AuthErrorCode.USER_001));
  }

  private int calculateStreak(String userId) {
    List<CalendarEvent> completed =
        calendarEventRepository.findCompletedByUserIdSince(userId, LocalDate.now().minusDays(365));
    Set<LocalDate> dates =
        completed.stream().map(CalendarEvent::getEventDate).collect(Collectors.toSet());
    return calculateStreakFromDates(dates, LocalDate.now());
  }

  private int calculateStreakFromDates(Set<LocalDate> dates, LocalDate from) {
    int streak = 0;
    LocalDate cursor = from;
    while (dates.contains(cursor)) {
      streak++;
      cursor = cursor.minusDays(1);
    }
    return streak;
  }

  private int calculateLongestStreak(Set<LocalDate> dates) {
    if (dates.isEmpty()) return 0;
    List<LocalDate> sorted = dates.stream().sorted().toList();
    int longest = 1, current = 1;
    for (int i = 1; i < sorted.size(); i++) {
      if (sorted.get(i).equals(sorted.get(i - 1).plusDays(1))) {
        current++;
        longest = Math.max(longest, current);
      } else {
        current = 1;
      }
    }
    return longest;
  }

  /** duration(분) → intensity 0~4 변환 */
  private int toIntensity(int minutes) {
    if (minutes <= 0) return 0;
    if (minutes <= 30) return 1;
    if (minutes <= 60) return 2;
    if (minutes <= 90) return 3;
    return 4;
  }
}
