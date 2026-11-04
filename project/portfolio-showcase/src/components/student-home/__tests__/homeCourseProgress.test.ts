import { describe, expect, it } from 'vitest';
import type { CourseStatus, CourseSummaryResponse } from '@/apis';
import { isCourseFullyCompleted } from '../homeCourseProgress';

function course(partial: Partial<CourseSummaryResponse>): CourseSummaryResponse {
  return {
    id: 'course-1',
    title: 'course',
    subject: 'subject',
    tutorName: 'tutor',
    tutorAvatarUrl: '',
    status: 'ACTIVE',
    progress: 0,
    totalLessons: 3,
    completedLessons: 0,
    lastStudiedAt: '',
    createdAt: '',
    ...partial,
  };
}

describe('isCourseFullyCompleted', () => {
  it('COMPLETED 상태면 완료로 본다', () => {
    expect(isCourseFullyCompleted(course({ status: 'COMPLETED' }))).toBe(true);
  });

  it('ACTIVE여도 완료 강의 수가 전체 이상이면 완료로 본다', () => {
    expect(isCourseFullyCompleted(course({ status: 'ACTIVE', completedLessons: 3, totalLessons: 3 }))).toBe(true);
    expect(isCourseFullyCompleted(course({ status: 'ACTIVE', completedLessons: 4, totalLessons: 3 }))).toBe(true);
  });

  it('남은 강의가 있거나 전체 강의 수가 없으면 완료로 보지 않는다', () => {
    expect(isCourseFullyCompleted(course({ completedLessons: 2, totalLessons: 3 }))).toBe(false);
    expect(isCourseFullyCompleted(course({ completedLessons: 0, totalLessons: 0 }))).toBe(false);
  });

  it('초안/생성 중 상태는 완료 강의 수만으로 완료 처리하지 않는다', () => {
    const draftStatuses: CourseStatus[] = ['CURRICULUM_GENERATING', 'CURRICULUM_READY', 'CURRICULUM_FAILED'];

    for (const status of draftStatuses) {
      expect(isCourseFullyCompleted(course({ status, completedLessons: 3, totalLessons: 3 }))).toBe(false);
    }
  });
});
