/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.report.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
import com.swez.backend.domain.report.dto.response.ConceptUnderstandingResponse.ChapterAccuracy;
import com.swez.backend.domain.report.dto.response.ProgressReportResponse;
import com.swez.backend.domain.report.dto.response.ProgressReportResponse.SubjectProgress;
import com.swez.backend.domain.report.dto.response.StudyTimeReportResponse;
import com.swez.backend.domain.report.dto.response.StudyTimeReportResponse.DailyStudyTime;
import com.swez.backend.domain.report.dto.response.WeeklyReportResponse;
import com.swez.backend.domain.report.dto.response.WeeklyReportResponse.DailyStat;
import com.swez.backend.domain.report.exception.ReportErrorCode;
import com.swez.backend.domain.report.repository.StudyTimeLogRepository;
import com.swez.backend.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 학습 리포트의 주간 통계, 과목별 진행률, 학습 시간 추이를 집계한다. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

  private final UserRepository userRepository;
  private final StudyTimeLogRepository studyTimeLogRepository;
  private final CourseRepository courseRepository;
  private final ChapterRepository chapterRepository;
  private final QuizAnswerRepository quizAnswerRepository;
  private final SubmissionRepository submissionRepository;

  /**
   * 주간 학습 통계를 조회한다.
   *
   * <p>일별 학습 시간, 완료한 챕터 수, 연속 학습일을 계산한다.
   *
   * @param week ISO 주 형식 문자열 (예: "2026-W20"), null이면 이번 주
   */
  public WeeklyReportResponse getWeeklyReport(String userId, String week) {
    log.debug("[ReportService] getWeeklyReport() - START | userId: {}, week: {}", userId, week);

    /*
        ISO 주 형식에서 월~일 범위를 뽑는다.
        null이면 이번 주를 쓰고, 형식이 잘못되면 RPT_001을 던진다.
    */
    LocalDate monday = parseWeekToMonday(week);
    LocalDate sunday = monday.plusDays(6);
    String weekLabel = buildWeekLabel(monday);

    LocalDateTime weekStart = monday.atStartOfDay();
    LocalDateTime weekEnd = sunday.atTime(LocalTime.MAX);

    List<Object[]> rawDailyData =
        studyTimeLogRepository.findDailyStudyMinutes(userId, weekStart, weekEnd);
    Map<LocalDate, Integer> dailyMap = toDailyMap(rawDailyData);

    List<Object[]> rawQuizData =
        quizAnswerRepository.findDailyQuizStats(userId, weekStart, weekEnd);
    Map<LocalDate, int[]> quizMap = toQuizMap(rawQuizData);

    List<DailyStat> dailyStats = new ArrayList<>();
    int totalStudyMinutes = 0;
    int totalQuizCount = 0;
    int totalCorrectCount = 0;
    for (LocalDate d = monday; !d.isAfter(sunday); d = d.plusDays(1)) {
      int minutes = dailyMap.getOrDefault(d, 0);
      int[] quiz = quizMap.getOrDefault(d, new int[] {0, 0});
      int quizCount = quiz[0];
      int correctCount = quiz[1];
      int averageScore =
          quizCount > 0 ? (int) Math.round((double) correctCount / quizCount * 100) : 0;
      dailyStats.add(
          DailyStat.builder()
              .date(d)
              .studyMinutes(minutes)
              .quizCount(quizCount)
              .averageScore(averageScore)
              .build());
      totalStudyMinutes += minutes;
      totalQuizCount += quizCount;
      totalCorrectCount += correctCount;
    }
    int averageScorePercent =
        totalQuizCount > 0
            ? (int) Math.round((double) totalCorrectCount / totalQuizCount * 100)
            : 0;

    /*
        사용자의 모든 Course → Chapter 중 completedAt이 해당 주 범위인 것을 센다.
        loadUserCourses()는 한 번만 불러 같은 DB 조회를 되풀이하지 않는다.
    */
    List<Course> courses = loadUserCourses(userId);
    int completedLessons = countCompletedChaptersInRange(courses, weekStart, weekEnd);

    /* 과제 통계는 해당 주에 채점이 끝난 제출물을 기준으로 한다. */
    List<Submission> doneSubmissions =
        submissionRepository.findDoneSubmissionsInRange(userId, weekStart, weekEnd);
    int completedAssignmentCount = doneSubmissions.size();
    int assignmentAvgScore = calculateAssignmentAvgScore(doneSubmissions);

    int streakDays = calculateStreakDays(userId);

    User user = findUserById(userId);

    WeeklyReportResponse result =
        WeeklyReportResponse.builder()
            .weekLabel(weekLabel)
            .dailyStudyGoalMinutes(
                user.getDailyStudyGoalMinutes() != null ? user.getDailyStudyGoalMinutes() : 30)
            .totalStudyMinutes(totalStudyMinutes)
            .totalQuizCount(totalQuizCount)
            .averageScorePercent(averageScorePercent)
            .completedLessons(completedLessons)
            .completedAssignmentCount(completedAssignmentCount)
            .assignmentAvgScore(assignmentAvgScore)
            .streakDays(streakDays)
            .dailyStats(dailyStats)
            .build();

    log.debug(
        "[ReportService] getWeeklyReport() - END | totalMinutes: {}, completedLessons: {},"
            + " streakDays: {}, totalQuizCount: {}, avgScore: {}",
        totalStudyMinutes,
        completedLessons,
        streakDays,
        totalQuizCount,
        averageScorePercent);
    return result;
  }

  /**
   * 과목별 진행률을 조회한다.
   *
   * <p>사용자의 모든 Course(DELETED 제외)를 subject별로 묶고, 과목마다 전체 챕터 수와 완료 챕터 수로
   * 진행률을 계산한다.
   */
  public ProgressReportResponse getProgressReport(String userId) {
    log.debug("[ReportService] getProgressReport() - START | userId: {}", userId);

    List<Course> courses = loadUserCourses(userId);

    int totalCourses = courses.size();
    int completedCourses =
        (int) courses.stream().filter(c -> c.getStatus() == CourseStatus.COMPLETED).count();

    /*
        N+1이 나지 않게 모든 Course의 Chapter를 벌크 쿼리 한 번으로 조회한다.
        이후 courseId 기준으로 메모리에서 묶어 과목별로 집계한다.
    */
    List<String> courseIds = courses.stream().map(Course::getId).toList();
    Map<String, List<Chapter>> chaptersByCourseId =
        courseIds.isEmpty()
            ? Collections.emptyMap()
            : chapterRepository.findAllByCourseIdIn(courseIds).stream()
                .collect(Collectors.groupingBy(ch -> ch.getCourse().getId()));

    Map<String, List<Course>> coursesBySubject =
        courses.stream()
            .collect(
                Collectors.groupingBy(
                    c -> c.getSubject() != null ? c.getSubject() : "기타",
                    LinkedHashMap::new,
                    Collectors.toList()));

    List<SubjectProgress> subjects = new ArrayList<>();
    for (Map.Entry<String, List<Course>> entry : coursesBySubject.entrySet()) {
      int subjectTotalChapters = 0;
      int subjectCompletedChapters = 0;

      for (Course course : entry.getValue()) {
        /* 개별 쿼리 없이 벌크 조회 결과에서 courseId로 챕터를 꺼낸다. */
        List<Chapter> chapters =
            chaptersByCourseId.getOrDefault(course.getId(), Collections.emptyList());
        subjectTotalChapters += chapters.size();
        subjectCompletedChapters +=
            (int) chapters.stream().filter(ch -> ch.getStatus() == ChapterStatus.COMPLETED).count();
      }

      int percent =
          subjectTotalChapters > 0
              ? (int) Math.round((double) subjectCompletedChapters / subjectTotalChapters * 100)
              : 0;

      subjects.add(
          SubjectProgress.builder()
              .subject(entry.getKey())
              .totalChapters(subjectTotalChapters)
              .completedChapters(subjectCompletedChapters)
              .progressPercent(percent)
              .build());
    }

    ProgressReportResponse result =
        ProgressReportResponse.builder()
            .totalCourses(totalCourses)
            .completedCourses(completedCourses)
            .subjects(subjects)
            .build();

    log.debug(
        "[ReportService] getProgressReport() - END | totalCourses: {}, completedCourses: {}",
        totalCourses,
        completedCourses);
    return result;
  }

  /**
   * 학습 시간 추이를 조회한다.
   *
   * <p>period에 따라 7일/30일/90일 기간의 일별 학습 시간을 조회하고, 총 학습 시간과 일 평균을 계산한다.
   *
   * @param period 조회 기간 문자열 ("7d", "30d", "90d"), null이면 "30d"
   */
  public StudyTimeReportResponse getStudyTimeReport(String userId, String period) {
    log.debug(
        "[ReportService] getStudyTimeReport() - START | userId: {}, period: {}", userId, period);

    /* period가 null이면 30d를 쓰고, 형식이 잘못되면 RPT_002를 던진다. */
    String resolvedPeriod = period != null ? period : "30d";
    int days = parsePeriodToDays(resolvedPeriod);

    LocalDate today = LocalDate.now();
    LocalDate startDate = today.minusDays(days - 1);

    LocalDateTime start = startDate.atStartOfDay();
    LocalDateTime end = today.atTime(LocalTime.MAX);

    List<Object[]> rawDailyData = studyTimeLogRepository.findDailyStudyMinutes(userId, start, end);
    Map<LocalDate, Integer> dailyMap = toDailyMap(rawDailyData);

    List<DailyStudyTime> dailyData = new ArrayList<>();
    int totalMinutes = 0;
    for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
      int minutes = dailyMap.getOrDefault(d, 0);
      dailyData.add(DailyStudyTime.builder().date(d).minutes(minutes).build());
      totalMinutes += minutes;
    }

    double averageMinutesPerDay =
        days > 0 ? Math.round((double) totalMinutes / days * 10) / 10.0 : 0;

    List<Object[]> rawQuizData = quizAnswerRepository.findDailyQuizStats(userId, start, end);
    int totalQuizCount = 0;
    int totalCorrectCount = 0;
    for (Object[] row : rawQuizData) {
      totalQuizCount += ((Number) row[1]).intValue();
      totalCorrectCount += ((Number) row[2]).intValue();
    }
    int averageScorePercent =
        totalQuizCount > 0
            ? (int) Math.round((double) totalCorrectCount / totalQuizCount * 100)
            : 0;

    StudyTimeReportResponse result =
        StudyTimeReportResponse.builder()
            .period(resolvedPeriod)
            .totalMinutes(totalMinutes)
            .averageMinutesPerDay(averageMinutesPerDay)
            .totalQuizCount(totalQuizCount)
            .averageScorePercent(averageScorePercent)
            .dailyData(dailyData)
            .build();

    log.debug(
        "[ReportService] getStudyTimeReport() - END | totalMinutes: {}, avg: {}, quizCount: {},"
            + " avgScore: {}",
        totalMinutes,
        averageMinutesPerDay,
        totalQuizCount,
        averageScorePercent);
    return result;
  }

  /** 코스에 속한 챕터별 퀴즈 정답률을 집계해 개념 이해도를 조회한다. */
  public ConceptUnderstandingResponse getConceptUnderstanding(String userId, String courseId) {
    log.debug(
        "[ReportService] getConceptUnderstanding() - START | userId: {}, courseId: {}",
        userId,
        courseId);

    List<Object[]> rawData = quizAnswerRepository.findChapterQuizAccuracy(userId, courseId);

    int totalAll = 0;
    int correctAll = 0;
    List<ChapterAccuracy> chapters = new ArrayList<>();

    for (Object[] row : rawData) {
      String chapterId = (String) row[0];
      String chapterTitle = (String) row[1];
      int totalCount = ((Number) row[2]).intValue();
      int correctCount = ((Number) row[3]).intValue();
      int accuracy =
          totalCount > 0 ? (int) Math.round((double) correctCount / totalCount * 100) : 0;

      chapters.add(
          ChapterAccuracy.builder()
              .chapterId(chapterId)
              .chapterTitle(chapterTitle)
              .totalQuizCount(totalCount)
              .correctCount(correctCount)
              .accuracyPercent(accuracy)
              .build());

      totalAll += totalCount;
      correctAll += correctCount;
    }

    int overallAccuracy = totalAll > 0 ? (int) Math.round((double) correctAll / totalAll * 100) : 0;

    ConceptUnderstandingResponse result =
        ConceptUnderstandingResponse.builder()
            .overallAccuracy(overallAccuracy)
            .chapters(chapters)
            .build();

    log.debug(
        "[ReportService] getConceptUnderstanding() - END | chapters: {}, overall: {}",
        chapters.size(),
        overallAccuracy);
    return result;
  }

  // 내부 헬퍼 메서드

  /**
   * ISO 주 형식 문자열을 해당 주 월요일 LocalDate로 파싱한다.
   *
   * @param week "2026-W20" 형식, null이면 이번 주 월요일
   */
  private LocalDate parseWeekToMonday(String week) {
    if (week == null || week.isBlank()) {
      return LocalDate.now().with(DayOfWeek.MONDAY);
    }
    try {
      /* ISO 주 형식 "yyyy-Www"를 연도와 주 번호로 나눈다. */
      String[] parts = week.split("-W");
      if (parts.length != 2) {
        throw new CustomException(ReportErrorCode.RPT_001);
      }
      int year = Integer.parseInt(parts[0]);
      int weekNumber = Integer.parseInt(parts[1]);

      WeekFields weekFields = WeekFields.ISO;
      return LocalDate.now()
          .withYear(year)
          .with(weekFields.weekOfWeekBasedYear(), weekNumber)
          .with(DayOfWeek.MONDAY);
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new CustomException(ReportErrorCode.RPT_001);
    }
  }

  /**
   * 월요일 날짜로 ISO 주 라벨을 만든다.
   *
   * @return "yyyy-Www" 형식 문자열
   */
  private String buildWeekLabel(LocalDate monday) {
    WeekFields weekFields = WeekFields.ISO;
    int year = monday.get(weekFields.weekBasedYear());
    int weekNumber = monday.get(weekFields.weekOfWeekBasedYear());
    return String.format("%d-W%02d", year, weekNumber);
  }

  /**
   * period 문자열을 일수로 변환한다.
   *
   * @param period "7d", "30d", "90d" 형식
   */
  private int parsePeriodToDays(String period) {
    return switch (period) {
      case "7d" -> 7;
      case "30d" -> 30;
      case "90d" -> 90;
      default -> throw new CustomException(ReportErrorCode.RPT_002);
    };
  }

  /**
   * JPQL 조회 결과를 날짜별 학습 시간(분) Map으로 변환한다.
   *
   * @param rawData [LocalDate, Long] 형태의 배열 리스트
   */
  private Map<LocalDate, Integer> toDailyMap(List<Object[]> rawData) {
    Map<LocalDate, Integer> map = new LinkedHashMap<>();
    for (Object[] row : rawData) {
      LocalDate date = (LocalDate) row[0];
      long minutes = ((Number) row[1]).longValue();
      map.put(date, (int) minutes);
    }
    return map;
  }

  /**
   * JPQL 퀴즈 조회 결과를 날짜별 [퀴즈 수, 정답 수] Map으로 변환한다.
   *
   * @param rawData [LocalDate, Long totalCount, Long correctCount] 형태의 배열 리스트
   */
  private Map<LocalDate, int[]> toQuizMap(List<Object[]> rawData) {
    Map<LocalDate, int[]> map = new LinkedHashMap<>();
    for (Object[] row : rawData) {
      LocalDate date = (LocalDate) row[0];
      int totalCount = ((Number) row[1]).intValue();
      int correctCount = ((Number) row[2]).intValue();
      map.put(date, new int[] {totalCount, correctCount});
    }
    return map;
  }

  /**
   * 사용자의 활성 Course 목록을 조회한다 (DELETED 제외).
   *
   * <p>CourseRepository.findByUserId는 페이징 + 필터 전용이라 unpaged로 전체 Course를 가져와
   * DELETED를 거른다.
   */
  private List<Course> loadUserCourses(String userId) {
    return courseRepository
        .findByUserId(userId, null, null, org.springframework.data.domain.Pageable.unpaged())
        .getContent();
  }

  /**
   * 기간 내 완료한 챕터 수를 센다.
   *
   * <p>N+1이 나지 않게 전달받은 Course 목록의 챕터를 벌크 쿼리 한 번으로 조회하고, completedAt이
   * 범위에 드는 것을 센다.
   *
   * @param courses 호출부에서 미리 조회해 넘긴 사용자의 Course 목록
   */
  private int countCompletedChaptersInRange(
      List<Course> courses, LocalDateTime start, LocalDateTime end) {
    List<String> courseIds = courses.stream().map(Course::getId).toList();
    if (courseIds.isEmpty()) {
      return 0;
    }

    List<Chapter> allChapters = chapterRepository.findAllByCourseIdIn(courseIds);
    int count = 0;
    for (Chapter chapter : allChapters) {
      if (chapter.getCompletedAt() != null
          && !chapter.getCompletedAt().isBefore(start)
          && !chapter.getCompletedAt().isAfter(end)) {
        count++;
      }
    }
    return count;
  }

  /**
   * 연속 학습일을 계산한다.
   *
   * <p>오늘부터 거꾸로 학습 기록이 있는 날이 이어지는 일수를 센다. 오늘 기록이 없으면 어제부터 확인한다.
   */
  private int calculateStreakDays(String userId) {
    List<LocalDate> studyDates = studyTimeLogRepository.findDistinctStudyDates(userId);
    if (studyDates.isEmpty()) {
      return 0;
    }

    /* List.contains()는 O(n)이라 HashSet으로 바꿔 O(1)로 찾는다. */
    Set<LocalDate> studyDateSet = new HashSet<>(studyDates);

    LocalDate today = LocalDate.now();
    int streak = 0;

    /* 오늘 아직 학습 전이어도 어제까지의 연속이 끊기지 않게 오늘 또는 어제부터 센다. */
    LocalDate checkDate = today;
    if (!studyDateSet.contains(today)) {
      checkDate = today.minusDays(1);
      if (!studyDateSet.contains(checkDate)) {
        return 0;
      }
    }

    for (LocalDate d = checkDate; studyDateSet.contains(d); d = d.minusDays(1)) {
      streak++;
    }

    return streak;
  }

  /** userId로 사용자를 조회한다. 없으면 USER_001을 던진다. */
  private User findUserById(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(AuthErrorCode.USER_001));
  }

  /**
   * 완료된 제출물 목록에서 과제 평균 점수(0~100)를 계산한다.
   *
   * @param submissions done 상태 제출물 목록
   * @return 평균 점수 (0~100), 제출물이 없으면 0
   */
  private int calculateAssignmentAvgScore(List<Submission> submissions) {
    if (submissions.isEmpty()) return 0;

    double sumPercent = 0;
    int validCount = 0;
    for (Submission s : submissions) {
      Integer maxScore = s.getAssignment().getMaxScore();
      if (s.getOfficialScore() != null && maxScore != null && maxScore > 0) {
        sumPercent += (double) s.getOfficialScore() / maxScore * 100;
        validCount++;
      }
    }
    return validCount > 0 ? (int) Math.round(sumPercent / validCount) : 0;
  }
}
