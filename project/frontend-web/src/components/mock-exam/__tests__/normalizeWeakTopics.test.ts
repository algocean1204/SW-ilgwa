// FastAPI 파싱 결과의 빈 문자열·공백·중복이 빈 칩으로 보이지 않는지 본다.
import { describe, it, expect } from 'vitest';
import { normalizeWeakTopics } from '../ExamResultPanel';

describe('normalizeWeakTopics', () => {
  it('null/undefined 입력은 빈 배열을 반환한다', () => {
    expect(normalizeWeakTopics(null)).toEqual([]);
    expect(normalizeWeakTopics(undefined)).toEqual([]);
  });

  it('빈 문자열과 공백-only 항목을 제거한다 (빈 칩 렌더 방지)', () => {
    expect(normalizeWeakTopics(['', '  ', '문법'])).toEqual(['문법']);
  });

  it('trim 후 같은 주제가 되는 중복을 제거한다', () => {
    expect(normalizeWeakTopics(['독해', ' 독해 ', '독해'])).toEqual(['독해']);
  });

  it('전부 빈 값이면 빈 배열을 반환해 섹션 자체가 숨겨진다', () => {
    expect(normalizeWeakTopics(['', '   '])).toEqual([]);
  });

  it('정상 입력은 순서를 유지한 채 그대로 반환한다', () => {
    expect(normalizeWeakTopics(['어휘', '문법', '독해'])).toEqual(['어휘', '문법', '독해']);
  });
});
