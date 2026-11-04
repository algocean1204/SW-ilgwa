// 목록 항목 진입 ID. cutover 항목은 eat_(attemptId), 레거시 항목은 mex_(id)로 콜백을 불러야 한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ExamOverview from '../ExamOverview';
import type { MockExamListResponse, CourseSummaryResponse } from '@/apis';
import type { Palette } from '@/lib';

// 픽스처

/** 디자인 토큰 없이 타입만 맞춘 더미 팔레트 */
const dummyPalette: Palette = {
  primary: '#000',
  ink: '#000',
  wash: '#eee',
  tag: '#ccc',
} as unknown as Palette;

const dummyCourse: CourseSummaryResponse = {
  id: 'crs-1',
  title: '수학 과외',
  progress: 80,
  completedLessons: 8,
  totalLessons: 10,
} as unknown as CourseSummaryResponse;

/** attemptId(eat_)가 채워진 READY 항목 */
function makeReadyItem(
  id: string,
  attemptId: string | null | undefined,
): MockExamListResponse {
  return {
    id,
    title: `시험 ${id}`,
    examType: 'multiple_choice',
    questionCount: 10,
    difficulty: 'medium',
    timeLimit: 60,
    status: 'READY',
    totalPoints: 100,
    createdAt: '2026-01-01T00:00:00Z',
    attemptId,
  };
}

/** COMPLETED 항목 */
function makeCompletedItem(
  id: string,
  attemptId: string | null | undefined,
): MockExamListResponse {
  return { ...makeReadyItem(id, attemptId), status: 'COMPLETED' };
}

function makeProps(
  overrides: Partial<Parameters<typeof ExamOverview>[0]> = {},
): Parameters<typeof ExamOverview>[0] {
  return {
    courses: [dummyCourse],
    selectedCourseId: 'crs-1',
    examList: [],
    listLoading: false,
    coins: 10,
    cost: 1,
    palette: dummyPalette,
    courseNotCompleted: false,
    onSelectCourse: vi.fn(),
    onGenerate: vi.fn(),
    // vi.fn() 반환 타입을 ExamOverview props에 맞게 캐스팅한다.
    onOpenExam: vi.fn() as (examOrAttemptId: string) => void,
    onViewResult: vi.fn() as (examOrAttemptId: string) => void,
    onNeedCoins: vi.fn(),
    ...overrides,
  };
}

describe('ExamOverview — 목록 진입 ID 전달 검증', () => {
  it('READY 항목 클릭 시 onOpenExam에 attemptId(eat_)를 전달하고 mex_id(e.id)는 전달하지 않는다', async () => {
    const user = userEvent.setup();
    // 테스트마다 스파이를 따로 둔다.
    const onOpenExam = vi.fn() as (examOrAttemptId: string) => void;

    // mex_ id와 eat_ id를 일부러 다르게 둔다.
    const examItem = makeReadyItem('mex_abc123', 'eat_xyz789');
    const props = makeProps({ examList: [examItem], onOpenExam });

    render(<ExamOverview {...props} />);

    const btn = screen.getByRole('button', { name: /시험 mex_abc123/ });
    await user.click(btn);

    expect(onOpenExam).toHaveBeenCalledTimes(1);
    expect(onOpenExam).toHaveBeenCalledWith('eat_xyz789');

    expect(onOpenExam).not.toHaveBeenCalledWith('mex_abc123');
  });

  it('COMPLETED 항목 클릭 시 onViewResult에 attemptId(eat_)를 전달한다', async () => {
    const user = userEvent.setup();
    const onViewResult = vi.fn() as (examOrAttemptId: string) => void;

    const examItem = makeCompletedItem('mex_done01', 'eat_done99');
    const props = makeProps({ examList: [examItem], onViewResult });

    render(<ExamOverview {...props} />);

    const btn = screen.getByRole('button', { name: /시험 mex_done01/ });
    await user.click(btn);

    expect(onViewResult).toHaveBeenCalledTimes(1);
    expect(onViewResult).toHaveBeenCalledWith('eat_done99');
    expect(onViewResult).not.toHaveBeenCalledWith('mex_done01');
  });

  it('attemptId가 null인 READY 레거시 항목은 examId(mex_)로 onOpenExam을 호출한다', async () => {
    const user = userEvent.setup();
    const onOpenExam = vi.fn() as (examOrAttemptId: string) => void;

    // 레거시 항목: attemptId 없음
    const legacyItem = makeReadyItem('mex_legacy', null);
    const props = makeProps({ examList: [legacyItem], onOpenExam });

    render(<ExamOverview {...props} />);

    const btn = screen.getByRole('button', { name: /시험 mex_legacy/ });
    expect((btn as HTMLButtonElement).disabled).toBe(false);
    await user.click(btn);
    expect(onOpenExam).toHaveBeenCalledTimes(1);
    expect(onOpenExam).toHaveBeenCalledWith('mex_legacy');
  });

  it('attemptId가 undefined인 COMPLETED 레거시 항목은 examId(mex_)로 onViewResult를 호출한다', async () => {
    const user = userEvent.setup();
    const onViewResult = vi.fn() as (examOrAttemptId: string) => void;

    const legacyItem = makeCompletedItem('mex_legacy2', undefined);
    const props = makeProps({ examList: [legacyItem], onViewResult });

    render(<ExamOverview {...props} />);

    const btn = screen.getByRole('button', { name: /시험 mex_legacy2/ });
    expect((btn as HTMLButtonElement).disabled).toBe(false);
    await user.click(btn);
    expect(onViewResult).toHaveBeenCalledTimes(1);
    expect(onViewResult).toHaveBeenCalledWith('mex_legacy2');
  });

  it('GENERATING 항목은 attemptId 유무와 무관하게 disabled이고 onOpenExam이 호출되지 않는다', async () => {
    const user = userEvent.setup();
    const onOpenExam = vi.fn() as (examOrAttemptId: string) => void;

    const generatingItem: MockExamListResponse = {
      ...makeReadyItem('mex_gen', 'eat_gen'),
      status: 'GENERATING',
    };
    const props = makeProps({ examList: [generatingItem], onOpenExam });

    render(<ExamOverview {...props} />);

    const btn = screen.getByRole('button', { name: /시험 mex_gen/ });
    expect((btn as HTMLButtonElement).disabled).toBe(true);
    await user.click(btn);
    expect(onOpenExam).not.toHaveBeenCalled();
  });

  it('목록 메타 라인에 난이도가 한글 라벨로 표시된다', () => {
    const items = [
      { ...makeReadyItem('mex_easy', 'eat_easy'), difficulty: 'easy' },
      { ...makeReadyItem('mex_hard', 'eat_hard'), difficulty: 'hard' },
    ];
    render(<ExamOverview {...makeProps({ examList: items })} />);

    expect(screen.getByText(/· 쉬움$/)).toBeTruthy();
    expect(screen.getByText(/· 어려움$/)).toBeTruthy();
    expect(screen.queryByText(/· easy$/)).toBeNull();
    expect(screen.queryByText(/· hard$/)).toBeNull();
  });

  it('여러 항목 중 올바른 항목의 attemptId만 전달된다', async () => {
    const user = userEvent.setup();
    const onOpenExam = vi.fn() as (examOrAttemptId: string) => void;

    const items = [
      makeReadyItem('mex_first', 'eat_first'),
      makeReadyItem('mex_second', 'eat_second'),
    ];
    const props = makeProps({ examList: items, onOpenExam });

    render(<ExamOverview {...props} />);

    // 두 번째 항목만 클릭
    const btn = screen.getByRole('button', { name: /시험 mex_second/ });
    await user.click(btn);

    expect(onOpenExam).toHaveBeenCalledTimes(1);
    expect(onOpenExam).toHaveBeenCalledWith('eat_second');
    expect(onOpenExam).not.toHaveBeenCalledWith('eat_first');
  });
});
