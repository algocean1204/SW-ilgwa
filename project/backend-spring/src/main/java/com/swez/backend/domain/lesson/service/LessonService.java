/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.calendar.entity.CalendarEvent;
import com.swez.backend.domain.calendar.enums.CalendarEventStatus;
import com.swez.backend.domain.calendar.enums.CalendarEventType;
import com.swez.backend.domain.calendar.repository.CalendarEventRepository;
import com.swez.backend.domain.report.entity.StudyTimeLog;
import com.swez.backend.domain.report.repository.StudyTimeLogRepository;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.ChapterStatus;
import com.swez.backend.domain.course.exception.CourseErrorCode;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.lesson.dto.request.ProgressRequest;
import com.swez.backend.domain.lesson.dto.request.QuizAnswerRequest;
import com.swez.backend.domain.lesson.dto.response.QuizListResponse;
import com.swez.backend.domain.lesson.dto.response.QuizResponse;
import com.swez.backend.domain.lesson.dto.response.QuizResultResponse;
import com.swez.backend.domain.lesson.dto.response.SlideListResponse;
import com.swez.backend.domain.lesson.dto.response.SlideResponse;
import com.swez.backend.domain.lesson.entity.LessonProgress;
import com.swez.backend.domain.lesson.entity.Quiz;
import com.swez.backend.domain.lesson.entity.QuizAnswer;
import com.swez.backend.domain.lesson.exception.LessonErrorCode;
import com.swez.backend.domain.lesson.mapper.LessonMapper;
import com.swez.backend.domain.lesson.repository.LessonProgressRepository;
import com.swez.backend.domain.lesson.repository.QuizAnswerRepository;
import com.swez.backend.domain.lesson.repository.QuizRepository;
import com.swez.backend.domain.lesson.repository.SlideRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 학습 실행 중 슬라이드 조회, 진행률 기록, 퀴즈 채점을 처리한다. */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LessonService {

  private final CourseRepository courseRepository;
  private final ChapterRepository chapterRepository;
  private final SlideRepository slideRepository;
  private final LessonProgressRepository lessonProgressRepository;
  private final QuizRepository quizRepository;
  private final QuizAnswerRepository quizAnswerRepository;
  private final UserRepository userRepository;
  private final LessonMapper lessonMapper;
  private final ProgressiveLessonGenerationService progressiveLessonGenerationService;
  private final CalendarEventRepository calendarEventRepository;
  private final StudyTimeLogRepository studyTimeLogRepository;

  /** 챕터 내 슬라이드 목록과 사용자의 진행률(completedSlides)을 함께 반환한다. */
  public SlideListResponse getSlides(String userId, String courseId, String chapterId) {
    log.debug(
        "[LessonService] getSlides() - START | userId: {}, courseId: {}, chapterId: {}",
        userId,
        courseId,
        chapterId);

    Chapter chapter = validateCourseAndChapter(courseId, chapterId, userId);

    List<SlideResponse> slides =
        slideRepository.findByChapterIdOrderBySlideIdxAsc(chapterId).stream()
            .map(lessonMapper::toSlideResponse)
            .toList();

    int totalSlides = slides.size();
    int completedSlides =
        lessonProgressRepository
            .findByChapterIdAndUserId(chapterId, userId)
            .map(progress -> resolveCompletedSlides(progress, totalSlides))
            .orElse(0);

    SlideListResponse result =
        SlideListResponse.builder()
            .slides(slides)
            .totalSlides(totalSlides)
            .completedSlides(completedSlides)
            .build();

    log.debug("[LessonService] getSlides() - END | totalSlides: {}", totalSlides);
    return result;
  }

  /** 강의 진행률을 upsert 한다. 진행률이 없으면 만들고, 있으면 갱신한다. */
  @Transactional
  public void recordProgress(
      String userId, String courseId, String chapterId, ProgressRequest request) {
    log.debug(
        "[LessonService] recordProgress() - START | userId: {}, courseId: {}, chapterId: {}",
        userId,
        courseId,
        chapterId);

    Chapter chapter = validateCourseAndChapter(courseId, chapterId, userId);

    boolean requestedCompleted = Boolean.TRUE.equals(request.isCompleted());
    Optional<LessonProgress> existingProgress =
        lessonProgressRepository.findByChapterIdAndUserId(chapterId, userId);
    boolean wasCompleted = existingProgress.map(LessonProgress::getIsCompleted).orElse(false);

    int savedSlideProgress =
        normalizeSlideProgress(
            request.currentSlideIdx(), chapter.getTotalSlides(), requestedCompleted);

    LessonProgress progress =
        existingProgress
            .map(
                existing -> {
                  existing.updateProgress(
                      savedSlideProgress, request.playbackSec(), requestedCompleted);
                  return existing;
                })
            .orElseGet(
                () -> {
                  LessonProgress newProgress =
                      LessonProgress.builder()
                          .id(IdGenerator.lessonProgress())
                          .chapter(chapter)
                          .user(userRepository.getReferenceById(userId))
                          .currentSlideIdx(savedSlideProgress)
                          .playbackSec(request.playbackSec())
                          .isCompleted(requestedCompleted)
                          .build();
                  if (requestedCompleted) {
                    newProgress.updateProgress(
                        savedSlideProgress, request.playbackSec(), true);
                  }
                  return newProgress;
                });

    lessonProgressRepository.save(progress);

    // playbackSec으로 학습 시간을 계산해 StudyTimeLog를 INSERT 하고 CalendarEvent를 upsert 한다
    int addedMinutes = Math.max(1, request.playbackSec().intValue() / 60);
    recordStudyTimeLog(userId, chapter, addedMinutes, request.playbackSec().intValue());
    upsertDailyStudyEvent(userId, chapter.getCourse(), addedMinutes);

    if (!wasCompleted && requestedCompleted) {
      triggerNextLessonAfterCommit(userId, courseId, chapterId);
      // 모든 챕터를 완료했으면 Course를 COMPLETED로 전이한다
      checkAndMarkCourseCompleted(userId, courseId, chapter.getCourse());
    }

    log.debug(
        "[LessonService] recordProgress() - END | chapterId: {}, isCompleted: {}",
        chapterId,
        request.isCompleted());
  }

  /** 챕터 내 퀴즈 목록을 정답(correctOption) 없이 반환한다. */
  public QuizListResponse getQuizzes(String userId, String courseId, String chapterId) {
    log.debug(
        "[LessonService] getQuizzes() - START | userId: {}, courseId: {}, chapterId: {}",
        userId,
        courseId,
        chapterId);

    validateCourseAndChapter(courseId, chapterId, userId);

    List<QuizResponse> quizzes =
        quizRepository.findByChapterIdOrderByQuizIdxAsc(chapterId).stream()
            .map(lessonMapper::toQuizResponse)
            .toList();

    QuizListResponse result =
        QuizListResponse.builder().quizzes(quizzes).totalQuizzes(quizzes.size()).build();

    log.debug("[LessonService] getQuizzes() - END | totalQuizzes: {}", quizzes.size());
    return result;
  }

  /**
   * 퀴즈 답변을 제출하고 즉시 채점한다. 각 답변을 quiz.correctOption과 비교해 정오를 판별하고
   * quiz_answer에 저장한다.
   */
  @Transactional
  public QuizResultResponse submitQuizAnswers(
      String userId, String courseId, String chapterId, QuizAnswerRequest request) {
    log.debug(
        "[LessonService] submitQuizAnswers() - START | userId: {}, courseId: {}, chapterId: {}",
        userId,
        courseId,
        chapterId);

    validateCourseAndChapter(courseId, chapterId, userId);

    List<String> quizIds =
        request.answers().stream().map(QuizAnswerRequest.SingleAnswer::quizId).toList();

    Map<String, Quiz> quizMap =
        quizRepository.findAllByIdInAndChapterId(quizIds, chapterId).stream()
            .collect(Collectors.toMap(Quiz::getId, Function.identity()));

    /* 같은 퀴즈에 두 번 답하지 않도록 기존 답변을 미리 읽어 둔다. */
    List<QuizAnswer> existingAnswers =
        quizAnswerRepository.findByQuizIdInAndUserId(quizIds, userId);
    Map<String, QuizAnswer> existingMap =
        existingAnswers.stream()
            .collect(Collectors.toMap(qa -> qa.getQuiz().getId(), Function.identity()));

    List<QuizResultResponse.SingleResult> results = new ArrayList<>();
    List<QuizAnswer> answersToSave = new ArrayList<>();
    int correctCount = 0;

    for (QuizAnswerRequest.SingleAnswer answer : request.answers()) {
      Quiz quiz = quizMap.get(answer.quizId());
      if (quiz == null) {
        throw new CustomException(LessonErrorCode.LSN_002);
      }

      if (existingMap.containsKey(answer.quizId())) {
        throw new CustomException(LessonErrorCode.LSN_003);
      }

      boolean isCorrect = quiz.getCorrectOption().equals(answer.selectedOption());
      if (isCorrect) correctCount++;

      answersToSave.add(
          QuizAnswer.builder()
              .id(IdGenerator.quizAnswer())
              .quiz(quiz)
              .user(userRepository.getReferenceById(userId))
              .selectedOption(answer.selectedOption())
              .isCorrect(isCorrect)
              .build());

      results.add(
          QuizResultResponse.SingleResult.builder()
              .quizId(answer.quizId())
              .selectedOption(answer.selectedOption())
              .correctOption(quiz.getCorrectOption())
              .isCorrect(isCorrect)
              .explanation(quiz.getExplanation())
              .build());
    }

    quizAnswerRepository.saveAll(answersToSave);

    int totalQuestions = request.answers().size();
    int score =
        totalQuestions > 0 ? (int) Math.round((double) correctCount / totalQuestions * 100) : 0;

    QuizResultResponse result =
        QuizResultResponse.builder()
            .totalQuestions(totalQuestions)
            .correctCount(correctCount)
            .score(score)
            .results(results)
            .build();

    log.debug(
        "[LessonService] submitQuizAnswers() - END | score: {}/{} ({}%)",
        correctCount, totalQuestions, score);
    return result;
  }

  /**
   * 챕터 내 사용자의 기존 퀴즈 답변을 조회한다.
   *
   * @return 퀴즈 결과 응답 (답변 없으면 null)
   */
  public QuizResultResponse getQuizAnswers(String userId, String courseId, String chapterId) {
    log.debug(
        "[LessonService] getQuizAnswers() - START | userId: {}, courseId: {}, chapterId: {}",
        userId,
        courseId,
        chapterId);

    validateCourseAndChapter(courseId, chapterId, userId);

    List<QuizAnswer> answers = quizAnswerRepository.findByChapterIdAndUserId(chapterId, userId);
    if (answers.isEmpty()) {
      return null;
    }

    int correctCount = 0;
    List<QuizResultResponse.SingleResult> results = new ArrayList<>();

    for (QuizAnswer qa : answers) {
      Quiz quiz = qa.getQuiz();
      if (Boolean.TRUE.equals(qa.getIsCorrect())) correctCount++;

      results.add(
          QuizResultResponse.SingleResult.builder()
              .quizId(quiz.getId())
              .selectedOption(qa.getSelectedOption())
              .correctOption(quiz.getCorrectOption())
              .isCorrect(qa.getIsCorrect())
              .explanation(quiz.getExplanation())
              .build());
    }

    int totalQuestions = answers.size();
    int score =
        totalQuestions > 0 ? (int) Math.round((double) correctCount / totalQuestions * 100) : 0;

    log.debug(
        "[LessonService] getQuizAnswers() - END | score: {}/{} ({}%)",
        correctCount, totalQuestions, score);

    return QuizResultResponse.builder()
        .totalQuestions(totalQuestions)
        .correctCount(correctCount)
        .score(score)
        .results(results)
        .build();
  }

  // Private Helpers

  /**
   * 과외·챕터 소유권과 접근 가능 여부를 확인한다. Course 조회 → user 일치 확인 → Chapter 조회 → course 일치
   * 확인 → 접근 가능 여부 확인 순서다.
   *
   * @return 확인을 통과한 Chapter 엔티티
   */
  private Chapter validateCourseAndChapter(String courseId, String chapterId, String userId) {
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
            .orElseThrow(() -> new CustomException(LessonErrorCode.LSN_004));
    if (!chapter.getCourse().getId().equals(courseId)) {
      throw new CustomException(LessonErrorCode.LSN_004);
    }
    if (!canAccessChapter(userId, courseId, chapter)) {
      throw new CustomException(LessonErrorCode.LSN_005);
    }

    return chapter;
  }

  /**
   * 사용자별 완료 상태를 기준으로 지금 접근할 수 있는 강의인지 확인한다. FastAPI가 미리 생성해 둔 강의도 이
   * 확인을 거친다.
   */
  private boolean canAccessChapter(String userId, String courseId, Chapter targetChapter) {
    List<Chapter> chapters = chapterRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
    if (chapters.isEmpty()) {
      return isGenerated(targetChapter);
    }

    List<String> chapterIds = chapters.stream().map(Chapter::getId).toList();
    Map<String, Boolean> completedByChapterId =
        lessonProgressRepository.findByUserIdAndChapterIdIn(userId, chapterIds).stream()
            .collect(
                Collectors.toMap(
                    progress -> progress.getChapter().getId(),
                    progress -> Boolean.TRUE.equals(progress.getIsCompleted()),
                    Boolean::logicalOr));

    boolean previousChaptersCompleted = true;
    for (Chapter chapter : chapters) {
      boolean currentCompleted =
          Boolean.TRUE.equals(completedByChapterId.get(chapter.getId()));
      if (chapter.getId().equals(targetChapter.getId())) {
        return currentCompleted || (previousChaptersCompleted && isGenerated(chapter));
      }
      if (!currentCompleted) {
        previousChaptersCompleted = false;
      }
    }

    return false;
  }

  private boolean isGenerated(Chapter chapter) {
    return chapter.getStatus() != ChapterStatus.LOCKED;
  }

  private int normalizeSlideProgress(
      Integer requestedSlideProgress, Integer totalSlides, boolean isCompleted) {
    int safeTotalSlides = Math.max(0, totalSlides == null ? 0 : totalSlides);
    if (isCompleted) {
      return safeTotalSlides;
    }
    int rawProgress = requestedSlideProgress == null ? 0 : requestedSlideProgress;
    return Math.max(0, Math.min(rawProgress, safeTotalSlides));
  }

  private int resolveCompletedSlides(LessonProgress progress, int totalSlides) {
    int safeTotalSlides = Math.max(0, totalSlides);
    if (progress == null) {
      return 0;
    }
    if (Boolean.TRUE.equals(progress.getIsCompleted())) {
      return safeTotalSlides;
    }
    return Math.max(
        0,
        Math.min(
            progress.getCurrentSlideIdx() == null ? 0 : progress.getCurrentSlideIdx(),
            safeTotalSlides));
  }

  /**
   * 레슨 완료 뒤 사용자가 이 과외의 모든 챕터를 완료했는지 판정하고, 그렇다면 Course를 COMPLETED로
   * 전이한다.
   *
   * <p>course.totalChapters는 커리큘럼 생성 때 채워지지 않아 0으로 남을 수 있다. 이 값을 분모로 쓰면 완강
   * 판정이 영영 일어나지 않으므로 chapter 테이블의 실제 챕터 수(DB 카운트)를 분모로 쓴다. 실제 챕터 수가 0이면
   * 잘못된 완강 전이를 막기 위해 판정을 건너뛴다.
   *
   * @param course 소유권 확인을 마친 Course 엔티티
   */
  private void checkAndMarkCourseCompleted(String userId, String courseId, Course course) {
    // totalChapters가 0(미설정)이어도 실제 챕터가 있으면 완강 판정이 동작하도록 DB 카운트를 쓴다
    long actualTotalChapters = chapterRepository.countByCourseId(courseId);
    if (actualTotalChapters <= 0) {
      // 실제 챕터가 하나도 없으면 완강을 판정할 수 없다
      log.warn(
          "[LessonService] 완강 판정 건너뜀 — 실제 챕터 수 0 | courseId: {}", courseId);
      return;
    }

    long completedCount =
        lessonProgressRepository.countCompletedByCourseIdAndUserId(courseId, userId);

    log.debug(
        "[LessonService] 완강 판정 | courseId: {}, completedCount: {}, actualTotalChapters: {}",
        courseId,
        completedCount,
        actualTotalChapters);

    if (completedCount >= actualTotalChapters) {
      course.markCompleted();
      log.info(
          "[LessonService] 완강 전이 완료 (ACTIVE → COMPLETED) | courseId: {}, userId: {}",
          courseId,
          userId);
    }
  }

  /**
   * 슬라이드 진행 1회를 StudyTimeLog에 기록한다.
   * durationMinutes는 playbackSec으로 계산하며 최소 1분이다.
   * 리포트의 일별 학습 시간은 이 테이블의 SUM(durationMinutes)로 집계된다.
   */
  private void recordStudyTimeLog(String userId, Chapter chapter, int durationMinutes, int playbackSec) {
    LocalDateTime endedAt = LocalDateTime.now();
    LocalDateTime startedAt = endedAt.minusSeconds(Math.max(playbackSec, 60));

    studyTimeLogRepository.save(
        StudyTimeLog.builder()
            .id(IdGenerator.studyTimeLog())
            .user(userRepository.getReferenceById(userId))
            .course(chapter.getCourse())
            .chapter(chapter)
            .activityType("slide_view")
            .startedAt(startedAt)
            .endedAt(endedAt)
            .durationMinutes(durationMinutes)
            .build());
  }

  /**
   * 당일 학습 CalendarEvent를 upsert한다.
   * 오늘 이미 LESSON 이벤트가 있으면 durationMin을 누적하고, 없으면 새로 만든다.
   * 스트릭 히트맵은 이 이벤트로 집계한다.
   */
  private void upsertDailyStudyEvent(String userId, Course course, int addedMinutes) {
    LocalDate today = LocalDate.now();
    Optional<CalendarEvent> existing =
        calendarEventRepository.findTopByUser_IdAndEventDateAndType(
            userId, today, CalendarEventType.LESSON);

    if (existing.isPresent()) {
      CalendarEvent event = existing.get();
      int newDuration = (event.getDurationMin() != null ? event.getDurationMin() : 0) + addedMinutes;
      event.update(null, null, null, newDuration, CalendarEventStatus.COMPLETED);
    } else {
      calendarEventRepository.save(
          CalendarEvent.builder()
              .id(IdGenerator.calendarEvent())
              .user(userRepository.getReferenceById(userId))
              .title("강의 학습")
              .type(CalendarEventType.LESSON)
              .eventDate(today)
              .durationMin(addedMinutes)
              .status(CalendarEventStatus.COMPLETED)
              .course(course)
              .build());
    }
  }

  /** 진행률 트랜잭션이 커밋된 뒤 FastAPI 트리거를 호출한다. 테스트처럼 트랜잭션 동기화가 없으면 즉시 실행한다. */
  private void triggerNextLessonAfterCommit(String userId, String courseId, String chapterId) {
    Runnable trigger =
        () -> {
          try {
            progressiveLessonGenerationService.triggerNextLessonAfterCompletion(
                userId, courseId, chapterId);
          } catch (RuntimeException e) {
            log.error(
                "[LessonService] 다음 강의 생성 트리거 후처리 실패 | userId: {}, courseId: {}, chapterId: {}",
                userId,
                courseId,
                chapterId,
                e);
          }
        };

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      trigger.run();
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            trigger.run();
          }
        });
  }
}
