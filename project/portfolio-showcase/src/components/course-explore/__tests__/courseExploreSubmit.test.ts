// 과외 생성 요청에 선택한 튜터 ID가 실리는지 확인한다.
// tutorId가 createTutoring 페이로드에 있어야 백엔드가 튜터를 배정한다.
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { AppState } from '@/shared/types';
import type { TeacherItem } from '../types';

// vi.mock 팩토리는 호이스팅되어 외부 변수를 못 쓰므로 클래스를 안에서 정의한다
vi.mock('@/apis', () => {
  class InlineApiError extends Error {
    readonly status: number;
    readonly errorResponse: { code: string; message: string; traceId: null; details: null } | null;
    constructor(
      status: number,
      errorResponse: { code: string; message: string; traceId: null; details: null } | null,
    ) {
      super(errorResponse?.message ?? `HTTP ${status}`);
      this.name = 'ApiError';
      this.status = status;
      this.errorResponse = errorResponse;
    }
  }
  return {
    createTutoring: vi.fn(),
    uploadTutoringSourcePdf: vi.fn(),
    ApiError: InlineApiError,
  };
});

// 테스트 픽스처
const CUSTOM_TUTOR_ID = 'tut_01KTY4V1X589S6JDX04Y4DK7WQ';

const TEACHERS: TeacherItem[] = [
  { id: 'tut_00000000000000PRESET_OWL01', n: '올빼미 교수', d: '기본 튜터', isDefault: true },
  { id: CUSTOM_TUTOR_ID, n: '스피커2 QA 144712', d: '나만의 튜터', isDefault: false },
];

function makeAppState(): AppState {
  return {
    setActiveCourseId: vi.fn(),
    setHasCourses: vi.fn(),
  } as unknown as AppState;
}

function makeParams(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    mode: 'topic' as const,
    topic: '파이썬 함수와 반복문',
    selectedCategory: null,
    pdfFile: null,
    duration: 7,
    depth: '보통',
    lessonCount: 1,
    teacher: CUSTOM_TUTOR_ID,
    teachers: TEACHERS,
    appState: makeAppState(),
    setRoute: vi.fn(),
    setSubmitError: vi.fn(),
    ...overrides,
  };
}

describe('submitCourseExplore — 튜터 ID 페이로드 (선택 튜터 미반영 버그 회귀 방지)', () => {
  let createTutoringMock: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    vi.clearAllMocks();
    const apis = await import('@/apis');
    createTutoringMock = apis.createTutoring as ReturnType<typeof vi.fn>;
    createTutoringMock.mockResolvedValue({
      data: { id: 'crs_TEST00000000000000000000000' },
      success: true,
      message: null,
      timestamp: '',
    });
  });

  it('선택한 커스텀 튜터 ID가 createTutoring 페이로드에 포함된다', async () => {
    const { submitCourseExplore } = await import('../courseExploreSubmit');
    const params = makeParams();

    await submitCourseExplore(params as never);

    expect(createTutoringMock).toHaveBeenCalledTimes(1);
    const payload = createTutoringMock.mock.calls[0]?.[0];
    expect(payload?.tutorId).toBe(CUSTOM_TUTOR_ID);
    expect(params.setSubmitError as ReturnType<typeof vi.fn>).not.toHaveBeenCalledWith(
      expect.stringContaining('튜터'),
    );
  });

  it('기본 프리셋 튜터를 선택하면 프리셋 튜터 ID가 페이로드에 포함된다', async () => {
    const { submitCourseExplore } = await import('../courseExploreSubmit');
    const params = makeParams({ teacher: 'tut_00000000000000PRESET_OWL01' });

    await submitCourseExplore(params as never);

    expect(createTutoringMock).toHaveBeenCalledTimes(1);
    expect(createTutoringMock.mock.calls[0]?.[0]?.tutorId).toBe('tut_00000000000000PRESET_OWL01');
  });

  it('튜터 목록에 없는 ID를 선택하면 에러 메시지를 표시하고 요청하지 않는다', async () => {
    const { submitCourseExplore } = await import('../courseExploreSubmit');
    const setSubmitError = vi.fn();
    const params = makeParams({ teacher: 'tut_UNKNOWN', setSubmitError });

    await submitCourseExplore(params as never);

    expect(createTutoringMock).not.toHaveBeenCalled();
    expect(setSubmitError).toHaveBeenCalledWith('실제 튜터 ID를 불러온 뒤 다시 시도해 주세요.');
  });

  it('생성 성공 시 activeCourseId 설정 후 과외 상세로 이동한다', async () => {
    const { submitCourseExplore } = await import('../courseExploreSubmit');
    const appState = makeAppState();
    const setRoute = vi.fn();
    const params = makeParams({ appState, setRoute });

    await submitCourseExplore(params as never);

    expect(appState.setActiveCourseId).toHaveBeenCalledWith('crs_TEST00000000000000000000000');
    expect(setRoute).toHaveBeenCalledWith('courseDetail');
  });
});
