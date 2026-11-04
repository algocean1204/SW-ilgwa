// 챕터·노드 진행률을 합치는 blendLessonProgressPct 단위 테스트.
// total=0, stage가 null일 때의 폴백, 0~100 클램프, 챕터 경계 단조성을 확인한다.
import { describe, it, expect } from 'vitest';
import { blendLessonProgressPct } from '../LessonGeneratingProgress';

describe('blendLessonProgressPct', () => {
  it('총 챕터 0개면 0을 반환한다 (0으로 나누기 방지)', () => {
    expect(blendLessonProgressPct(0, 0, null)).toBe(0);
    expect(blendLessonProgressPct(0, 0, 85)).toBe(0);
  });

  it('stage 미수신(null)이면 챕터 단위 진행률 그대로다', () => {
    expect(blendLessonProgressPct(2, 5, null)).toBe(40);
  });

  it('생성 중 챕터의 노드 진행률을 병합한다', () => {
    // (2 + 0.85) / 5 = 57%
    expect(blendLessonProgressPct(2, 5, 85)).toBe(57);
  });

  it('비정상 stagePercent는 0~100으로 클램프한다', () => {
    expect(blendLessonProgressPct(0, 5, 120)).toBe(20); // 1챕터 분량을 넘지 않는다
    expect(blendLessonProgressPct(0, 5, -10)).toBe(0);
  });

  it('전체 결과를 100으로 클램프한다', () => {
    expect(blendLessonProgressPct(5, 5, 95)).toBe(100);
  });

  it('챕터 완료 경계에서 역행하지 않는다 (N-1챕터+95% → N챕터+0%)', () => {
    const before = blendLessonProgressPct(2, 5, 95); // 59
    const after = blendLessonProgressPct(3, 5, 0); // 60
    expect(after).toBeGreaterThanOrEqual(before);
  });

  it('totalChapters가 NaN/Infinity여도 NaN%를 만들지 않고 0을 반환한다', () => {
    expect(blendLessonProgressPct(2, Number.NaN, 50)).toBe(0);
    expect(blendLessonProgressPct(2, Number.POSITIVE_INFINITY, 50)).toBe(0);
  });

  it('readyCount·stagePercent가 NaN이어도 유한한 진행률을 반환한다', () => {
    expect(blendLessonProgressPct(Number.NaN, 5, 50)).toBe(10); // ready 0 취급 + stage 50%
    expect(blendLessonProgressPct(2, 5, Number.NaN)).toBe(40); // stage 무시(챕터 단위 그대로)
  });
});
