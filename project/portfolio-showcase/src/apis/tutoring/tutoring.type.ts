export type CourseStatus =
  | 'CURRICULUM_GENERATING'
  | 'CURRICULUM_READY'
  | 'CURRICULUM_FAILED'
  | 'ACTIVE'
  | 'COMPLETED'
  | 'PAUSED'
  | 'DELETED';

export type ChapterStatus = 'LOCKED' | 'AVAILABLE' | 'IN_PROGRESS' | 'COMPLETED';

export interface CourseSummaryResponse {
  id: string;
  title: string;
  subject: string;
  tutorName: string;
  tutorAvatarUrl: string;
  status: CourseStatus;
  progress: number;
  totalLessons: number;
  completedLessons: number;
  lastStudiedAt: string;
  createdAt: string;
}

export interface CourseDetailResponse {
  id: string;
  title: string;
  subject: string;
  tutorId: string;
  tutorName: string;
  tutorAvatarUrl: string;
  status: CourseStatus;
  sourceType: string;
  mode: string;
  progress: number;
  totalLessons: number;
  completedLessons: number;
  lastStudiedAt: string;
  createdAt: string;
}

export interface ChapterSummaryResponse {
  id: string;
  order: number;
  title: string;
  description: string;
  estimatedMinutes: number | null;
  status: ChapterStatus;
  slideCount: number;
  completedSlides: number;
  completedAt: string | null;
}

/** 강의 생성 진행 상태: GET /tutoring/{courseId}/lessons/{chapterId}/generation-status */
export interface LessonGenerationStatusResponse {
  lessonId: string;
  /** unknown(조회 불가·행 없음) / pending / running / done / failed */
  status: string;
  /** 마지막으로 완료된 생성 노드명 (prepare_context → generate_lesson → content_verify → synthesize_audio → postprocess_slides) */
  currentNode: string | null;
  completedNodes: number;
  totalNodes: number;
  /** 0~100 노드 단위 진행률 */
  progressPercent: number;
  errorMessage: string | null;
}

export interface BookmarkInChapterDto {
  slideIndex: number;
  memo: string;
  createdAt: string;
}

export interface ChapterDetailResponse {
  id: string;
  order: number;
  title: string;
  description: string;
  estimatedMinutes: number | null;
  status: ChapterStatus;
  slideCount: number;
  keyTopics: string[];
  bookmarks: BookmarkInChapterDto[];
  completedAt: string | null;
  xpEarned: number;
}

export interface CurriculumResponse {
  courseId: string;
  status: string;
  chapters: ChapterSummaryResponse[];
}

export interface RecentTutoringItem {
  courseId: string;
  courseName: string;
  subject: string;
  status: string;
  progressPercent: number;
  lastAccessedAt: string;
}

export interface RecentTutoringResponse {
  items: RecentTutoringItem[];
}

export interface SourcePdfUploadResponse {
  url: string;
  originalFileName: string;
  size: number;
}

export type TutoringSourceType = 'pdf' | 'topic';

export interface CreateTutoringRequest {
  title: string;
  topic?: string;
  subject: string;
  sourceType: TutoringSourceType;
  sourceRef?: string;
  durationDays?: number;
  difficulty?: string;
  tutorId?: string;
  lessonCount: number;
  audienceLevel?: string;
  learningGoal?: string;
}

export interface UpdateTutoringRequest {
  courseName?: string;
  subject?: string;
  examDate?: string;
}

export interface GetMyCoursesParams {
  status?: CourseStatus;
  subject?: string;
  page?: number;
  size?: number;
}
