import { describe, it, expect } from 'vitest';
import { formatDifficultyLabel, formatExamTypeLabel } from '../examOptions';

describe('examOptions — 표시 라벨 변환', () => {
  it('formatDifficultyLabel: 알려진 난이도는 한글로 변환한다', () => {
    expect(formatDifficultyLabel('easy')).toBe('쉬움');
    expect(formatDifficultyLabel('medium')).toBe('보통');
    expect(formatDifficultyLabel('hard')).toBe('어려움');
  });

  it('formatDifficultyLabel: 알 수 없는 값은 원문을 유지한다', () => {
    expect(formatDifficultyLabel('expert')).toBe('expert');
  });

  it('formatExamTypeLabel: 알려진 시험 유형은 한글로 변환한다', () => {
    expect(formatExamTypeLabel('multiple_choice')).toBe('객관식 모의고사');
    expect(formatExamTypeLabel('final')).toBe('최종 점검 모의고사');
  });

  it('formatExamTypeLabel: 알 수 없는 값은 원문을 유지한다', () => {
    expect(formatExamTypeLabel('quiz')).toBe('quiz');
  });
});