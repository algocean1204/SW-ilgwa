import { describe, expect, it } from 'vitest';
import { resolveResumeSlideIndex } from '../learningProgress';

describe('resolveResumeSlideIndex', () => {
  it('진행 중인 강의는 완료한 슬라이드 수를 다음 재개 인덱스로 사용한다', () => {
    expect(resolveResumeSlideIndex(0, 5)).toBe(0);
    expect(resolveResumeSlideIndex(2, 5)).toBe(2);
    expect(resolveResumeSlideIndex(4, 5)).toBe(4);
  });

  it('이미 완료한 강의는 다시 보기 의도로 보고 첫 슬라이드부터 연다', () => {
    expect(resolveResumeSlideIndex(5, 5)).toBe(0);
    expect(resolveResumeSlideIndex(8, 5)).toBe(0);
  });

  it('비정상 값은 안전한 범위로 보정한다', () => {
    expect(resolveResumeSlideIndex(-3, 5)).toBe(0);
    expect(resolveResumeSlideIndex(1, 0)).toBe(0);
  });
});
