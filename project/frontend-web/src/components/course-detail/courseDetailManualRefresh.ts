// 사용자가 누르면 바로 다시 조회하는 액션
import { getCourse } from '@/apis';
import type { CourseDetailResponse } from '@/apis';
import {
  hasReadyCurriculum,
  isCurriculumFailed,
  loadCurriculum,
  tryLoadCurriculum,
  type CurriculumSnapshot,
} from './curriculumState';

interface ManualRefreshParams {
  courseId: string;
  applyCurrentCourseMeta: (course: CourseDetailResponse) => void;
  applyReadyCurriculum: (course: CourseDetailResponse, curriculum: CurriculumSnapshot) => void;
  setGenerating: (v: boolean) => void;
  setGenerationNotice: (v: string | null) => void;
  setGenerationTimedOut: (v: boolean) => void;
  // 수동 재확인에서 CURRICULUM_FAILED를 받으면 실패 상태로 바꾸는 데 쓴다
  setCurriculumFailed: (v: boolean) => void;
}

export async function refreshCourseDetailNow({
  courseId,
  applyCurrentCourseMeta,
  applyReadyCurriculum,
  setGenerating,
  setGenerationNotice,
  setGenerationTimedOut,
  setCurriculumFailed,
}: ManualRefreshParams): Promise<void> {
  setGenerationTimedOut(false);
  setGenerationNotice('지금 상태를 다시 확인하고 있습니다.');
  try {
    const courseRes = await getCourse(courseId);
    if (!courseRes.data) throw new Error('Course response is empty');
    const course = courseRes.data;
    applyCurrentCourseMeta(course);

    // 수동 재확인에서도 CURRICULUM_FAILED면 바로 실패 상태로 바꾼다
    if (isCurriculumFailed(course.status)) {
      setGenerating(false);
      setCurriculumFailed(true);
      setGenerationNotice(null);
      return;
    }

    const curriculum = course.status === 'CURRICULUM_GENERATING'
      ? await tryLoadCurriculum(courseId)
      : await loadCurriculum(courseId);

    if (course.status === 'CURRICULUM_GENERATING' && !hasReadyCurriculum(curriculum)) {
      setGenerating(true);
      setCurriculumFailed(false);
      setGenerationNotice('아직 생성 중입니다. 자동 확인을 계속합니다.');
      return;
    }
    if (curriculum) applyReadyCurriculum(course, curriculum);
  } catch {
    setGenerationNotice('상태 확인에 실패했습니다. 잠시 뒤 다시 시도해 주세요.');
  }
}
