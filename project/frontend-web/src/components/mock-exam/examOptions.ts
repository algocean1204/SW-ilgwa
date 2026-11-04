// 모의고사 보기(options) 파싱과 생성 옵션 상수.
// MockExamQuestionResponse.options는 보기 텍스트의 JSON 배열 문자열이다.
// 예) '["배열","큐","스택"]' → ["배열","큐","스택"]

/**
 * options 문자열을 보기 배열로 파싱한다. 실패하면 빈 배열을 돌려주고 호출부가 "보기 없음"을 보인다.
 */
export function parseOptions(raw: string): string[] {
  if (!raw) return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    if (Array.isArray(parsed) && parsed.every((o) => typeof o === 'string')) {
      return parsed as string[];
    }
    return [];
  } catch {
    // 파싱 실패는 "보기 없음"으로 본다.
    return [];
  }
}

// 난이도 선택지. value는 백엔드 difficulty 값과 같다.
export const DIFFICULTY_OPTIONS: { value: string; label: string; desc: string }[] = [
  { value: 'easy', label: '쉬움', desc: '핵심 개념 위주' },
  { value: 'medium', label: '보통', desc: '실전 난이도' },
  { value: 'hard', label: '어려움', desc: '심화 · 응용' },
];

// 시험 유형 선택지. value는 백엔드 examType 값과 같다.
export const EXAM_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'multiple_choice', label: '객관식 모의고사' },
  { value: 'final', label: '최종 점검 모의고사' },
];

/** difficulty 값을 한글 라벨로 바꾼다. 모르는 값은 원문을 그대로 쓴다. */
export function formatDifficultyLabel(value: string): string {
  return DIFFICULTY_OPTIONS.find((d) => d.value === value)?.label ?? value;
}

/** examType 값을 한글 라벨로 바꾼다. 모르는 값은 원문을 그대로 쓴다. */
export function formatExamTypeLabel(value: string): string {
  return EXAM_TYPE_OPTIONS.find((e) => e.value === value)?.label ?? value;
}

// 기본 문항 수
export const DEFAULT_QUESTION_COUNT = 20;
export const QUESTION_COUNT_OPTIONS = [10, 20, 30] as const;
// 기본 제한 시간(분)
export const DEFAULT_TIME_LIMIT = 60;
// AI 생성은 수십 초에서 수 분이 걸려 5초마다 다시 조회한다.
export const POLL_INTERVAL_MS = 5000;
