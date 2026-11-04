// 튜터 API 응답을 화면 선택 모델로 바꾼다. 기본 튜터 개수는 가정하지 않는다.
import { ApiError } from '@/apis';
import type { TutorResponse } from '@/apis';
import type { TeacherItem } from './types';

export function toTeacherItem(tutor: TutorResponse): TeacherItem {
  return {
    id: tutor.tutorId,
    n: tutor.name,
    d: tutor.tagline || (tutor.isDefault ? '기본 튜터' : '나만의 튜터'),
    photo: tutor.profileImageUrl || undefined,
    isDefault: tutor.isDefault,
  };
}

export function splitTutorItems(tutors: TutorResponse[]): {
  defaultTutors: TeacherItem[];
  customTutors: TeacherItem[];
  allTutors: TeacherItem[];
} {
  const defaultTutors = tutors.filter(t => t.isDefault).map(toTeacherItem);
  const customTutors = tutors.filter(t => !t.isDefault).map(toTeacherItem);
  return { defaultTutors, customTutors, allTutors: [...defaultTutors, ...customTutors] };
}

/**
 * 기본 튜터 개수와 무관하게 항상 null을 반환한다.
 * 백엔드 시드가 달라져도 화면을 막지 않고, 빈 배열이면 TutorSection이 emptyText를 보여 준다.
 * useCourseExploreTutors 호출부를 그대로 두려고 시그니처를 유지한다.
 */
export function createDefaultTutorCountError(_defaultCount: number): string | null {
  return null;
}

export function createTutorLoadError(error: unknown): string {
  if (error instanceof ApiError && error.status === 0) {
    return '튜터 목록 서버에 연결하지 못했습니다. 백엔드 실행 상태를 확인해 주세요.';
  }
  return '튜터 목록을 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.';
}
