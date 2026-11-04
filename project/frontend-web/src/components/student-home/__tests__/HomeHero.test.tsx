import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { CourseSummaryResponse } from '@/apis';
import HomeHero from '../HomeHero';

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

describe('HomeHero', () => {
  it('모든 강의를 완료한 과외는 이어서 학습하기 버튼을 비활성화한다', () => {
    const setRoute = vi.fn();

    render(
      <HomeHero
        activeCourse={course({ completedLessons: 3, totalLessons: 3 })}
        currentChapter={null}
        missions={[]}
        setRoute={setRoute}
        courses={[]}
        activeCourseId="course-1"
        onCourseSelect={vi.fn()}
        onContinueChapterSelect={vi.fn()}
        slideProgressPct={100}
        completedSlides={0}
        totalSlides={0}
      />,
    );

    const button = screen.getByRole('button', { name: /학습 완료/ });
    expect(button).toHaveProperty('disabled', true);
    fireEvent.click(button);
    expect(setRoute).not.toHaveBeenCalled();
  });
});
