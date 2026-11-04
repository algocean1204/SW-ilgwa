// 자료가 좁을 때의 모의고사 생성 결과를 표시 상태로 바꾼다.
//   partial_success: 요청 20문항 중 N(<20)문항만 생성. 응시는 할 수 있다.
//   needs_more_source: 자료가 부족해 시험을 만들지 못함. 일반 FAILED와 안내가 다르다.
// 백엔드의 generationOutcome/requestedQuestionCount/actualQuestionCount/실패사유 코드는 모두
// 옵셔널이다. 값이 없으면 일반 생성 흐름 그대로 둔다.
// 백엔드 코드값 표기가 정해지지 않아 snake·대문자·축약형을 모두 받는다.
//   - passed_partial / PARTIAL_SUCCESS / partial → partial
//   - failed_minimum_threshold / NEEDS_MORE_SOURCE / needs_more_source → needs_more_source
import type { GenerationOutcomeMeta } from '@/apis';

/** 생성 결과 표시 종류 */
export type GenerationDisplayKind =
  | 'ok' // 요청 수만큼 생성. 안내 없음
  | 'partial' // 일부 문항만 생성. 배지를 달고 응시는 그대로
  | 'needs_more_source'; // 자료 부족. FAILED 대신 행동 제안 화면

export interface GenerationDisplayState {
  kind: GenerationDisplayKind;
  /** 요청 문항 수. 알 수 없으면 null */
  requested: number | null;
  /** 실제 생성 문항 수. 알 수 없으면 null */
  actual: number | null;
}

/** 코드 문자열을 소문자·언더스코어로 맞춘다. 'PARTIAL_SUCCESS' → 'partial_success' */
function normalizeCode(raw: string | null | undefined): string {
  return (raw ?? '').trim().toLowerCase();
}

/** outcome 코드가 partial 계열(passed_partial, partial_success, partial)인지 본다. */
function isPartialCode(code: string): boolean {
  return code.includes('partial');
}

/**
 * outcome·실패사유 코드가 자료 부족 계열인지 본다.
 * FastAPI: failed_minimum_threshold. 상위 표기: needs_more_source / insufficient_source 등.
 */
function isNeedsMoreSourceCode(code: string): boolean {
  return (
    code.includes('needs_more_source') ||
    code.includes('minimum_threshold') ||
    code.includes('insufficient_source') ||
    code.includes('not_enough_source')
  );
}

/**
 * 옵셔널 메타로 표시 상태를 정한다. 판정 순서:
 *   needs_more_source·최소 문항 미달 코드 → 'needs_more_source'
 *   partial 코드 → 'partial'
 *   코드 없이 actual < requested(둘 다 양수) → 'partial'
 *   그 외 → 'ok'
 * 메타가 하나도 없으면 항상 'ok'다.
 */
export function resolveGenerationDisplay(
  meta: GenerationOutcomeMeta | null | undefined,
): GenerationDisplayState {
  const requested =
    typeof meta?.requestedQuestionCount === 'number' && meta.requestedQuestionCount > 0
      ? meta.requestedQuestionCount
      : null;
  const actual =
    typeof meta?.actualQuestionCount === 'number' && meta.actualQuestionCount >= 0
      ? meta.actualQuestionCount
      : null;

  const code = normalizeCode(meta?.generationOutcome);
  const failureCode = normalizeCode(meta?.generationFailureReason);

  // outcome 코드와 실패사유 코드 중 어느 쪽에 와도 자료 부족으로 본다.
  if (isNeedsMoreSourceCode(code) || isNeedsMoreSourceCode(failureCode)) {
    return { kind: 'needs_more_source', requested, actual };
  }

  if (isPartialCode(code)) {
    return { kind: 'partial', requested, actual };
  }

  // 백엔드가 코드 없이 개수만 줄 때를 대비한다.
  if (requested !== null && actual !== null && actual < requested) {
    return { kind: 'partial', requested, actual };
  }

  return { kind: 'ok', requested, actual };
}

/**
 * partial 안내 문구. 예: "요청 20문항 중 12문항 생성됨 (자료 범위 기준)".
 * 개수가 일부만 있으면 있는 만큼만 보인다.
 */
export function partialNoticeText(state: GenerationDisplayState): string {
  const { requested, actual } = state;
  if (requested !== null && actual !== null) {
    return `요청 ${requested}문항 중 ${actual}문항 생성됨 (자료 범위 기준)`;
  }
  if (actual !== null) {
    return `자료 범위 기준 ${actual}문항으로 구성됨`;
  }
  return '자료 범위에 맞춰 일부 문항만 생성됨';
}
