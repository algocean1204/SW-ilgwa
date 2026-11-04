// authReady=false이면 getMyCourses를 부르지 않고, true로 바뀌면 부르는지 확인한다.
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act } from '@testing-library/react';
import type { AppState } from '@/shared/types';

// WorkspaceContextStrip이 내부에서 부르는 getMyCourses를 모킹한다
vi.mock('@/apis', () => ({
  getMyCourses: vi.fn(),
}));

// 컴포넌트를 render하고 getMyCourses 호출 횟수로 확인한다.
import { render } from '@testing-library/react';
import { WorkspaceContextStrip } from '../WorkspaceContextStrip';

// 테스트 픽스처
function makeAppState(authReady: boolean): AppState {
  return {
    authReady,
    activeCourseId: 'course-1',
    setActiveCourseId: vi.fn(),
    coins: 0,
    setCoins: vi.fn(),
    plan: 'free',
    setPlan: vi.fn(),
    activeHomeworkFilter: 'all',
    setActiveHomeworkFilter: vi.fn(),
    selectedHomeworkId: '',
    setSelectedHomeworkId: vi.fn(),
    pageSubView: null,
    setPageSubView: vi.fn(),
    activeModal: null,
    setActiveModal: vi.fn(),
    hasCourses: null,
    setHasCourses: vi.fn(),
    selectedChapterId: '',
    setSelectedChapterId: vi.fn(),
  } as AppState;
}

describe('WorkspaceContextStrip — authReady 게이팅 (#5)', () => {
  let getMyCoursesMock: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    vi.clearAllMocks();
    const apis = await import('@/apis');
    getMyCoursesMock = apis.getMyCourses as ReturnType<typeof vi.fn>;
    // 기본 응답(200)
    getMyCoursesMock.mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 },
      success: true,
      message: null,
      timestamp: '',
    });
  });

  it('authReady=false이면 getMyCourses를 호출하지 않는다', () => {
    const appState = makeAppState(false);
    render(
      <WorkspaceContextStrip
        route="home"
        setRoute={vi.fn()}
        appState={appState}
      />,
    );
    // 부트스트랩 전에는 보호 API 호출이 0건이다
    expect(getMyCoursesMock).not.toHaveBeenCalled();
  });

  it('authReady=true이면 getMyCourses를 호출한다', async () => {
    const appState = makeAppState(true);
    await act(async () => {
      render(
        <WorkspaceContextStrip
          route="home"
          setRoute={vi.fn()}
          appState={appState}
        />,
      );
    });
    // 부트스트랩 후에는 과외 목록 조회가 1건이다
    expect(getMyCoursesMock).toHaveBeenCalledTimes(1);
    expect(getMyCoursesMock).toHaveBeenCalledWith({ size: 50 });
  });

  it('authReady false → true 전환 시 getMyCourses가 호출된다', async () => {
    const { rerender } = render(
      <WorkspaceContextStrip
        route="home"
        setRoute={vi.fn()}
        appState={makeAppState(false)}
      />,
    );
    // 아직 호출 없음
    expect(getMyCoursesMock).not.toHaveBeenCalled();

    // authReady true로 전환
    await act(async () => {
      rerender(
        <WorkspaceContextStrip
          route="home"
          setRoute={vi.fn()}
          appState={makeAppState(true)}
        />,
      );
    });
    // 전환 후 1회 호출
    expect(getMyCoursesMock).toHaveBeenCalledTimes(1);
  });
});
