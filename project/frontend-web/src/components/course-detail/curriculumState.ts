// 커리큘럼 API 응답을 화면 상태로 바꾸는 순수 헬퍼
import { ApiError, getCurriculum } from '@/apis';
import type { ChapterSummaryResponse, CourseDetailResponse, CourseStatus, CurriculumResponse } from '@/apis';
import type { Chapter } from './types';

export const CURRICULUM_POLL_INTERVAL_MS = 4_000;
export const CURRICULUM_POLL_TIMEOUT_MS = 5 * 60_000;

export interface CurriculumSnapshot {
  status: string;
  chapters: Chapter[];
}

export type CurriculumPhase = 'draft' | 'confirmed';

export function loadErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 404) {
      return '선택한 과외를 찾을 수 없습니다. 내 과외 목록을 다시 확인해 주세요.';
    }
    if (error.status === 0) {
      return '서버에 연결하지 못했습니다. 백엔드 실행 상태를 확인한 뒤 다시 시도해 주세요.';
    }
  }
  return '과외 정보를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.';
}

export function applyCourseMeta(
  course: CourseDetailResponse,
  setters: {
    setTitle: (v: string) => void;
    setTutorId: (v: string) => void;
    setTutorName: (v: string) => void;
    setTutorAvatarUrl: (v: string) => void;
  },
): void {
  setters.setTitle(course.title);
  setters.setTutorId(course.tutorId);
  setters.setTutorName(course.tutorName);
  setters.setTutorAvatarUrl(course.tutorAvatarUrl);
}

export function mapChapter(ch: ChapterSummaryResponse): Chapter {
  return {
    id: ch.id,
    t: ch.title,
    s: Number.isFinite(ch.slideCount) ? ch.slideCount : 0,
    m: ch.estimatedMinutes ?? 0,
    status: ch.status === 'COMPLETED'
      ? 'done'
      : ch.status === 'IN_PROGRESS'
        ? 'active'
        : ch.status === 'AVAILABLE'
          ? 'available'
          : 'locked',
  };
}

export function phaseFromStatus(
  courseStatus: CourseStatus,
  curriculumStatus?: string,
): CurriculumPhase {
  // CURRICULUM_GENERATING도 확정 전 단계라 draft로 본다. 상태가 GENERATING에 멈춘 채
  // 챕터가 이미 있는(데이터 불일치) 코스를 전부 잠긴 readOnly 화면 대신 검토 화면으로 연다
  return courseStatus === 'CURRICULUM_READY'
    || courseStatus === 'CURRICULUM_GENERATING'
    || curriculumStatus === 'CURRICULUM_READY'
    ? 'draft'
    : 'confirmed';
}

export function mapCurriculum(data: CurriculumResponse | null): CurriculumSnapshot {
  return {
    status: data?.status ?? '',
    chapters: (data?.chapters ?? []).map(mapChapter),
  };
}

export async function loadCurriculum(courseId: string): Promise<CurriculumSnapshot> {
  const currRes = await getCurriculum(courseId);
  return mapCurriculum(currRes.data);
}

export async function tryLoadCurriculum(courseId: string): Promise<CurriculumSnapshot | null> {
  try {
    return await loadCurriculum(courseId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export function hasReadyCurriculum(
  curriculum: CurriculumSnapshot | null,
): curriculum is CurriculumSnapshot {
  return !!curriculum && (curriculum.status === 'CURRICULUM_READY' || curriculum.chapters.length > 0);
}

/** CURRICULUM_FAILED인지 확인한다. 참이면 폴링을 멈춘다 */
export function isCurriculumFailed(courseStatus: CourseStatus): boolean {
  return courseStatus === 'CURRICULUM_FAILED';
}
