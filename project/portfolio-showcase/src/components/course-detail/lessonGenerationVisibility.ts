// 확정 커리큘럼 뷰에서 강의 생성 진행 배너를 언제 띄울지 판단하는 순수 함수.
//
// 이 배너는 학습 페이지(student-learning)의 풀스크린 진행 화면과 달리
// 확정된 커리큘럼 위에 덧붙는 보조 배너다. 그래서 가시성 판단을 렌더·폴링과 떼어 테스트한다.
import type { ChapterSummaryResponse, ChapterStatus, CourseStatus } from '@/apis';

/** 아직 슬라이드가 생성되지 않은(잠긴) 챕터 상태 */
const PENDING_STATUS: ChapterStatus = 'LOCKED';

/**
 * 미완(LOCKED) 챕터 수. 하나라도 0보다 크면 생성이 진행 중이라는 의미다.
 * 비배열 입력(폴링 실패 등)은 0으로 본다.
 */
export function countPendingChapters(chapters: ChapterSummaryResponse[]): number {
  if (!Array.isArray(chapters)) return 0;
  return chapters.filter(c => c.status === PENDING_STATUS).length;
}

/**
 * 준비 완료된(LOCKED가 아닌) 챕터 수.
 * "N/M 완료" 표시의 N 값으로 쓴다.
 */
export function countReadyChapters(chapters: ChapterSummaryResponse[]): number {
  if (!Array.isArray(chapters)) return 0;
  return chapters.filter(c => c.status !== PENDING_STATUS).length;
}

export interface BannerVisibilityInput {
  /** 확정된(readOnly) 뷰인지. draft 단계에서는 띄우지 않는다 */
  readOnly: boolean;
  /** 코스 상태. 종료/실패 계열이면 띄우지 않는다 */
  courseStatus?: CourseStatus;
  /** 현재까지 확인된 챕터 목록(초기엔 비어 있을 수 있음) */
  chapters: ChapterSummaryResponse[];
  /** 폴링이 한 번이라도 성공해 챕터 목록을 받아봤는지 */
  fetched: boolean;
}

/**
 * 배너를 띄울지 여부.
 *
 * 판단 기준:
 *  1) readOnly(=confirmed phase)가 아니면 false.
 *     draft 검토 화면에는 생성 배너가 필요 없다.
 *  2) courseStatus가 종료/실패 계열(COMPLETED·CURRICULUM_FAILED·
 *     DELETED·PAUSED)이면 false. undefined(초기 미수신)는 막지 않는다.
 *  3) 폴링 결과가 아직 없으면(fetched=false) true.
 *     확정 직후 챕터 목록을 받기 전에도 "생성 중"을 바로 보여줘 진행 표시가 비지 않게 한다.
 *  4) 폴링 결과가 있으면 미완(LOCKED) 챕터가 하나라도 있을 때만 true.
 *     전부 준비되면 false가 되어 배너가 사라진다.
 */
export function shouldShowGenerationBanner(input: BannerVisibilityInput): boolean {
  if (!input.readOnly) return false;

  // 종료/실패 계열은 생성이 끝났거나 더 진행되지 않아 배너를 띄우지 않는다.
  if (
    input.courseStatus === 'COMPLETED'
    || input.courseStatus === 'CURRICULUM_FAILED'
    || input.courseStatus === 'DELETED'
    || input.courseStatus === 'PAUSED'
  ) {
    return false;
  }

  // 챕터를 아직 못 받았으면 확정 직후 공백이 없게 일단 띄운다.
  if (!input.fetched) return true;

  // 받아봤다면 미완 챕터가 있을 때만 띄운다.
  return countPendingChapters(input.chapters) > 0;
}

/**
 * 진행률이 뒤로 가지 않게 단조 증가로 보정한다.
 * 폴링 사이 generation-status가 잠시 비거나 done으로 떨어져 prev보다 낮은 값이
 * 들어와도, 이미 보여준 값보다 뒤로 가지 않게 한다. 0~100으로 클램프한다.
 */
export function clampMonotonicPercent(prev: number, next: number): number {
  const safePrev = Number.isFinite(prev) ? Math.min(100, Math.max(0, prev)) : 0;
  const safeNext = Number.isFinite(next) ? Math.min(100, Math.max(0, next)) : 0;
  return Math.max(safePrev, safeNext);
}

/**
 * 챕터 단위 + 노드 단위 진행률 병합(0~100 정수).
 * total=0이면 0, 생성 중 챕터의 stagePercent(null이면 챕터 단위 그대로).
 * student-learning의 blendLessonProgressPct와 같은 규칙이며 NaN/Infinity는 0으로 본다.
 */
export function blendGenerationPercent(
  readyCount: number,
  totalChapters: number,
  stagePercent: number | null,
): number {
  if (!Number.isFinite(totalChapters) || totalChapters <= 0) return 0;
  const safeReady = Number.isFinite(readyCount) ? Math.max(0, readyCount) : 0;
  const stageFraction = stagePercent !== null && Number.isFinite(stagePercent)
    ? Math.min(100, Math.max(0, stagePercent)) / 100
    : 0;
  return Math.min(100, Math.round(((safeReady + stageFraction) / totalChapters) * 100));
}

/** 경과 시간을 "X분 Y초 경과" 형식으로 바꾼다. status API 실패 시 대신 보여준다 */
export function formatElapsed(ms: number): string {
  const safeMs = Number.isFinite(ms) && ms > 0 ? ms : 0;
  const totalSec = Math.floor(safeMs / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  if (min === 0) return `${sec}초 경과`;
  return `${min}분 ${sec}초 경과`;
}
