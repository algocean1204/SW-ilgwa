// ChapterStudio_V1 응답 계약 타입. 프론트는 완성 콘텐츠를 다시 조립하지 않는다.

/** API 공통 응답 래퍼 */
export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp: string;
}

/** 커리큘럼 생성·확정 상태 */
export type CurriculumStatus =
  | 'draft'
  | 'curriculum_generating'
  | 'curriculum_ready'
  | 'confirmed'
  | 'active'
  | 'completed'
  | 'failed';

/** 강의 생성 상태 */
export type LessonGenerationStatus = 'queued' | 'generating' | 'ready' | 'failed';

/** 커리큘럼의 강의 계획 */
export interface CurriculumLessonDto {
  lessonId: string;
  orderIndex: number;
  title: string;
  summary: string;
  slideCount: number;
  estimatedMinutes: number;
  status: LessonGenerationStatus | 'draft';
}

/** 커리큘럼 조회 응답 */
export interface CurriculumDto {
  tutoringId: string;
  title: string;
  status: CurriculumStatus;
  lessons: CurriculumLessonDto[];
}

/** DB에 저장된 완성 슬라이드 렌더 단위 */
export interface LessonSlideDto {
  slideId: string;
  slideIdx: number;
  title: string;
  category: string;
  templateId: string;
  iframeHtml: string;
  audioUrl: string | null;
  audioDuration: number | null;
  cueStartSec: number | null;
  cueEndSec: number | null;
  validationStatus: 'passed' | 'pending' | 'failed';
}

/** 강의 슬라이드 조회 응답 */
export interface LessonSlidesDto {
  tutoringId: string;
  lessonId: string;
  lessonTitle: string;
  generationStatus: LessonGenerationStatus;
  slides: LessonSlideDto[];
}

/** 퀴즈는 정답 공개와 채점을 위해 구조 데이터로 유지한다. */
export interface LessonQuizDto {
  quizId: string;
  quizIdx: number;
  question: string;
  choices: string[];
  answerIdx: number;
  explanation: string;
  difficulty: 'easy' | 'medium' | 'hard';
}

/** 강의 단위 요약 노트 */
export interface LessonNoteDto {
  lessonId: string;
  html: string;
}

/** 강의 과제 */
export interface LessonAssignmentDto {
  assignmentId: string;
  lessonId: string;
  title: string;
  prompt: string;
  criteria: string[];
  expectedMinutes: number;
  displayHtml: string | null;
}
