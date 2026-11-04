// attemptId로 getAttemptStatus를 폴링해 GENERATING이 READY나 FAILED가 되기를 기다린다.
// READY면 onReady, FAILED면 onFailed를 부르고 폴링을 멈춰 스피너가 계속 돌지 않게 한다.
import { useCallback, useRef } from 'react';
import { getAttemptStatus } from '@/apis';
import type { AttemptStatus, AttemptStatusResponse } from '@/apis';
import { POLL_INTERVAL_MS } from './examOptions';

interface UseExamPollingArgs {
  /**
   * READY를 받으면 호출된다. 상위가 생성 메타(generationOutcome/requestedQuestionCount/
   * actualQuestionCount)를 보관할 수 있게 status 응답 전체를 넘긴다. 메타는 모두 옵셔널이다.
   */
  onReady: (attemptId: string, status?: AttemptStatusResponse) => void;
  /**
   * FAILED를 받으면 호출된다. 자료 부족(needs_more_source) 분기에 쓰도록 status 응답을 넘긴다.
   */
  onFailed?: (attemptId: string, status?: AttemptStatusResponse) => void;
}

interface UseExamPollingResult {
  pollUntilReady: (attemptId: string) => void;
  clearPoll: () => void;
  pollRef: React.MutableRefObject<ReturnType<typeof setInterval> | null>;
}

// 폴링을 멈추는 상태. GENERATING, CREATED, IN_PROGRESS 등은 다음 주기에 다시 묻는다.
const TERMINAL_STATUSES: ReadonlySet<AttemptStatus> = new Set<AttemptStatus>([
  'READY',
  'FAILED',
]);

export function useExamPolling({
  onReady,
  onFailed,
}: UseExamPollingArgs): UseExamPollingResult {
  // 언마운트나 단계 전환 때 바깥에서 clearPoll()로 정리한다.
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const clearPoll = useCallback(() => {
    if (pollRef.current !== null) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  const pollUntilReady = useCallback(
    (attemptId: string) => {
      clearPoll();
      pollRef.current = setInterval(() => {
        void getAttemptStatus(attemptId)
          .then((res) => {
            const statusData = res.data ?? undefined;
            const status = statusData?.status;
            if (!status || !TERMINAL_STATUSES.has(status)) {
              return;
            }
            // 콜백보다 먼저 폴링을 멈춘다.
            clearPoll();
            if (status === 'READY') {
              onReady(attemptId, statusData);
            } else if (status === 'FAILED') {
              onFailed?.(attemptId, statusData);
            }
          })
          .catch(() => {
            // 일시적 네트워크 오류는 폴링을 멈추지 않고 다음 주기에 다시 묻는다.
          });
      }, POLL_INTERVAL_MS);
    },
    [clearPoll, onReady, onFailed],
  );

  return { pollUntilReady, clearPoll, pollRef };
}
