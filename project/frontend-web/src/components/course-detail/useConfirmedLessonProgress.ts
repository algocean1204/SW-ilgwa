// 확정 커리큘럼 뷰의 강의 생성 진행 폴링 훅.
//
// student-learning의 useLessonGenerationPolling과 하는 일은 같지만, 풀스크린 진행 화면이
// 아니라 확정 커리큘럼 위 배너용이라 가볍게 따로 뒀다.
//  - 5초 간격으로 getChapters와 첫 미완 챕터 generation-status를 조회한다
//  - percent가 줄어들지 않게 보정하고, 언마운트 시 폴링을 멈춘다
//  - status API가 실패하면 pollError를 세우고 경과 시간만 보여준다
import { useCallback, useEffect, useRef, useState } from 'react';
import { getChapters, getLessonGenerationStatus } from '@/apis';
import type { ChapterSummaryResponse } from '@/apis';
import { resolveLessonGenerationStage } from '@/components/student-learning/lessonGenerationStage';
import type { LessonGenerationStage } from '@/components/student-learning/lessonGenerationStage';
import {
  blendGenerationPercent,
  clampMonotonicPercent,
  countPendingChapters,
  countReadyChapters,
} from './lessonGenerationVisibility';

/** 폴링 간격 5초. 서버 부하를 생각해 더 줄이지 않는다 */
const POLL_INTERVAL_MS = 5_000;
/** 최대 대기. 15분이 지나면 폴링을 멈춘다(타임아웃) */
const POLL_TIMEOUT_MS = 15 * 60_000;

export interface ConfirmedLessonProgressState {
  /** 폴링이 한 번이라도 챕터 목록을 받았는지. 가시성 판단에 쓴다 */
  fetched: boolean;
  /** 최근 폴링으로 확인한 챕터 목록 */
  chapters: ChapterSummaryResponse[];
  /** 준비 완료(LOCKED 아님) 챕터 수 */
  readyCount: number;
  /** 미완(LOCKED) 챕터 수 */
  pendingCount: number;
  /** 단조 증가 보정된 0~100 진행률 */
  progressPct: number;
  /** 지금 생성 중인 챕터(첫 LOCKED). 라벨 표시용이며 없으면 null */
  generatingChapter: ChapterSummaryResponse | null;
  /** 생성 중 챕터의 노드 단위 단계(라벨/percent). 조회하지 못하면 null */
  currentStage: LessonGenerationStage | null;
  /** generation-status/chapters 조회 실패 여부. true면 경과 시간만 보여준다 */
  pollError: boolean;
  /** 타임아웃 도달 여부 */
  timedOut: boolean;
  /** 폴링 시작 후 경과 ms. status 실패 시 대신 보여준다 */
  elapsedMs: number;
}

interface UseConfirmedLessonProgressOptions {
  courseId: string;
  /** 폴링을 켤지. 확정(readOnly) 단계에서만 true */
  enabled: boolean;
}

export function useConfirmedLessonProgress({
  courseId,
  enabled,
}: UseConfirmedLessonProgressOptions): ConfirmedLessonProgressState {
  const [fetched, setFetched] = useState(false);
  const [chapters, setChapters] = useState<ChapterSummaryResponse[]>([]);
  const [currentStage, setCurrentStage] = useState<LessonGenerationStage | null>(null);
  const [pollError, setPollError] = useState(false);
  const [timedOut, setTimedOut] = useState(false);
  const [elapsedMs, setElapsedMs] = useState(0);
  // 폴링 사이 stage가 잠시 비어도 진행 바가 뒤로 가지 않게 이전 값을 기억한다
  const [progressPct, setProgressPct] = useState(0);

  // react-hooks/purity 규칙상 렌더 중 Date.now()를 못 불러 시작 effect에서 한 번 초기화한다
  const startedAtRef = useRef(0);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const tickRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const clearPolls = useCallback(() => {
    if (pollRef.current !== null) { clearInterval(pollRef.current); pollRef.current = null; }
    if (tickRef.current !== null) { clearInterval(tickRef.current); tickRef.current = null; }
  }, []);

  const pollStatus = useCallback(async () => {
    if (Date.now() - startedAtRef.current > POLL_TIMEOUT_MS) {
      clearPolls();
      setTimedOut(true);
      return;
    }
    try {
      const chaptersRes = await getChapters(courseId);
      const chapterList = chaptersRes.data ?? [];
      setChapters(chapterList);
      setFetched(true);
      setPollError(false);

      const ready = countReadyChapters(chapterList);
      const pending = countPendingChapters(chapterList);

      // 생성 중 챕터(첫 LOCKED)의 노드 단위 진행률을 합친다. 부가 기능이라 실패는 무시한다
      let stage: LessonGenerationStage | null = null;
      const generating = chapterList.find(c => c.status === 'LOCKED');
      if (generating) {
        try {
          const statusRes = await getLessonGenerationStatus(courseId, generating.id);
          stage = resolveLessonGenerationStage(statusRes.data ?? null);
        } catch {
          stage = null;
        }
      }
      setCurrentStage(stage);

      const rawPct = blendGenerationPercent(ready, chapterList.length, stage?.percent ?? null);
      // prev보다 낮은 값이 들어와도 뒤로 가지 않게 보정한다
      setProgressPct(prev => clampMonotonicPercent(prev, rawPct));

      // 모든 챕터가 준비되면 폴링을 멈춘다. 배너는 가시성 로직이 숨긴다
      if (chapterList.length > 0 && pending === 0) clearPolls();
    } catch {
      // chapters 조회가 실패하면 경과 시간만 보여준다
      setPollError(true);
      setFetched(true);
    }
  }, [courseId, clearPolls]);

  useEffect(() => {
    if (!enabled || !courseId) {
      clearPolls();
      return;
    }
    startedAtRef.current = Date.now();
    setTimedOut(false);
    // effect 본문에서 동기 setState가 일어나지 않게 첫 폴링도 타이머로 돌린다
    const firstPoll = setTimeout(() => { void pollStatus(); }, 0);
    pollRef.current = setInterval(() => { void pollStatus(); }, POLL_INTERVAL_MS);
    tickRef.current = setInterval(() => { setElapsedMs(Date.now() - startedAtRef.current); }, 1_000);
    return () => { clearTimeout(firstPoll); clearPolls(); };
  }, [enabled, courseId, pollStatus, clearPolls]);

  const generatingChapter = chapters.find(c => c.status === 'LOCKED') ?? null;

  return {
    fetched,
    chapters,
    readyCount: countReadyChapters(chapters),
    pendingCount: countPendingChapters(chapters),
    progressPct,
    generatingChapter,
    currentStage,
    pollError,
    timedOut,
    elapsedMs,
  };
}
