// 폴링이 /exam-forge/attempts/{attemptId}/status만 쓰고 레거시 /tutoring/.../mock-exams/{examId}는
// 쓰지 않는지, FAILED를 받으면 바로 멈추고 onFailed를 부르는지 본다.
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useExamPolling } from '../useExamPolling';

// getAttemptStatus 모킹
vi.mock('@/apis', () => ({
  getAttemptStatus: vi.fn(),
}));

describe('useExamPolling — 신규 계약 폴링 검증 (Bug #3)', () => {
  let getAttemptStatusMock: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    const apis = await import('@/apis');
    getAttemptStatusMock = apis.getAttemptStatus as ReturnType<typeof vi.fn>;
    getAttemptStatusMock.mockResolvedValue({
      data: { attemptId: 'attempt-1', status: 'GENERATING' },
      success: true, message: null, timestamp: '',
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('pollUntilReady 호출 시 getAttemptStatus(attemptId)를 호출한다', async () => {
    const onReady = vi.fn();
    const { result } = renderHook(() => useExamPolling({ onReady }));

    act(() => {
      result.current.pollUntilReady('attempt-1');
    });

    await act(async () => {
      vi.advanceTimersByTime(5001);
    });

    expect(getAttemptStatusMock).toHaveBeenCalledWith('attempt-1');
    result.current.clearPoll();
  });

  it('READY 상태 수신 시 onReady(attemptId, status) 콜백이 호출된다', async () => {
    const statusData = { attemptId: 'attempt-2', status: 'READY' };
    getAttemptStatusMock.mockResolvedValue({
      data: statusData,
      success: true, message: null, timestamp: '',
    });
    const onReady = vi.fn();
    const { result } = renderHook(() => useExamPolling({ onReady }));

    act(() => {
      result.current.pollUntilReady('attempt-2');
    });

    await act(async () => {
      vi.advanceTimersByTime(5001);
    });

    // attemptId와 함께 생성 메타가 담긴 status 응답을 넘긴다.
    expect(onReady).toHaveBeenCalledWith('attempt-2', statusData);
  });

  it('GENERATING 상태에서는 onReady가 호출되지 않는다', async () => {
    getAttemptStatusMock.mockResolvedValue({
      data: { attemptId: 'attempt-3', status: 'GENERATING' },
      success: true, message: null, timestamp: '',
    });
    const onReady = vi.fn();
    const { result } = renderHook(() => useExamPolling({ onReady }));

    act(() => {
      result.current.pollUntilReady('attempt-3');
    });

    await act(async () => {
      vi.advanceTimersByTime(5001);
    });

    expect(onReady).not.toHaveBeenCalled();
    result.current.clearPoll();
  });

  it('clearPoll 호출 시 폴링이 중단된다', async () => {
    const onReady = vi.fn();
    const { result } = renderHook(() => useExamPolling({ onReady }));

    act(() => {
      result.current.pollUntilReady('attempt-4');
    });
    act(() => {
      result.current.clearPoll();
    });

    await act(async () => {
      vi.advanceTimersByTime(15000);
    });

    expect(getAttemptStatusMock).not.toHaveBeenCalled();
    expect(onReady).not.toHaveBeenCalled();
  });

  // FAILED 처리
  it('FAILED 상태 수신 시 onFailed(attemptId, status) 콜백이 호출된다', async () => {
    const statusData = { attemptId: 'attempt-5', status: 'FAILED' };
    getAttemptStatusMock.mockResolvedValue({
      data: statusData,
      success: true, message: null, timestamp: '',
    });
    const onReady = vi.fn();
    const onFailed = vi.fn();
    const { result } = renderHook(() => useExamPolling({ onReady, onFailed }));

    act(() => {
      result.current.pollUntilReady('attempt-5');
    });

    await act(async () => {
      vi.advanceTimersByTime(5001);
    });

    expect(onFailed).toHaveBeenCalledWith('attempt-5', statusData);
    expect(onReady).not.toHaveBeenCalled();
  });

  it('FAILED 수신 후 폴링이 즉시 중단된다 (추가 인터벌에서 API 재호출 없음)', async () => {
    getAttemptStatusMock.mockResolvedValue({
      data: { attemptId: 'attempt-6', status: 'FAILED' },
      success: true, message: null, timestamp: '',
    });
    const onReady = vi.fn();
    const onFailed = vi.fn();
    const { result } = renderHook(() => useExamPolling({ onReady, onFailed }));

    act(() => {
      result.current.pollUntilReady('attempt-6');
    });

    await act(async () => {
      vi.advanceTimersByTime(5001);
    });

    const callCountAfterFailed = getAttemptStatusMock.mock.calls.length;

    await act(async () => {
      vi.advanceTimersByTime(5001);
    });

    expect(getAttemptStatusMock.mock.calls.length).toBe(callCountAfterFailed);
  });

  it('onFailed 미등록 시 FAILED 수신 후 onReady도 호출되지 않는다', async () => {
    getAttemptStatusMock.mockResolvedValue({
      data: { attemptId: 'attempt-7', status: 'FAILED' },
      success: true, message: null, timestamp: '',
    });
    const onReady = vi.fn();
    const { result } = renderHook(() => useExamPolling({ onReady }));

    act(() => {
      result.current.pollUntilReady('attempt-7');
    });

    await act(async () => {
      vi.advanceTimersByTime(5001);
    });

    expect(onReady).not.toHaveBeenCalled();
  });

  // 생성 메타 전달
  it('READY 시 onReady에 status 응답(좁은 소스 메타 포함)을 함께 전달한다', async () => {
    const statusData = {
      attemptId: 'attempt-8',
      status: 'READY',
      generationOutcome: 'passed_partial',
      requestedQuestionCount: 20,
      actualQuestionCount: 12,
    };
    getAttemptStatusMock.mockResolvedValue({
      data: statusData, success: true, message: null, timestamp: '',
    });
    const onReady = vi.fn();
    const { result } = renderHook(() => useExamPolling({ onReady }));

    act(() => { result.current.pollUntilReady('attempt-8'); });
    await act(async () => { vi.advanceTimersByTime(5001); });

    expect(onReady).toHaveBeenCalledWith('attempt-8', statusData);
  });

  it('FAILED 시 onFailed에 status 응답(실패사유 코드 포함)을 함께 전달한다', async () => {
    const statusData = {
      attemptId: 'attempt-9',
      status: 'FAILED',
      generationFailureReason: 'needs_more_source',
    };
    getAttemptStatusMock.mockResolvedValue({
      data: statusData, success: true, message: null, timestamp: '',
    });
    const onReady = vi.fn();
    const onFailed = vi.fn();
    const { result } = renderHook(() => useExamPolling({ onReady, onFailed }));

    act(() => { result.current.pollUntilReady('attempt-9'); });
    await act(async () => { vi.advanceTimersByTime(5001); });

    expect(onFailed).toHaveBeenCalledWith('attempt-9', statusData);
  });
});
