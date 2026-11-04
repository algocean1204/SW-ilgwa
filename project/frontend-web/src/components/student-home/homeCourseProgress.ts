import type { CourseSummaryResponse } from '@/apis';
import { DRAFT_STATUSES } from '@/shared/constants/courseStatus';

export function isCourseFullyCompleted(course: CourseSummaryResponse): boolean {
  if (DRAFT_STATUSES.has(course.status)) return false;
  if (course.status === 'COMPLETED') return true;

  const totalLessons = Math.max(0, course.totalLessons);
  if (totalLessons === 0) return false;

  return Math.max(0, course.completedLessons) >= totalLessons;
}
