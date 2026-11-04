// 생성 오류 코드별 상태 변화. CON_004(미완강), CON_001(코인 부족), 그 밖의 오류.
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { AppState } from '@/shared/types';

// vi.mock 팩토리는 호이스팅돼 바깥 변수를 못 쓰므로 클래스를 안에서 정의한다.
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
    generateExam: vi.fn(),
    getAttemptDetail: vi.fn(),
    getAttemptResult: vi.fn(),
    getExamDetail: vi.fn(),
    getExamList: vi.fn().mockResolvedValue({ data: [], success: true, message: null, timestamp: '' }),
    getExamResult: vi.fn(),
    submitAttempt: vi.fn(),
    submitExam: vi.fn(),
    getCoinBalance: vi.fn().mockResolvedValue({
      data: { balance: 5 }, success: true, message: null, timestamp: '',
    }),
    // useMockExam이 instanceof로 확인하므로 모킹 모듈에서 ApiError를 export한다.
    ApiError: InlineApiError,
  };
});

// 픽스처
function makeAppState(coins = 10): AppState {
  return {
    coins,
    setCoins: vi.fn(),
    activeCourseId: '',
    setActiveCourseId: vi.fn(),
    hasCourses: true,
    setHasCourses: vi.fn(),
    userName: '',
    userEmail: '',
    isAuthenticated: false,
    setIsAuthenticated: vi.fn(),
    userId: null,
    setUserId: vi.fn(),
    subscriptionStatus: null,
    setSubscriptionStatus: vi.fn(),
  } as unknown as AppState;
}

describe('useMockExam — 에러 코드 분기 (Bug #1)', () => {
  let generateExamMock: ReturnType<typeof vi.fn>;
  let getExamDetailMock: ReturnType<typeof vi.fn>;
  let submitExamMock: ReturnType<typeof vi.fn>;
  const onNeedCoins = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    const apis = await import('@/apis');
    generateExamMock = apis.generateExam as ReturnType<typeof vi.fn>;
    getExamDetailMock = apis.getExamDetail as ReturnType<typeof vi.fn>;
    submitExamMock = apis.submitExam as ReturnType<typeof vi.fn>;
  });

  /**
   * instanceof가 통과하려면 vi.mock 안의 InlineApiError와 같은 클래스여야 한다.
   */
  async function makeApiError(status: number, code: string, message: string) {
    const apis = await import('@/apis');
    const Err = apis.ApiError as new (
      status: number,
      errorResponse: { code: string; message: string; traceId: null; details: null },
    ) => InstanceType<typeof apis.ApiError>;
    return new Err(status, { code, message, traceId: null, details: null });
  }

  function legacyDetail() {
    return {
      id: 'mex_legacy',
      title: '레거시 모의고사',
      examType: 'final',
      questionCount: 1,
      difficulty: 'medium',
      timeLimit: 60,
      status: 'READY',
      totalPoints: 5,
      createdAt: '2026-01-01T00:00:00Z',
      questions: [
        {
          id: 'mq_1',
          questionIdx: 1,
          questionText: '다음 중 정답은?',
          questionType: 'multiple_choice',
          options: '["오답","정답"]',
          points: 5,
        },
      ],
    };
  }

  function legacyResult() {
    return {
      examId: 'mex_legacy',
      title: '레거시 모의고사',
      score: 5,
      totalPoints: 5,
      grade: 'A',
      timeTaken: 33,
      submittedAt: '2026-01-01T00:01:00Z',
      analysis: '',
      questionResults: [
        {
          questionId: 'mq_1',
          questionIdx: 1,
          questionText: '다음 중 정답은?',
          options: '["오답","정답"]',
          selectedOption: '1',
          correctOption: '1',
          correct: true,
          explanation: '두 번째 보기가 정답입니다.',
          points: 5,
        },
      ],
    };
  }

  it('CON_004 응답 시 courseNotCompleted=true, error 메시지 표시, onNeedCoins 미호출', async () => {
    const error = await makeApiError(400, 'CON_004', '코스를 완강하지 않았습니다.');
    generateExamMock.mockRejectedValue(error);

    const { useMockExam } = await import('../useMockExam');
    const { result } = renderHook(() =>
      useMockExam({ courseId: 'course-1', appState: makeAppState(), onNeedCoins }),
    );

    expect(result.current.courseNotCompleted).toBe(false);
    expect(result.current.error).toBeNull();

    await act(async () => {
      await result.current.startGenerate({ examType: 'multiple_choice', questionCount: 10, difficulty: 'medium' });
    });

    // 미완강 플래그와 안내가 뜨고 가격 페이지로 가지 않는다.
    expect(result.current.courseNotCompleted).toBe(true);
    expect(result.current.error).toMatch(/강의를 먼저 완료해야/);
    expect(onNeedCoins).not.toHaveBeenCalled();
    expect(result.current.phase).toBe('overview');
  });

  it('CON_001 응답 시 onNeedCoins 호출, courseNotCompleted false, error null', async () => {
    const error = await makeApiError(400, 'CON_001', '코인 잔액이 부족합니다.');
    generateExamMock.mockRejectedValue(error);

    const { useMockExam } = await import('../useMockExam');
    const { result } = renderHook(() =>
      useMockExam({ courseId: 'course-1', appState: makeAppState(), onNeedCoins }),
    );

    await act(async () => {
      await result.current.startGenerate({ examType: 'multiple_choice', questionCount: 10, difficulty: 'medium' });
    });

    expect(onNeedCoins).toHaveBeenCalledTimes(1);
    expect(result.current.courseNotCompleted).toBe(false);
    expect(result.current.error).toBeNull();
    expect(result.current.phase).toBe('overview');
  });

  it('기타 400 에러 시 일반 에러 메시지, onNeedCoins 미호출', async () => {
    const error = await makeApiError(400, 'OTHER_CODE', '알 수 없는 오류');
    generateExamMock.mockRejectedValue(error);

    const { useMockExam } = await import('../useMockExam');
    const { result } = renderHook(() =>
      useMockExam({ courseId: 'course-1', appState: makeAppState(), onNeedCoins }),
    );

    await act(async () => {
      await result.current.startGenerate({ examType: 'multiple_choice', questionCount: 10, difficulty: 'medium' });
    });

    expect(onNeedCoins).not.toHaveBeenCalled();
    expect(result.current.courseNotCompleted).toBe(false);
    expect(result.current.error).toMatch(/모의고사 생성에 실패했어요/);
  });

  it('500 에러 시 AI 서버 오류 메시지', async () => {
    const error = await makeApiError(500, 'SERVER_ERROR', '서버 오류');
    generateExamMock.mockRejectedValue(error);

    const { useMockExam } = await import('../useMockExam');
    const { result } = renderHook(() =>
      useMockExam({ courseId: 'course-1', appState: makeAppState(), onNeedCoins }),
    );

    await act(async () => {
      await result.current.startGenerate({ examType: 'multiple_choice', questionCount: 10, difficulty: 'medium' });
    });

    expect(result.current.error).toMatch(/AI 서버에 일시적인/);
    expect(onNeedCoins).not.toHaveBeenCalled();
  });

  it('courseId 변경 시 courseNotCompleted 초기화', async () => {
    const error = await makeApiError(400, 'CON_004', '코스를 완강하지 않았습니다.');
    generateExamMock.mockRejectedValue(error);

    const { useMockExam } = await import('../useMockExam');
    const { result, rerender } = renderHook(
      ({ courseId }: { courseId: string }) =>
        useMockExam({ courseId, appState: makeAppState(), onNeedCoins }),
      { initialProps: { courseId: 'course-1' } },
    );

    await act(async () => {
      await result.current.startGenerate({ examType: 'multiple_choice', questionCount: 10, difficulty: 'medium' });
    });
    expect(result.current.courseNotCompleted).toBe(true);

    await act(async () => {
      rerender({ courseId: 'course-2' });
    });
    expect(result.current.courseNotCompleted).toBe(false);
  });

  it('reset() 호출 시 courseNotCompleted 초기화', async () => {
    const error = await makeApiError(400, 'CON_004', '코스를 완강하지 않았습니다.');
    generateExamMock.mockRejectedValue(error);

    const { useMockExam } = await import('../useMockExam');
    const { result } = renderHook(() =>
      useMockExam({ courseId: 'course-1', appState: makeAppState(), onNeedCoins }),
    );

    await act(async () => {
      await result.current.startGenerate({ examType: 'multiple_choice', questionCount: 10, difficulty: 'medium' });
    });
    expect(result.current.courseNotCompleted).toBe(true);

    act(() => { result.current.reset(); });
    expect(result.current.courseNotCompleted).toBe(false);
  });

  it('attemptId가 없는 레거시 시험은 mex_ id로 상세를 불러와 응시 화면으로 전환한다', async () => {
    getExamDetailMock.mockResolvedValue({
      data: legacyDetail(), success: true, message: null, timestamp: '',
    });

    const { useMockExam } = await import('../useMockExam');
    const { result } = renderHook(() =>
      useMockExam({ courseId: 'course-1', appState: makeAppState(), onNeedCoins }),
    );

    await act(async () => {
      await result.current.openAttempt('mex_legacy');
    });

    expect(getExamDetailMock).toHaveBeenCalledWith('course-1', 'mex_legacy');
    expect(result.current.phase).toBe('taking');
    expect(result.current.detail?.attemptId).toBe('mex_legacy');
    expect(result.current.detail?.questions[0]?.options[1]).toEqual({
      label: 'B',
      text: '정답',
    });
  });

  it('레거시 시험 제출은 A/B 라벨을 Spring 0-based selectedOption으로 변환한다', async () => {
    getExamDetailMock.mockResolvedValue({
      data: legacyDetail(), success: true, message: null, timestamp: '',
    });
    submitExamMock.mockResolvedValue({
      data: legacyResult(), success: true, message: null, timestamp: '',
    });

    const { useMockExam } = await import('../useMockExam');
    const { result } = renderHook(() =>
      useMockExam({ courseId: 'course-1', appState: makeAppState(), onNeedCoins }),
    );

    await act(async () => {
      await result.current.openAttempt('mex_legacy');
    });
    await act(async () => {
      await result.current.finishExam({
        mq_1: { kind: 'multiple_choice', label: 'B' },
      }, 33);
    });

    expect(submitExamMock).toHaveBeenCalledWith('course-1', 'mex_legacy', {
      answers: [{ questionId: 'mq_1', selectedOption: '1' }],
      timeTaken: 33,
    });
    expect(result.current.phase).toBe('result');
    expect(result.current.result?.totalScore).toBe(5);
    expect(result.current.result?.results[0]?.reviewAnswer).toBe('B. 정답');
  });
});
