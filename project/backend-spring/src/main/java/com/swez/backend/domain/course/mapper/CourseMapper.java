/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.mapper;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.course.dto.request.CreateTutoringRequest;
import com.swez.backend.domain.course.dto.response.BookmarkInChapterDto;
import com.swez.backend.domain.course.dto.response.ChapterDetailResponse;
import com.swez.backend.domain.course.dto.response.ChapterSummaryResponse;
import com.swez.backend.domain.course.dto.response.CourseDetailResponse;
import com.swez.backend.domain.course.dto.response.CourseSummaryResponse;
import com.swez.backend.domain.course.dto.response.CurriculumResponse;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.ChapterStatus;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.note.entity.Bookmark;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.global.util.IdGenerator;

/** 과외 도메인 엔티티 ↔ DTO 변환 */
@Component
public class CourseMapper {

  /**
   * Course 엔티티를 CourseSummaryResponse 로 변환한다.
   *
   * <p>학습한 강의 수(completedLessons)는 N+1 을 피하려고 서비스에서 벌크 집계하므로, 매퍼는 계산된
   * 값을 파라미터로만 받는다. completedSlidesByChapterId 를 넘기는 방식과 같다.
   *
   * @param completedLessons 서비스에서 LessonProgress 로 집계한 학습 강의(챕터) 수
   */
  public CourseSummaryResponse toSummaryResponse(Course course, int completedLessons) {
    String tutorName = course.getTutor() != null ? course.getTutor().getName() : null;
    return CourseSummaryResponse.builder()
        .id(course.getId())
        .title(course.getCourseName())
        .subject(course.getSubject())
        .tutorName(tutorName)
        .tutorAvatarUrl(null) // TODO: 아바타 이미지 연동 시 교체
        .status(course.getStatus())
        .progress(course.getProgressPercent().doubleValue() / 100.0)
        .totalLessons(course.getTotalChapters())
        .completedLessons(completedLessons)
        .lastStudiedAt(null) // TODO: 학습 이력 연동 시 교체
        .createdAt(course.getCreatedAt())
        .build();
  }

  /**
   * Course 엔티티를 CourseDetailResponse 로 변환한다.
   *
   * <p>학습한 강의 수(completedLessons)는 서비스에서 집계하므로 매퍼는 계산된 값을 파라미터로만 받는다.
   *
   * @param completedLessons 서비스에서 LessonProgress 로 집계한 학습 강의(챕터) 수
   */
  public CourseDetailResponse toDetailResponse(Course course, int completedLessons) {
    String tutorId = course.getTutor() != null ? course.getTutor().getTutorId() : null;
    String tutorName = course.getTutor() != null ? course.getTutor().getName() : null;
    return CourseDetailResponse.builder()
        .id(course.getId())
        .title(course.getCourseName())
        .subject(course.getSubject())
        .tutorId(tutorId)
        .tutorName(tutorName)
        .tutorAvatarUrl(null) // TODO: 아바타 이미지 연동 시 교체
        .status(course.getStatus())
        .sourceType(course.getSourceType())
        .mode(course.getMode())
        .progress(course.getProgressPercent().doubleValue() / 100.0)
        .totalLessons(course.getTotalChapters())
        .completedLessons(completedLessons)
        .lastStudiedAt(null) // TODO: 학습 이력 연동 시 교체
        .createdAt(course.getCreatedAt())
        .build();
  }

  /**
   * CreateTutoringRequest 와 User·Tutor 엔티티로 Course 엔티티를 생성한다.
   *
   * <p>tutor 가 null 이면 표시·강의 생성 단계에서 기본 튜터로 폴백한다. 선택한 튜터를 여기서 함께 저장해야
   * 과외 상세(tutorName)와 강의 생성 개인화(tutorName/tutorTagline)에 커스텀 튜터가 반영된다.
   *
   * @param tutor 과외에 배정할 튜터 (null 허용)
   * @return 신규 Course 엔티티 (저장 전)
   */
  public Course toEntity(CreateTutoringRequest request, User user, Tutor tutor) {
    // course_name=varchar(200), subject=varchar(100) 컬럼 한도에 맞춰 자른다.
    // 카테고리를 고르지 않으면 프론트가 subject 에 주제 전문을 넣어, 100자를 넘으면 insert 가
    // "value too long" 500 으로 실패한다. 전체 주제는 topic_text(TEXT)에 남는다.
    return Course.builder()
        .id(IdGenerator.course())
        .user(user)
        .tutor(tutor)
        .courseName(truncate(request.title(), 200))
        .subject(truncate(request.subject(), 100))
        .sourceType(request.sourceType())
        .sourcePdfUrl(request.sourceRef())
        .topicText(request.topic())
        .totalChapters(request.lessonCount())
        .status(CourseStatus.CURRICULUM_GENERATING)
        .build();
  }

  /** varchar 컬럼 한도를 넘지 않게 문자열을 최대 길이로 자른다. null 은 그대로 둔다. */
  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }

  /** Course 엔티티 + 챕터 목록을 CurriculumResponse 로 변환한다. */
  public CurriculumResponse toCurriculumResponse(Course course, List<Chapter> chapters) {
    return toCurriculumResponse(course, chapters, Map.of());
  }

  /**
   * Course 엔티티 + 챕터 목록 + 사용자별 슬라이드 진행률을 CurriculumResponse 로 변환한다.
   *
   * @param completedSlidesByChapterId chapterId → 완료한 슬라이드 수
   */
  public CurriculumResponse toCurriculumResponse(
      Course course, List<Chapter> chapters, Map<String, Integer> completedSlidesByChapterId) {
    return toCurriculumResponse(course, chapters, completedSlidesByChapterId, Map.of());
  }

  /**
   * Course 엔티티 + 챕터 목록 + 사용자별 진행률/노출 상태를 CurriculumResponse 로 변환한다.
   *
   * @param completedSlidesByChapterId chapterId → 완료한 슬라이드 수
   * @param visibleStatusByChapterId chapterId → 사용자에게 노출할 챕터 상태
   */
  public CurriculumResponse toCurriculumResponse(
      Course course,
      List<Chapter> chapters,
      Map<String, Integer> completedSlidesByChapterId,
      Map<String, ChapterStatus> visibleStatusByChapterId) {
    return new CurriculumResponse(
        course.getId(),
        course.getStatus().name(),
        chapters.stream()
            .map(
                chapter ->
                    toChapterSummaryResponse(
                        chapter,
                        completedSlidesByChapterId.getOrDefault(chapter.getId(), 0),
                        visibleStatusByChapterId.getOrDefault(
                            chapter.getId(), chapter.getStatus())))
            .toList());
  }

  /** Chapter 엔티티를 ChapterSummaryResponse 로 변환한다. */
  public ChapterSummaryResponse toChapterSummaryResponse(Chapter chapter) {
    return toChapterSummaryResponse(chapter, 0);
  }

  /** Chapter 엔티티와 사용자별 완료 슬라이드 수를 ChapterSummaryResponse 로 변환한다. */
  public ChapterSummaryResponse toChapterSummaryResponse(Chapter chapter, int completedSlides) {
    return toChapterSummaryResponse(chapter, completedSlides, chapter.getStatus());
  }

  /** Chapter 엔티티와 사용자별 완료 슬라이드 수, 노출 상태를 ChapterSummaryResponse 로 변환한다. */
  public ChapterSummaryResponse toChapterSummaryResponse(
      Chapter chapter, int completedSlides, ChapterStatus visibleStatus) {
    int safeTotalSlides = chapter.getTotalSlides() == null ? 0 : chapter.getTotalSlides();
    int safeCompletedSlides = Math.max(0, Math.min(completedSlides, safeTotalSlides));

    return ChapterSummaryResponse.builder()
        .id(chapter.getId())
        .order(chapter.getOrderIndex())
        .title(chapter.getChapterName())
        .description(chapter.getDescription())
        .estimatedMinutes(chapter.getEstimatedMinutes())
        .status(visibleStatus)
        .slideCount(safeTotalSlides)
        .completedSlides(safeCompletedSlides)
        .completedAt(chapter.getCompletedAt())
        .build();
  }

  /** Chapter 엔티티 + 북마크 목록을 ChapterDetailResponse 로 변환한다. */
  public ChapterDetailResponse toChapterDetailResponse(Chapter chapter, List<Bookmark> bookmarks) {
    List<BookmarkInChapterDto> bookmarkDtos =
        bookmarks.stream()
            .map(
                b ->
                    BookmarkInChapterDto.builder()
                        .slideIndex(b.getSlideIndex())
                        .memo(b.getMemo())
                        .createdAt(b.getCreatedAt())
                        .build())
            .toList();

    return ChapterDetailResponse.builder()
        .id(chapter.getId())
        .order(chapter.getOrderIndex())
        .title(chapter.getChapterName())
        .description(chapter.getDescription())
        .estimatedMinutes(chapter.getEstimatedMinutes())
        .status(chapter.getStatus())
        .slideCount(chapter.getTotalSlides())
        .keyTopics(chapter.parseKeyTopics())
        .bookmarks(bookmarkDtos)
        .completedAt(chapter.getCompletedAt())
        .xpEarned(chapter.getXpEarned())
        .build();
  }
}
