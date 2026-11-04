// 확정 뷰 생성 배너의 가시성·진행률 순수 함수 테스트.
// readOnly 게이트, 종료/실패 차단, 미수신 구간, 미완 챕터 유무와
// 단조 증가, 진행률 병합, 경과 시간 포맷의 경계 동작을 확인한다.
import { describe, it, expect } from 'vitest';
import type { ChapterSummaryResponse, ChapterStatus } from '@/apis';
import {
  blendGenerationPercent,
  clampMonotonicPercent,
  countPendingChapters,
  countReadyChapters,
  formatElapsed,
  shouldShowGenerationBanner,
} from '../lessonGenerationVisibility';
import type { BannerVisibilityInput } from '../lessonGenerationVisibility';

function chapter(id: string, status: ChapterStatus): ChapterSummaryResponse {
  return {
    id,
    order: 1,
    title: `챕터 ${id}`,
    description: '',
    estimatedMinutes: 10,
    status,
    slideCount: 0,
    completedSlides: 0,
    completedAt: null,
  };
}

describe('countPendingChapters / countReadyChapters', () => {
  it('LOCKED만 미완으로 센다', () => {
    const list = [chapter('1', 'COMPLETED'), chapter('2', 'LOCKED'), chapter('3', 'AVAILABLE')];
    expect(countPendingChapters(list)).toBe(1);
    expect(countReadyChapters(list)).toBe(2);
  });

  it('비배열 입력은 0으로 방어한다', () => {
    // 폴링 실패 등으로 비정상 값이 들어와도 NaN/throw 없이 0
    expect(countPendingChapters(undefined as unknown as ChapterSummaryResponse[])).toBe(0);
    expect(countReadyChapters(null as unknown as ChapterSummaryResponse[])).toBe(0);
  });
});

describe('shouldShowGenerationBanner', () => {
  it('draft(readOnly=false) 단계에서는 항상 false다', () => {
    expect(shouldShowGenerationBanner({
      readOnly: false, courseStatus: 'ACTIVE', chapters: [chapter('1', 'LOCKED')], fetched: true,
    })).toBe(false);
  });

  it('확정 직후 아직 챕터 미수신(fetched=false)이면 true다 (진행 공백 방지)', () => {
    expect(shouldShowGenerationBanner({
      readOnly: true, courseStatus: 'ACTIVE', chapters: [], fetched: false,
    })).toBe(true);
  });

  it('courseStatus 미수신(undefined)이어도 미수신 구간은 막지 않는다', () => {
    expect(shouldShowGenerationBanner({
      readOnly: true, courseStatus: undefined, chapters: [], fetched: false,
    })).toBe(true);
  });

  it('미완(LOCKED) 챕터가 하나라도 있으면 true다', () => {
    expect(shouldShowGenerationBanner({
      readOnly: true, courseStatus: 'ACTIVE',
      chapters: [chapter('1', 'COMPLETED'), chapter('2', 'LOCKED')], fetched: true,
    })).toBe(true);
  });

  it('모든 챕터가 준비되면 false다 (배너 자연 소멸)', () => {
    expect(shouldShowGenerationBanner({
      readOnly: true, courseStatus: 'ACTIVE',
      chapters: [chapter('1', 'COMPLETED'), chapter('2', 'AVAILABLE')], fetched: true,
    })).toBe(false);
  });

  it('종료/실패 계열 상태는 미완 챕터가 있어도 false다', () => {
    const base: Omit<BannerVisibilityInput, 'courseStatus'> = {
      readOnly: true, chapters: [chapter('1', 'LOCKED')], fetched: true,
    };
    expect(shouldShowGenerationBanner({ ...base, courseStatus: 'COMPLETED' })).toBe(false);
    expect(shouldShowGenerationBanner({ ...base, courseStatus: 'CURRICULUM_FAILED' })).toBe(false);
    expect(shouldShowGenerationBanner({ ...base, courseStatus: 'DELETED' })).toBe(false);
    expect(shouldShowGenerationBanner({ ...base, courseStatus: 'PAUSED' })).toBe(false);
  });

  it('ACTIVE 상태 + 미완 챕터는 true다 (정상 생성 진행 흐름)', () => {
    expect(shouldShowGenerationBanner({
      readOnly: true, courseStatus: 'ACTIVE', chapters: [chapter('1', 'LOCKED')], fetched: true,
    })).toBe(true);
  });
});

describe('clampMonotonicPercent', () => {
  it('이전 값보다 낮으면 이전 값을 유지한다 (역행 방지)', () => {
    expect(clampMonotonicPercent(60, 40)).toBe(60);
  });

  it('이전 값보다 높으면 새 값을 채택한다', () => {
    expect(clampMonotonicPercent(40, 70)).toBe(70);
  });

  it('0~100으로 클램프한다', () => {
    expect(clampMonotonicPercent(0, 150)).toBe(100);
    expect(clampMonotonicPercent(-5, 10)).toBe(10);
  });

  it('NaN/Infinity 입력은 0으로 방어한다 (비유한값은 무시되고 유한한 쪽이 이긴다)', () => {
    expect(clampMonotonicPercent(Number.NaN, Number.NaN)).toBe(0);
    // next가 Infinity면 0으로 보므로 유한한 prev(50)가 유지된다
    expect(clampMonotonicPercent(50, Number.POSITIVE_INFINITY)).toBe(50);
    // prev가 Infinity면 0으로 보므로 유한한 next(30)가 채택된다
    expect(clampMonotonicPercent(Number.POSITIVE_INFINITY, 30)).toBe(30);
  });
});

describe('blendGenerationPercent', () => {
  it('총 챕터 0개면 0이다', () => {
    expect(blendGenerationPercent(0, 0, null)).toBe(0);
    expect(blendGenerationPercent(0, 0, 80)).toBe(0);
  });

  it('stage 미수신(null)이면 챕터 단위 진행률 그대로다', () => {
    expect(blendGenerationPercent(2, 5, null)).toBe(40);
  });

  it('생성 중 챕터의 노드 진행률을 병합한다', () => {
    // (2 + 0.85) / 5 = 57%
    expect(blendGenerationPercent(2, 5, 85)).toBe(57);
  });

  it('NaN/Infinity totalChapters는 0을 반환한다', () => {
    expect(blendGenerationPercent(2, Number.NaN, 50)).toBe(0);
    expect(blendGenerationPercent(2, Number.POSITIVE_INFINITY, 50)).toBe(0);
  });

  it('전체 결과를 100으로 클램프한다', () => {
    expect(blendGenerationPercent(5, 5, 95)).toBe(100);
  });
});

describe('formatElapsed', () => {
  it('1분 미만은 초만 표시한다', () => {
    expect(formatElapsed(5_000)).toBe('5초 경과');
  });

  it('1분 이상은 분·초를 표시한다', () => {
    expect(formatElapsed(125_000)).toBe('2분 5초 경과');
  });

  it('음수/NaN은 0초로 방어한다', () => {
    expect(formatElapsed(-1)).toBe('0초 경과');
    expect(formatElapsed(Number.NaN)).toBe('0초 경과');
  });
});
