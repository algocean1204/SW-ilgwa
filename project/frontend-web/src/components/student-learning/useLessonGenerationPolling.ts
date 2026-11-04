// 강의 생성 진행 폴링 훅.
// getCourse + getChapters를 주기적으로 조회해 챕터별 슬라이드 준비 상태를 돌려준다.
// 진행 신호는 챕터 단위(완료 챕터 수 + 완료 챕터 slideCount 합)에
// 생성 중인 챕터의 노드 단위 진행률(generation-status 프록시)을 더한 값이다.
// generation-status 조회가 실패하면 stage=null로 챕터 단위 표시로 돌아간다.
// 시작 시각은 localStorage에 남겨 다시 들어와도 경과·남은 시간이 이어진다.
import { useCallback, useEffect, useRef, useState } from 'react';
import { getCourse, getChapters, getLessonGenerationStatus } from '@/apis';
import type { ChapterSummaryResponse } from '@/apis';
import { resolveLessonGenerationStage } from './lessonGenerationStage';
import type { LessonGenerationStage } from './lessonGenerationStage';
import { getStartedAt, setStartedAtIfAbsent, clearStartedAt } from './lessonGenStartedAt';

/** 폴링 간격. 서버 부하를 줄이려고 5초마다 조회한다 */
const POLL_INTERVAL_MS = 5_000;
/** 최대 대기 시간. 15분이 넘으면 타임아웃 */
const POLL_TIMEOUT_MS = 15 * 60_000;

/** 챕터 목록에서 슬라이드가 준비된 챕터 수를 계산 */
export function countReadyChapters(chapters: ChapterSummaryResponse[]): number {
  return chapters.filter(c => c.status !== 'LOCKED').length;
}

/** 완료 챕터들의 slideCount 합. 실제 생성된 슬라이드 수의 챕터 단위 근사값 */
export function sumGeneratedSlides(chapters: ChapterSummaryResponse[]): number {
  return chapters
    .filter(c => c.status !== 'LOCKED')
    .reduce((sum, c) => sum + c.slideCount, 0);
}

interface UseLessonGenerationPollingOptions {
  courseId: string;
  /** 첫 챕터 슬라이드 준비 시 1회 호출 */
  onFirstChapterReady: () => void;
}

interface PollingState {
  chapters: ChapterSummaryResponse[];
  readyCount: number;
  /** 완료 챕터들의 slideCount 합. 슬라이드 단위 API가 생기면 바꿀 수 있는 근사값 */
  generatedSlides: number;
  /** 생성 중인 챕터의 노드 단위 진행 단계. 조회할 수 없으면 null(챕터 단위 표시) */
  currentStage: LessonGenerationStage | null;
  timedOut: boolean;
  pollError: boolean;
  elapsedMs: number;
}

export function useLessonGenerationPolling({
  courseId,
  onFirstChapterReady,
}: UseLessonGenerationPollingOptions): PollingState {
  const [chapters, setChapters] = useState<ChapterSummaryResponse[]>([]);
  const [readyCount, setReadyCount] = useState(0);
  const [generatedSlides, setGeneratedSlides] = useState(0);
  const [currentStage, setCurrentStage] = useState<LessonGenerationStage | null>(null);
  const [timedOut, setTimedOut] = useState(false);
  const [pollError, setPollError] = useState(false);
  // react-hooks/purity 때문에 렌더 중 Date.now()를 부르지 않고 폴링 시작 effect에서 한 번 정한다
  const startedAtRef = useRef(0);
  const [elapsedMs, setElapsedMs] = useState(0);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const tickRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const notifiedRef = useRef(false);

  const clearPolls = useCallback(() => {
    if (pollRef.current !== null) { clearInterval(pollRef.current); pollRef.current = null; }
    if (tickRef.current !== null) { clearInterval(tickRef.current); tickRef.current = null; }
  }, []);

  const pollStatus = useCallback(async () => {
    if (Date.now() - startedAtRef.current > POLL_TIMEOUT_MS) {
      clearPolls();
      // 타임아웃: 재시도 때 새 시작 시각이 잡히게 localStorage 키를 지운다
      clearStartedAt(courseId);
      setTimedOut(true);
      return;
    }
    try {
      const [courseRes, chaptersRes] = await Promise.all([
        getCourse(courseId),
        getChapters(courseId),
      ]);

      const courseOk = courseRes.data?.status === 'ACTIVE' || courseRes.data?.status === 'COMPLETED';
      if (!courseOk) { clearPolls(); return; }

      const chapterList = chaptersRes.data ?? [];
      const ready = countReadyChapters(chapterList);
      const slides = sumGeneratedSlides(chapterList);
      setChapters(chapterList);
      setReadyCount(ready);
      setGeneratedSlides(slides);
      setPollError(false);

      // 지금 생성 중인 챕터(첫 LOCKED)의 generation-status를 조회해 노드 단위 진행률을 더한다.
      // 부가 기능이라 조회 실패는 무시하고 stage=null(챕터 단위 표시)로 둔다.
      const generating = chapterList.find(c => c.status === 'LOCKED');
      if (generating) {
        try {
          const statusRes = await getLessonGenerationStatus(courseId, generating.id);
          setCurrentStage(resolveLessonGenerationStage(statusRes.data ?? null));
        } catch {
          setCurrentStage(null);
        }
      } else {
        setCurrentStage(null);
      }

      if (ready > 0 && !notifiedRef.current) {
        notifiedRef.current = true;
        onFirstChapterReady();
      }

      if (chapterList.length > 0 && ready >= chapterList.length) {
        // 생성 완료: 다음 생성 요청에서 새 시작 시각이 잡히게 localStorage 키를 지운다
        clearStartedAt(courseId);
        clearPolls();
      }
    } catch {
      setPollError(true);
    }
  }, [courseId, clearPolls, onFirstChapterReady]);

  useEffect(() => {
    // courseId가 바뀌거나 처음 들어오면 시작 시각을 정한다(effect deps에 courseId 포함).
    // 1) localStorage에 유효한 값이 있으면 재사용한다.
    // 2) 없으면 Date.now()로 새로 기록하고 localStorage에도 저장한다.
    // react-hooks/refs 때문에 ref 쓰기는 effect 안에서만 한다.
    const persisted = getStartedAt(courseId);
    const now = Date.now();
    if (persisted !== null) {
      // 재진입: 저장된 시작 시각을 그대로 쓴다
      startedAtRef.current = persisted;
    } else {
      // 첫 진입 또는 courseId 교체: 지금을 시작 시각으로 기록하고 저장한다
      startedAtRef.current = now;
      setStartedAtIfAbsent(courseId, now);
    }
    // 첫 폴링은 타이머 콜백으로 넘겨 effect 본문의 동기 setState를 피한다
    // (react-hooks/set-state-in-effect)
    const firstPoll = setTimeout(() => { void pollStatus(); }, 0);
    pollRef.current = setInterval(() => { void pollStatus(); }, POLL_INTERVAL_MS);
    tickRef.current = setInterval(() => { setElapsedMs(Date.now() - startedAtRef.current); }, 1_000);
    return () => { clearTimeout(firstPoll); clearPolls(); };
  }, [pollStatus, clearPolls, courseId]);

  return { chapters, readyCount, generatedSlides, currentStage, timedOut, pollError, elapsedMs };
}
