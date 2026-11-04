/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.exception.CourseErrorCode;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.lesson.repository.SlideRepository;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.global.exception.CustomException;

import lombok.extern.slf4j.Slf4j;

/** 강의를 점진적으로 생성 요청한다. Spring은 생성 범위와 학생 수준만 정하고 실제 생성은 FastAPI가 한다. */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ProgressiveLessonGenerationService {

  private static final int INITIAL_LESSON_COUNT = 2;
  private static final int NEXT_LESSON_OFFSET = 2;
  private static final int DEFAULT_TUTOR_SLIDER = 50;
  private static final String DEFAULT_AUDIENCE_LEVEL = "일반 학습자";

  private final CourseRepository courseRepository;
  private final ChapterRepository chapterRepository;
  private final SlideRepository slideRepository;
  private final RestTemplate restTemplate;
  private final Set<String> requestedLessonIds = ConcurrentHashMap.newKeySet();

  /** FastAPI 베이스 URL (application.yml의 app.fastapi.base-url) */
  @Value("${app.fastapi.base-url}")
  private String fastapiBaseUrl;

  public ProgressiveLessonGenerationService(
      CourseRepository courseRepository,
      ChapterRepository chapterRepository,
      SlideRepository slideRepository,
      @Qualifier("fastapiRestTemplate") RestTemplate restTemplate) {
    this.courseRepository = courseRepository;
    this.chapterRepository = chapterRepository;
    this.slideRepository = slideRepository;
    this.restTemplate = restTemplate;
  }

  /** 커리큘럼 확정 직후 order_index 기준 첫 2개 강의만 생성 요청한다. */
  public void requestInitialLessons(String userId, String courseId) {
    Course course = findCourse(courseId);
    validateCourseOwner(course, userId);

    List<Chapter> chapters = chapterRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
    chapters.stream()
        .limit(INITIAL_LESSON_COUNT)
        .forEach(chapter -> requestGenerateOne(course, chapter, true));

    log.info("[ProgressiveLessonGenerationService] 초기 강의 생성 요청 완료 | courseId: {}", courseId);
  }

  /** 강의 K 완료 뒤 K+2 강의를 생성 요청한다. 호출이 실패해도 진행률 저장이 깨지지 않게 로그만 남긴다. */
  public void triggerNextLessonAfterCompletion(
      String userId, String courseId, String completedChapterId) {
    try {
      requestNextLesson(userId, courseId, completedChapterId);
    } catch (RuntimeException e) {
      log.error(
          "[ProgressiveLessonGenerationService] 다음 강의 생성 트리거 실패 | userId: {}, courseId: {}, completedChapterId: {}",
          userId,
          courseId,
          completedChapterId,
          e);
    }
  }

  private void requestNextLesson(String userId, String courseId, String completedChapterId) {
    Course course = findCourse(courseId);
    validateCourseOwner(course, userId);

    Chapter completedChapter =
        chapterRepository
            .findById(completedChapterId)
            .orElseThrow(() -> new CustomException(CourseErrorCode.CRS_002));
    if (!courseId.equals(completedChapter.getCourse().getId())) {
      log.warn(
          "[ProgressiveLessonGenerationService] 완료 챕터가 과외에 속하지 않아 트리거를 건너뜀 | courseId: {}, completedChapterId: {}",
          courseId,
          completedChapterId);
      return;
    }

    int nextOrderIndex = completedChapter.getOrderIndex() + NEXT_LESSON_OFFSET;
    chapterRepository
        .findByCourseIdAndOrderIndex(courseId, nextOrderIndex)
        .ifPresentOrElse(
            nextChapter -> requestGenerateOne(course, nextChapter, false),
            () ->
                log.debug(
                    "[ProgressiveLessonGenerationService] K+2 챕터가 없어 트리거를 건너뜀 | courseId: {}, nextOrderIndex: {}",
                    courseId,
                    nextOrderIndex));
  }

  private void requestGenerateOne(Course course, Chapter chapter, boolean failOnError) {
    if (slideRepository.existsByChapterId(chapter.getId())) {
      log.info(
          "[ProgressiveLessonGenerationService] 이미 생성된 강의라 요청을 건너뜀 | courseId: {}, lessonId: {}",
          course.getId(),
          chapter.getId());
      return;
    }

    if (!requestedLessonIds.add(chapter.getId())) {
      log.info(
          "[ProgressiveLessonGenerationService] 이미 트리거된 강의라 요청을 건너뜀 | courseId: {}, lessonId: {}",
          course.getId(),
          chapter.getId());
      return;
    }

    String url = fastapiBaseUrl + "/api/lessons/generate-one";
    try {
      restTemplate.postForEntity(url, buildGenerateOneBody(course, chapter), Void.class);
      log.info(
          "[ProgressiveLessonGenerationService] 강의 1개 생성 요청 성공 | courseId: {}, lessonId: {}, orderIndex: {}",
          course.getId(),
          chapter.getId(),
          chapter.getOrderIndex());
    } catch (RestClientException e) {
      requestedLessonIds.remove(chapter.getId());
      log.error(
          "[ProgressiveLessonGenerationService] 강의 1개 생성 요청 실패 | courseId: {}, lessonId: {}, error: {}",
          course.getId(),
          chapter.getId(),
          e.getMessage());
      if (failOnError) {
        throw new CustomException(CourseErrorCode.CRS_005);
      }
    } catch (RuntimeException e) {
      requestedLessonIds.remove(chapter.getId());
      log.error(
          "[ProgressiveLessonGenerationService] 강의 1개 생성 요청 처리 실패 | courseId: {}, lessonId: {}",
          course.getId(),
          chapter.getId(),
          e);
      if (failOnError) {
        throw new CustomException(CourseErrorCode.CRS_005);
      }
    }
  }

  private Map<String, Object> buildGenerateOneBody(Course course, Chapter chapter) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("courseId", course.getId());
    body.put("lessonId", chapter.getId());
    body.put("audienceLevel", resolveAudienceLevel(course.getUser()));
    body.put("learningGoal", resolveLearningGoal(course.getUser()));
    body.put("tone", resolveTone(course.getTutor()));
    body.put("pace", resolvePace(course.getTutor()));
    body.put("tutorDepth", resolveTutorDepth(course.getTutor()));
    body.put("socratic", resolveSocratic(course.getTutor()));
    body.put("useFormalSpeech", resolveUseFormalSpeech(course.getTutor()));
    body.put("useEmoji", resolveUseEmoji(course.getTutor()));
    body.put("tutorName", resolveTutorName(course.getTutor()));
    body.put("tutorTagline", resolveTutorTagline(course.getTutor()));
    body.put("isDefaultTutor", resolveIsDefault(course.getTutor()));
    body.put("voiceSampleUrl", resolveVoiceSampleUrl(course.getTutor()));
    return body;
  }

  private Course findCourse(String courseId) {
    return courseRepository
        .findById(courseId)
        .orElseThrow(() -> new CustomException(CourseErrorCode.CRS_001));
  }

  private void validateCourseOwner(Course course, String userId) {
    if (!userId.equals(course.getUser().getId())) {
      throw new CustomException(CourseErrorCode.CRS_001);
    }
  }

  private String resolveAudienceLevel(User user) {
    if (user == null || isBlank(user.getGradeLevel())) {
      return DEFAULT_AUDIENCE_LEVEL;
    }
    return user.getGradeLevel().trim();
  }

  private String resolveLearningGoal(User user) {
    if (user == null || isBlank(user.getLearningGoal())) {
      return null;
    }
    return user.getLearningGoal().trim();
  }

  private int resolveTone(Tutor tutor) {
    return tutor == null ? DEFAULT_TUTOR_SLIDER : tutor.getToneSlider();
  }

  private int resolvePace(Tutor tutor) {
    return tutor == null ? DEFAULT_TUTOR_SLIDER : tutor.getSpeedSlider();
  }

  private int resolveTutorDepth(Tutor tutor) {
    return tutor == null ? DEFAULT_TUTOR_SLIDER : tutor.getDepthSlider();
  }

  private int resolveSocratic(Tutor tutor) {
    return tutor == null ? DEFAULT_TUTOR_SLIDER : tutor.getQuestionStyleSlider();
  }

  private boolean resolveUseFormalSpeech(Tutor tutor) {
    return tutor == null || tutor.isUseFormalSpeech();
  }

  private boolean resolveUseEmoji(Tutor tutor) {
    return tutor != null && tutor.isUseEmoji();
  }

  private String resolveTutorName(Tutor tutor) {
    return tutor == null || isBlank(tutor.getName()) ? null : tutor.getName().trim();
  }

  private String resolveTutorTagline(Tutor tutor) {
    return tutor == null || isBlank(tutor.getTagline()) ? null : tutor.getTagline().trim();
  }

  private boolean resolveIsDefault(Tutor tutor) {
    return tutor == null || tutor.isDefault();
  }

  private String resolveVoiceSampleUrl(Tutor tutor) {
    return tutor == null || isBlank(tutor.getVoiceSampleUrl())
        ? null
        : tutor.getVoiceSampleUrl().trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
