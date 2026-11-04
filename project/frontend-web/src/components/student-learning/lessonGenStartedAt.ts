// 강의 슬라이드 생성 시작 시각(epoch ms)을 courseId별로 localStorage에 보관한다.
// 다시 들어와도 경과 시간이 0으로 돌아가지 않게 한다.
// localStorage 접근이 실패하면 console.warn 후 인메모리 값을 쓴다.

/** courseId별 localStorage 키 */
function buildKey(courseId: string): string {
  return `lessonGenStartedAt:${courseId}`;
}

/** 24시간보다 오래된 시작 시각은 이전 세션 잔재로 본다 */
const MAX_VALID_ELAPSED_MS = 24 * 60 * 60 * 1000;

/**
 * 저장된 시작 시각이 유효한지 확인한다.
 * - 미래 시각, 24시간보다 오래된 과거, 숫자가 아닌 값이면 false.
 */
function isValidStartedAt(value: unknown, now: number): value is number {
  if (typeof value !== 'number' || !Number.isFinite(value)) return false;
  // 미래 시각은 거른다(클라이언트 시계 오차 5초 허용)
  if (value > now + 5_000) return false;
  // 24시간이 지난 값도 거른다
  if (now - value > MAX_VALID_ELAPSED_MS) return false;
  return true;
}

/**
 * 저장된 시작 시각을 읽는다.
 * - 없거나 비정상이면 null을 돌려준다. 호출 측이 Date.now()로 새로 정한다.
 * - localStorage 접근이 실패하면 console.warn 후 null을 돌려주고 인메모리 값으로 이어간다.
 */
export function getStartedAt(courseId: string): number | null {
  const key = buildKey(courseId);
  const now = Date.now();
  try {
    const raw = localStorage.getItem(key);
    if (raw === null) return null;
    const parsed: unknown = JSON.parse(raw);
    if (!isValidStartedAt(parsed, now)) {
      // 비정상 값은 즉시 제거하고 새로 시작하게 한다
      localStorage.removeItem(key);
      return null;
    }
    return parsed;
  } catch (err) {
    console.warn('[lessonGenStartedAt] localStorage 읽기 실패 — 인메모리 폴백으로 동작', err);
    return null;
  }
}

/**
 * 시작 시각을 저장한다.
 * - 이미 유효한 값이 있으면 덮어쓰지 않아 재진입 때 기존 시각을 유지한다.
 * - localStorage 접근이 실패하면 console.warn만 하고 인메모리 값으로 진행한다.
 */
export function setStartedAtIfAbsent(courseId: string, startedAt: number): void {
  const key = buildKey(courseId);
  const now = Date.now();
  try {
    const raw = localStorage.getItem(key);
    if (raw !== null) {
      const parsed: unknown = JSON.parse(raw);
      if (isValidStartedAt(parsed, now)) return;
    }
    localStorage.setItem(key, JSON.stringify(startedAt));
  } catch (err) {
    console.warn('[lessonGenStartedAt] localStorage 쓰기 실패 — 인메모리 폴백으로 동작', err);
  }
}

/**
 * 저장된 시작 시각을 삭제한다.
 * - 생성 완료·실패·타임아웃 때 불러 다음 생성 요청에서 새 시작 시각을 잡게 한다.
 * - localStorage 접근이 실패하면 console.warn만 하고 진행한다.
 */
export function clearStartedAt(courseId: string): void {
  const key = buildKey(courseId);
  try {
    localStorage.removeItem(key);
  } catch (err) {
    console.warn('[lessonGenStartedAt] localStorage 삭제 실패', err);
  }
}
