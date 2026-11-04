// ChapterStudio_V1 API 클라이언트. 화면 컴포넌트가 직접 fetch하지 않게 분리했다.
import { env } from '@/core/env';
import type {
  ApiEnvelope,
  CurriculumDto,
  LessonQuizDto,
  LessonSlidesDto,
} from '@/shared/types/chapter-studio';

const jsonHeaders = {
  Accept: 'application/json',
} as const;

/** 실패 응답을 한 지점에서 표준화한다. */
async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`ChapterStudio API 실패: ${response.status} ${body}`);
  }
  return response.json() as Promise<T>;
}

/** 커리큘럼 초안 또는 확정본을 조회한다. */
export async function fetchCurriculum(tutoringId: string): Promise<CurriculumDto> {
  const response = await fetch(`${env.apiSpring}/api/tutoring/${tutoringId}/curriculum`, {
    headers: jsonHeaders,
  });
  const payload = await readJson<ApiEnvelope<CurriculumDto>>(response);
  return payload.data;
}

/** 강의 1개에 저장된 완성 슬라이드 iframeHtml 목록을 조회한다. */
export async function fetchLessonSlides(
  tutoringId: string,
  lessonId: string,
): Promise<LessonSlidesDto> {
  const response = await fetch(`${env.apiSpring}/api/tutoring/${tutoringId}/lessons/${lessonId}/slides`, {
    headers: jsonHeaders,
  });
  const payload = await readJson<ApiEnvelope<LessonSlidesDto>>(response);
  return payload.data;
}

/** 퀴즈는 슬라이드 iframe과 분리된 구조 데이터로 조회한다. */
export async function fetchLessonQuizzes(
  tutoringId: string,
  lessonId: string,
): Promise<LessonQuizDto[]> {
  const response = await fetch(`${env.apiSpring}/api/tutoring/${tutoringId}/lessons/${lessonId}/quizzes`, {
    headers: jsonHeaders,
  });
  const payload = await readJson<ApiEnvelope<{ quizzes: LessonQuizDto[] }>>(response);
  return payload.data.quizzes;
}
