/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.exception.AuthErrorCode;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.course.dto.request.CreateTutoringRequest;
import com.swez.backend.domain.course.dto.request.UpdateTutoringRequest;
import com.swez.backend.domain.course.dto.response.ChapterDetailResponse;
import com.swez.backend.domain.course.dto.response.ChapterSummaryResponse;
import com.swez.backend.domain.course.dto.response.CourseDetailResponse;
import com.swez.backend.domain.course.dto.response.CourseSummaryResponse;
import com.swez.backend.domain.course.dto.response.CurriculumResponse;
import com.swez.backend.domain.course.dto.response.LessonGenerationStatusResponse;
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
import com.swez.backend.domain.subscription.entity.SubscriptionPlan;
import com.swez.backend.domain.subscription.repository.SubscriptionPlanRepository;
import com.swez.backend.domain.lesson.repository.LessonProgressRepository;
import com.swez.backend.domain.lesson.service.ProgressiveLessonGenerationService;
import com.swez.backend.domain.note.entity.Bookmark;
import com.swez.backend.domain.note.repository.BookmarkRepository;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.domain.tutor.exception.TutorErrorCode;
import com.swez.backend.domain.tutor.repository.TutorRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.logging.LogMaskUtil;
import com.swez.backend.global.page.OffsetPageResponse;
import com.swez.backend.global.service.S3Service;

import lombok.extern.slf4j.Slf4j;

/** 과외·강의 CRUD와 FastAPI 커리큘럼·강의 생성 요청을 처리한다. */
@Slf4j
@Service
@Transactional(readOnly = true)
public class CourseService {

  private static final int RECENT_TUTORING_LIMIT = 5;
  private static final long MAX_SOURCE_PDF_SIZE = 100L * 1024 * 1024;

  private final CourseRepository courseRepository;
  private final ChapterRepository chapterRepository;
  private final BookmarkRepository bookmarkRepository;
  private final LessonProgressRepository lessonProgressRepository;
  private final UserRepository userRepository;
  private final CourseMapper courseMapper;
  private final RestTemplate restTemplate;
  private final ProgressiveLessonGenerationService progressiveLessonGenerationService;
  private final S3Service s3Service;
  private final SubscriptionPlanRepository subscriptionPlanRepository;
  private final TutorRepository tutorRepository;

  @PersistenceContext private EntityManager entityManager;

  /** FastAPI 베이스 URL (application.yml 의 app.fastapi.base-url) */
  @Value("${app.fastapi.base-url}")
  private String fastapiBaseUrl;

  public CourseService(
      CourseRepository courseRepository,
      ChapterRepository chapterRepository,
      BookmarkRepository bookmarkRepository,
      LessonProgressRepository lessonProgressRepository,
      UserRepository userRepository,
      CourseMapper courseMapper,
      @Qualifier("fastapiRestTemplate") RestTemplate restTemplate,
      ProgressiveLessonGenerationService progressiveLessonGenerationService,
      S3Service s3Service,
      SubscriptionPlanRepository subscriptionPlanRepository,
      TutorRepository tutorRepository) {
    this.courseRepository = courseRepository;
    this.chapterRepository = chapterRepository;
    this.bookmarkRepository = bookmarkRepository;
    this.lessonProgressRepository = lessonProgressRepository;
    this.userRepository = userRepository;
    this.courseMapper = courseMapper;
    this.restTemplate = restTemplate;
    this.progressiveLessonGenerationService = progressiveLessonGenerationService;
    this.s3Service = s3Service;
    this.subscriptionPlanRepository = subscriptionPlanRepository;
    this.tutorRepository = tutorRepository;
  }

  /**
   * 내 과외 목록을 조회한다. status·subject 필터 및 페이지네이션을 지원한다.
   *
   * @param status 상태 필터 (null이면 전체)
   * @param subject 과목 필터 (null이면 전체)
   * @param page 페이지 번호 (0부터 시작)
   */
  public OffsetPageResponse<CourseSummaryResponse> getMyCourses(
      String userId, CourseStatus status, String subject, int page, int size) {
    log.debug(
        "[CourseService] getMyCourses() - START | userId: {}, status: {}, subject: {}",
        userId,
        status,
        subject);

    Pageable pageable = PageRequest.of(page, size);
    Page<Course> coursePage = courseRepository.findByUserId(userId, status, subject, pageable);

    /*
        학습 강의 수는 N+1 을 피하려고 한 번에 집계한다. Chapter.status 는 생성·잠금 상태에 가깝고
        학생별 완료 여부는 lesson_progress.is_completed 에 있으므로 사용자별 진행 기록으로 센다.
    */
    Map<String, Integer> completedByCourseId =
        countCompletedChaptersByCourseIds(
            userId,
            coursePage.getContent().stream().map(Course::getId).toList());

    List<CourseSummaryResponse> content =
        coursePage.getContent().stream()
            .map(
                course ->
                    courseMapper.toSummaryResponse(
                        course, completedByCourseId.getOrDefault(course.getId(), 0)))
            .toList();
    OffsetPageResponse<CourseSummaryResponse> result =
        OffsetPageResponse.of(content, coursePage.getTotalElements(), page, size);

    log.debug(
        "[CourseService] getMyCourses() - END | totalElements: {}", coursePage.getTotalElements());
    return result;
  }

  /** 과외 상세 정보를 조회한다. 소유자만 접근 가능하다. */
  public CourseDetailResponse getCourse(String userId, String courseId) {
    log.debug("[CourseService] getCourse() - START | userId: {}, courseId: {}", userId, courseId);

    /*
        소유자가 아니어도 CRS_001 을 던져 과외 존재 여부를 드러내지 않는다.
    */
    Course course = findCourseByIdAndUser(courseId, userId);

    CourseDetailResponse result =
        courseMapper.toDetailResponse(course, countCompletedChapters(userId, courseId));

    log.debug("[CourseService] getCourse() - END | courseId: {}", courseId);
    return result;
  }

  /** 과외 내 강의(챕터) 목록을 order_index 오름차순으로 조회한다. */
  public List<ChapterSummaryResponse> getChapters(String userId, String courseId) {
    log.debug("[CourseService] getChapters() - START | userId: {}, courseId: {}", userId, courseId);

    findCourseByIdAndUser(courseId, userId);

    List<Chapter> chapters = chapterRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
    Map<String, LessonProgress> progressByChapterId = findProgressByChapterId(userId, chapters);
    Map<String, Integer> completedSlidesByChapterId =
        toCompletedSlidesByChapterId(chapters, progressByChapterId);
    Map<String, ChapterStatus> visibleStatusByChapterId =
        resolveVisibleChapterStatuses(chapters, progressByChapterId);
    List<ChapterSummaryResponse> result =
        chapters.stream()
            .map(
                chapter ->
                    courseMapper.toChapterSummaryResponse(
                        chapter,
                        completedSlidesByChapterId.getOrDefault(chapter.getId(), 0),
                        visibleStatusByChapterId.getOrDefault(chapter.getId(), chapter.getStatus())))
            .toList();

    log.debug("[CourseService] getChapters() - END | count: {}", result.size());
    return result;
  }

  /** 강의(챕터) 상세 정보를 조회한다. 북마크 목록을 포함한다. */
  public ChapterDetailResponse getChapter(String userId, String courseId, String chapterId) {
    log.debug(
        "[CourseService] getChapter() - START | userId: {}, courseId: {}, chapterId: {}",
        userId,
        courseId,
        chapterId);

    findCourseByIdAndUser(courseId, userId);

    /*
        챕터가 요청한 과외에 속하는지 확인한다.
    */
    Chapter chapter =
        chapterRepository
            .findById(chapterId)
            .orElseThrow(() -> new CustomException(CourseErrorCode.CRS_002));
    if (!chapter.getCourse().getId().equals(courseId)) {
      throw new CustomException(CourseErrorCode.CRS_002);
    }

    List<Bookmark> bookmarks = bookmarkRepository.findByUserIdAndChapterId(userId, chapterId);

    ChapterDetailResponse result = courseMapper.toChapterDetailResponse(chapter, bookmarks);

    log.debug("[CourseService] getChapter() - END | chapterId: {}", chapterId);
    return result;
  }

  /**
   * 강의 생성의 노드 단위 진행 상태를 조회한다.
   *
   * <p>FastAPI ChapterStudio가 기록하는 lesson_generation_status(자료 분석 → 슬라이드 생성 → 검증 → 음성 합성 →
   * 마무리)를 프록시해 프론트 대기 화면에 실제 진행률을 보여 준다. 진행률 표시는 부가 기능이라 폴링 루프를 깨지 않는
   * 쪽을 택해, FastAPI 미응답·오류 시에는 예외 대신 status=unknown 을 반환하고 프론트는 기존 챕터 단위 표시를 쓴다.
   *
   * @param chapterId 강의(챕터) ID. FastAPI lesson_id 와 같은 키다.
   * @return 생성 진행 상태 응답 (조회 불가 시 status=unknown)
   */
  public LessonGenerationStatusResponse getLessonGenerationStatus(
      String userId, String courseId, String chapterId) {
    /*
        소유권과 챕터 소속은 getChapter 와 같은 규칙으로 확인한다.
    */
    findCourseByIdAndUser(courseId, userId);
    Chapter chapter =
        chapterRepository
            .findById(chapterId)
            .orElseThrow(() -> new CustomException(CourseErrorCode.CRS_002));
    if (!chapter.getCourse().getId().equals(courseId)) {
      throw new CustomException(CourseErrorCode.CRS_002);
    }

    /*
        FastAPI 진행률 조회가 실패해도 폴링이 깨지지 않게 unknown 으로 폴백한다.
    */
    String url = fastapiBaseUrl + "/api/lessons/" + chapterId + "/generation-status";
    try {
      LessonGenerationStatusResponse body =
          restTemplate.getForObject(url, LessonGenerationStatusResponse.class);
      if (body == null || body.status() == null) {
        return LessonGenerationStatusResponse.unknown(chapterId);
      }
      return body;
    } catch (RestClientException e) {
      log.warn(
          "[CourseService] 강의 생성 진행 상태 조회 실패 — unknown 폴백 | chapterId: {}, error: {}",
          chapterId,
          e.getMessage());
      return LessonGenerationStatusResponse.unknown(chapterId);
    }
  }

  // 대시보드

  /**
   * 최근 학습 과외 목록을 조회한다. DELETED 제외, 최근 접근순 정렬, 상위 5개를 반환한다. study_time_log 기록이 없는 과외는
   * Course.updatedAt 을 대체 시각으로 사용한다.
   */
  public RecentTutoringResponse getRecentTutoring(String userId) {
    log.debug("[CourseService] getRecentTutoring() - START | userId: {}", userId);

    /*
        사용자의 최근 과외를 updatedAt 내림차순으로 상위 N개만 조회한다.
        Integer.MAX_VALUE 로 전부 읽으면 OOM 위험이 있어 개수를 제한한다.
    */
    List<Course> courses =
        courseRepository
            .findByUserId(
                userId,
                null,
                null,
                PageRequest.of(0, RECENT_TUTORING_LIMIT, Sort.by(Sort.Direction.DESC, "updatedAt")))
            .getContent();

    if (courses.isEmpty()) {
      log.debug("[CourseService] getRecentTutoring() - END | 과외 없음");
      return RecentTutoringResponse.builder().items(List.of()).build();
    }

    /*
        study_time_log 에서 과외별 최근 접근 시각을 NativeQuery 로 조회한다.
        NativeQuery 는 TypedQuery 를 지원하지 않아 wildcard List 로 받은 뒤
        instanceof 패턴으로 캐스팅한다. @SuppressWarnings 가 필요 없다.
    */
    List<?> lastAccessRaw =
        entityManager
            .createNativeQuery(
                "SELECT course_id, MAX(created_at) AS last_access "
                    + "FROM study_time_log WHERE user_id = :userId "
                    + "GROUP BY course_id")
            .setParameter("userId", userId)
            .getResultList();

    Map<String, LocalDateTime> lastAccessMap = new HashMap<>();
    for (Object element : lastAccessRaw) {
      if (!(element instanceof Object[] row)) {
        continue;
      }
      String courseId = (String) row[0];
      LocalDateTime lastAccess = (row[1] instanceof Timestamp ts) ? ts.toLocalDateTime() : null;
      if (lastAccess != null) {
        lastAccessMap.put(courseId, lastAccess);
      }
    }

    /*
        과외별 진행률은 전체 챕터 대비 COMPLETED 챕터 비율이다.
    */
    List<String> courseIds = courses.stream().map(Course::getId).toList();

    /*
        JPQL createQuery(String)는 TypedQuery 를 반환하지 않아 wildcard List 로 받은 뒤
        instanceof 패턴으로 캐스팅한다. @SuppressWarnings 가 필요 없다.
    */
    List<?> chapterCountRaw =
        entityManager
            .createQuery(
                "SELECT c.course.id, c.status, COUNT(c) "
                    + "FROM Chapter c WHERE c.course.id IN :courseIds "
                    + "GROUP BY c.course.id, c.status")
            .setParameter("courseIds", courseIds)
            .getResultList();

    /* courseId → {total, completed} 집계 */
    Map<String, int[]> chapterStatsMap = new HashMap<>();
    for (Object element : chapterCountRaw) {
      if (!(element instanceof Object[] row)) {
        continue;
      }
      String courseId = (String) row[0];
      String status = row[1].toString();
      long count = (long) row[2];
      int[] stats = chapterStatsMap.computeIfAbsent(courseId, k -> new int[2]);
      stats[0] += (int) count; // total
      if ("COMPLETED".equals(status)) {
        stats[1] += (int) count; // completed
      }
    }

    /*
        최근 접근순으로 정렬해 상위 5개를 남긴다.
    */
    List<RecentTutoringResponse.RecentTutoringItem> items =
        courses.stream()
            .map(
                course -> {
                  LocalDateTime lastAccess =
                      lastAccessMap.getOrDefault(course.getId(), course.getUpdatedAt());
                  int[] stats = chapterStatsMap.getOrDefault(course.getId(), new int[] {0, 0});
                  int progressPercent = stats[0] > 0 ? (int) (stats[1] * 100.0 / stats[0]) : 0;

                  return RecentTutoringResponse.RecentTutoringItem.builder()
                      .courseId(course.getId())
                      .courseName(course.getCourseName())
                      .subject(course.getSubject())
                      .status(course.getStatus().name())
                      .progressPercent(progressPercent)
                      .lastAccessedAt(lastAccess)
                      .build();
                })
            .sorted(
                Comparator.comparing(
                    RecentTutoringResponse.RecentTutoringItem::lastAccessedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(RECENT_TUTORING_LIMIT)
            .toList();

    RecentTutoringResponse result = RecentTutoringResponse.builder().items(items).build();

    log.debug("[CourseService] getRecentTutoring() - END | itemCount: {}", items.size());
    return result;
  }

  // 과외 생성·수정·삭제

  /** 과외를 생성하고 FastAPI 에 비동기 커리큘럼 생성을 요청한다. */
  @Transactional
  public CourseDetailResponse createTutoring(
      com.swez.backend.global.auth.UserPrincipal principal, CreateTutoringRequest request) {
    log.debug("[CourseService] createTutoring() - START | userId: {}", principal.getUserId());

    User user = findUserById(principal.getUserId());

    /*
        활성 과외 수가 플랜의 max_courses 이상이면 생성을 막는다.
    */
    String planCode = user.getSubscriptionStatus().getDbValue();
    SubscriptionPlan plan = subscriptionPlanRepository.findById(planCode).orElse(null);
    if (plan != null) {
      long activeCourses = courseRepository.countActiveByUserId(user.getId());
      if (activeCourses >= plan.getMaxCourses()) {
        throw new CustomException(CourseErrorCode.CRS_012);
      }
    }

    /*
        기본 프리셋 또는 본인 소유 커스텀 튜터만 배정할 수 있다. 여기서 배정하지 않으면
        과외 상세와 강의 생성 개인화가 모두 기본 튜터로 폴백된다.
    */
    Tutor tutor = resolveTutorForAssignment(user.getId(), request.tutorId());

    Course course = courseMapper.toEntity(request, user, tutor);
    courseRepository.save(course);

    /*
        FastAPI 에 비동기 커리큘럼 생성을 요청한다 (실패해도 과외 생성은 유지).
    */
    requestCurriculumGeneration(course.getId(), request);

    /*
        갓 생성된 과외는 CURRICULUM_GENERATING 상태라 챕터가 없어 학습 강의 수가 항상 0이다.
        집계 쿼리 없이 0을 넘긴다.
    */
    CourseDetailResponse result = courseMapper.toDetailResponse(course, 0);

    log.debug("[CourseService] createTutoring() - END | courseId: {}", course.getId());
    return result;
  }

  /** 과외 생성 소스 PDF를 S3에 업로드하고 URL과 원본 메타데이터를 반환한다. */
  public SourcePdfUploadResponse uploadSourcePdf(String userId, MultipartFile file) {
    log.debug("[CourseService] uploadSourcePdf() - START | userId: {}", userId);

    validateSourcePdfFile(file);

    String originalFileName = StringUtils.getFilename(file.getOriginalFilename());
    if (originalFileName == null || originalFileName.isBlank()) {
      originalFileName = "source.pdf";
    }

    String fileName = UUID.randomUUID() + ".pdf";
    String url;
    try {
      url = s3Service.upload("source-pdfs/" + userId, fileName, file, "application/pdf");
    } catch (Exception e) {
      log.error(
          "[CourseService] uploadSourcePdf() - S3 업로드 실패 | userId: {}, error: {}",
          userId,
          e.getMessage());
      throw new CustomException(CourseErrorCode.CRS_010);
    }

    log.debug(
        "[CourseService] uploadSourcePdf() - END | userId: {}, url: {}",
        userId,
        LogMaskUtil.mask(url));
    return SourcePdfUploadResponse.builder()
        .url(url)
        .originalFileName(originalFileName)
        .size(file.getSize())
        .build();
  }

  /** 과외의 커리큘럼(챕터 목록 + 상태)을 조회한다. */
  public CurriculumResponse getCurriculum(
      com.swez.backend.global.auth.UserPrincipal principal, String courseId) {
    log.debug(
        "[CourseService] getCurriculum() - START | userId: {}, courseId: {}",
        principal.getUserId(),
        courseId);

    Course course = findCourseByIdAndUser(courseId, principal.getUserId());

    List<Chapter> chapters = chapterRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
    Map<String, LessonProgress> progressByChapterId =
        findProgressByChapterId(principal.getUserId(), chapters);

    CurriculumResponse result =
        courseMapper.toCurriculumResponse(
            course,
            chapters,
            toCompletedSlidesByChapterId(chapters, progressByChapterId),
            resolveVisibleChapterStatuses(chapters, progressByChapterId));

    log.debug(
        "[CourseService] getCurriculum() - END | courseId: {}, chapterCount: {}",
        courseId,
        chapters.size());
    return result;
  }

  /** 커리큘럼을 확정하고 FastAPI 에 강의 생성을 요청한다. CURRICULUM_READY 상태에서만 ACTIVE 로 전환된다. */
  @Transactional
  public CurriculumResponse confirmCurriculum(
      com.swez.backend.global.auth.UserPrincipal principal, String courseId) {
    log.debug(
        "[CourseService] confirmCurriculum() - START | userId: {}, courseId: {}",
        principal.getUserId(),
        courseId);

    Course course = findCourseByIdAndUser(courseId, principal.getUserId());

    /*
        CURRICULUM_GENERATING 이면 아직 생성 중(CRS_003)이고,
        CURRICULUM_FAILED 이면 커리큘럼 생성이 실패한 상태(CRS_011)다.
    */
    if (course.getStatus() == CourseStatus.CURRICULUM_GENERATING) {
      throw new CustomException(CourseErrorCode.CRS_003);
    }
    if (course.getStatus() == CourseStatus.CURRICULUM_FAILED) {
      throw new CustomException(CourseErrorCode.CRS_011);
    }

    /*
        CURRICULUM_READY → ACTIVE 전환. 상태 검증은 엔티티가 한다.
    */
    course.confirmCurriculum();

    /*
        FastAPI 에는 order_index 기준 1·2강만 먼저 생성을 요청한다.
    */
    progressiveLessonGenerationService.requestInitialLessons(principal.getUserId(), courseId);

    List<Chapter> chapters = chapterRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
    Map<String, LessonProgress> progressByChapterId =
        findProgressByChapterId(principal.getUserId(), chapters);
    CurriculumResponse result =
        courseMapper.toCurriculumResponse(
            course,
            chapters,
            toCompletedSlidesByChapterId(chapters, progressByChapterId),
            resolveVisibleChapterStatuses(chapters, progressByChapterId));

    log.debug("[CourseService] confirmCurriculum() - END | courseId: {}", courseId);
    return result;
  }

  /** 과외 정보를 부분 수정한다 (제목·과목·시험일). */
  @Transactional
  public CourseDetailResponse updateTutoring(
      com.swez.backend.global.auth.UserPrincipal principal,
      String courseId,
      UpdateTutoringRequest request) {
    log.debug(
        "[CourseService] updateTutoring() - START | userId: {}, courseId: {}",
        principal.getUserId(),
        courseId);

    Course course = findCourseByIdAndUser(courseId, principal.getUserId());

    /*
        null 이 아닌 필드만 갱신한다.
    */
    course.updateInfo(request.courseName(), request.subject(), request.examDate());

    CourseDetailResponse result =
        courseMapper.toDetailResponse(
            course, countCompletedChapters(principal.getUserId(), courseId));

    log.debug("[CourseService] updateTutoring() - END | courseId: {}", courseId);
    return result;
  }

  /** 과외를 소프트 삭제한다 (status → DELETED). */
  @Transactional
  public void deleteTutoring(
      com.swez.backend.global.auth.UserPrincipal principal, String courseId) {
    log.debug(
        "[CourseService] deleteTutoring() - START | userId: {}, courseId: {}",
        principal.getUserId(),
        courseId);

    Course course = findCourseByIdAndUser(courseId, principal.getUserId());

    /*
        이미 DELETED 면 엔티티가 CRS_006 예외를 던진다.
    */
    course.softDelete();

    log.debug("[CourseService] deleteTutoring() - END | courseId: {}", courseId);
  }

  // 내부 헬퍼

  /**
   * N+1 을 피하려고 여러 과외의 사용자별 완료(isCompleted=true) 강의(챕터) 수를 한 번에 집계한다.
   *
   * <p>completedLessons 는 완료 기준 진행률(progress)과 맞아야 하므로 진행 중(currentSlideIdx>0)은 빼고
   * 완료한 챕터만 센다. 강의를 열기만 했을 때 진행률 0%인데 1강 완료로 표시되지 않게 한다.
   *
   * @return courseId → 완료한 챕터 수 (0개인 코스는 키 없음 → 호출부에서 getOrDefault(0))
   */
  private Map<String, Integer> countCompletedChaptersByCourseIds(
      String userId, List<String> courseIds) {
    if (courseIds.isEmpty()) {
      return Map.of();
    }

    return lessonProgressRepository.countCompletedByUserIdAndCourseIdIn(userId, courseIds).stream()
        .collect(
            Collectors.toMap(
                row -> (String) row[0],
                row -> Math.toIntExact((Long) row[1])));
  }

  /** 단일 과외의 사용자별 완료(isCompleted=true) 강의(챕터) 수를 집계한다. */
  private int countCompletedChapters(String userId, String courseId) {
    return Math.toIntExact(
        lessonProgressRepository.countCompletedByCourseIdAndUserId(courseId, userId));
  }

  /**
   * 과외를 조회하고 소유권을 검증한다. 없거나 소유자가 달라도 같은 CRS_001 을 던져 존재 여부를 드러내지
   * 않는다.
   */
  private Course findCourseByIdAndUser(String courseId, String userId) {
    Course course =
        courseRepository
            .findById(courseId)
            .orElseThrow(() -> new CustomException(CourseErrorCode.CRS_001));
    if (!course.getUser().getId().equals(userId)) {
      throw new CustomException(CourseErrorCode.CRS_001);
    }
    return course;
  }

  /** userId 로 사용자를 조회한다. 없으면 USER_001 예외를 던진다. */
  private User findUserById(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(AuthErrorCode.USER_001));
  }

  /** 챕터 목록에 대한 사용자별 진행률을 조회한다. */
  private Map<String, LessonProgress> findProgressByChapterId(String userId, List<Chapter> chapters) {
    if (chapters.isEmpty()) {
      return Map.of();
    }

    List<String> chapterIds = chapters.stream().map(Chapter::getId).toList();
    return lessonProgressRepository.findByUserIdAndChapterIdIn(userId, chapterIds).stream()
        .collect(
            Collectors.toMap(
                progress -> progress.getChapter().getId(),
                progress -> progress,
                this::selectMoreAdvancedProgress));
  }

  /**
   * 챕터 목록에 대한 사용자별 완료 슬라이드 수를 계산한다.
   *
   * <p>Chapter.completedSlides는 챕터 공통 집계 컬럼이고, 화면에 필요한 값은 로그인한 사용자의 LessonProgress 기준 값이다.
   */
  private Map<String, Integer> toCompletedSlidesByChapterId(
      List<Chapter> chapters, Map<String, LessonProgress> progressByChapterId) {
    if (progressByChapterId.isEmpty()) {
      return Map.of();
    }

    return chapters.stream()
        .filter(chapter -> progressByChapterId.containsKey(chapter.getId()))
        .collect(
            Collectors.toMap(
                Chapter::getId,
                chapter ->
                    resolveCompletedSlides(
                        progressByChapterId.get(chapter.getId()), chapter.getTotalSlides())));
  }

  /**
   * 사용자에게 노출할 챕터 상태를 계산한다.
   *
   * <p>FastAPI는 다음 강의를 미리 생성해 public.chapter.status를 AVAILABLE로 바꿀 수 있다. 하지만 학생 화면에서는 완료한
   * 챕터 다음의 1개 강의만 열려야 하므로, DB 상태를 그대로 노출하지 않고 LessonProgress 기준으로 마스킹한다.
   */
  private Map<String, ChapterStatus> resolveVisibleChapterStatuses(
      List<Chapter> chapters, Map<String, LessonProgress> progressByChapterId) {
    if (chapters.isEmpty()) {
      return Map.of();
    }

    Map<String, ChapterStatus> visibleStatusByChapterId = new HashMap<>();
    boolean canOpenNext = true;

    for (Chapter chapter : chapters) {
      LessonProgress progress = progressByChapterId.get(chapter.getId());
      if (isCompleted(progress)) {
        visibleStatusByChapterId.put(chapter.getId(), ChapterStatus.COMPLETED);
        canOpenNext = true;
        continue;
      }

      if (canOpenNext && isGenerated(chapter)) {
        visibleStatusByChapterId.put(
            chapter.getId(), hasStarted(progress) ? ChapterStatus.IN_PROGRESS : ChapterStatus.AVAILABLE);
        canOpenNext = false;
        continue;
      }

      visibleStatusByChapterId.put(chapter.getId(), ChapterStatus.LOCKED);
      canOpenNext = false;
    }

    return visibleStatusByChapterId;
  }

  private LessonProgress selectMoreAdvancedProgress(
      LessonProgress left, LessonProgress right) {
    if (isCompleted(left) != isCompleted(right)) {
      return isCompleted(left) ? left : right;
    }
    return rawSlideProgress(left) >= rawSlideProgress(right) ? left : right;
  }

  private int resolveCompletedSlides(LessonProgress progress, Integer totalSlides) {
    int safeTotalSlides = Math.max(0, totalSlides == null ? 0 : totalSlides);
    if (progress == null) {
      return 0;
    }
    if (isCompleted(progress)) {
      return safeTotalSlides;
    }
    return Math.max(0, Math.min(rawSlideProgress(progress), safeTotalSlides));
  }

  private int rawSlideProgress(LessonProgress progress) {
    return progress == null || progress.getCurrentSlideIdx() == null
        ? 0
        : progress.getCurrentSlideIdx();
  }

  private boolean isCompleted(LessonProgress progress) {
    return progress != null && Boolean.TRUE.equals(progress.getIsCompleted());
  }

  private boolean hasStarted(LessonProgress progress) {
    return progress != null
        && progress.getCurrentSlideIdx() != null
        && progress.getCurrentSlideIdx() > 0;
  }

  private boolean isGenerated(Chapter chapter) {
    return chapter.getStatus() != ChapterStatus.LOCKED;
  }

  /** 소스 PDF 파일의 존재·Content-Type·크기·Magic Bytes 를 검증한다. */
  private void validateSourcePdfFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new CustomException(CourseErrorCode.CRS_007);
    }

    String contentType = file.getContentType();
    if (!"application/pdf".equalsIgnoreCase(contentType)) {
      throw new CustomException(CourseErrorCode.CRS_008);
    }

    if (file.getSize() > MAX_SOURCE_PDF_SIZE) {
      throw new CustomException(CourseErrorCode.CRS_009);
    }

    validatePdfMagicBytes(file);
  }

  /** PDF Magic Bytes(%PDF-)를 검증해 Content-Type 스푸핑을 줄인다. */
  private void validatePdfMagicBytes(MultipartFile file) {
    try {
      byte[] header = file.getInputStream().readNBytes(5);
      boolean isPdf =
          header.length >= 5
              && header[0] == 0x25
              && header[1] == 0x50
              && header[2] == 0x44
              && header[3] == 0x46
              && header[4] == 0x2D;
      if (!isPdf) {
        throw new CustomException(CourseErrorCode.CRS_008);
      }
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new CustomException(CourseErrorCode.CRS_007);
    }
  }

  /**
   * 과외 생성 시 배정할 튜터를 조회한다.
   *
   * <p>tutorId 가 비어 있으면 튜터를 배정하지 않고 null 을 반환하며, 표시·강의 생성 단계에서 기본 튜터로
   * 폴백한다. 기본 프리셋 튜터(isDefault=true)이거나 본인 소유 커스텀 튜터만 배정할 수 있고, 없거나 다른
   * 사용자의 튜터면 TUT_001 예외를 던진다.
   *
   * @param tutorId 요청에 담긴 튜터 ID (null 허용)
   * @return 배정할 Tutor 엔티티, tutorId 미지정 시 null
   */
  private Tutor resolveTutorForAssignment(String userId, String tutorId) {
    if (!StringUtils.hasText(tutorId)) {
      return null;
    }
    Tutor tutor =
        tutorRepository
            .findById(tutorId)
            .orElseThrow(() -> new CustomException(TutorErrorCode.TUT_001));
    boolean ownedByUser = tutor.getUser() != null && userId.equals(tutor.getUser().getId());
    if (!tutor.isDefault() && !ownedByUser) {
      throw new CustomException(TutorErrorCode.TUT_001);
    }
    return tutor;
  }

  /** FastAPI 에 커리큘럼 생성을 비동기 요청한다. 실패하면 CRS_005 예외를 던진다. */
  private void requestCurriculumGeneration(String courseId, CreateTutoringRequest request) {
    String url = fastapiBaseUrl + "/api/curriculum/generate";
    try {
      Map<String, Object> body =
          Map.of(
              "courseId", courseId,
              "subject", request.subject(),
              "lessonCount", request.lessonCount(),
              "sourceType", request.sourceType());
      restTemplate.postForEntity(url, body, Void.class);
      log.info("[CourseService] 커리큘럼 생성 요청 성공 | courseId: {}", courseId);
    } catch (RestClientException e) {
      log.error(
          "[CourseService] 커리큘럼 생성 요청 실패 | courseId: {}, error: {}", courseId, e.getMessage());
      throw new CustomException(CourseErrorCode.CRS_005);
    }
  }
}
