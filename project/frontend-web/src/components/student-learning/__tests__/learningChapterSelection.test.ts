import { describe, expect, it } from 'vitest';
import type { ChapterSummaryResponse, ChapterStatus } from '@/apis';
import { resolveActiveChapterId } from '../learningChapterSelection';

function chapter(id: string, status: ChapterStatus): ChapterSummaryResponse {
  return {
    id,
    order: Number(id.replace(/\D/g, '')) || 1,
    title: id,
    description: '',
    estimatedMinutes: null,
    status,
    slideCount: 2,
    completedSlides: 0,
    completedAt: null,
  };
}

describe('resolveActiveChapterId', () => {
  it('명시적으로 선택한 챕터가 진행 중 챕터보다 우선한다', () => {
    expect(resolveActiveChapterId(
      [chapter('lesson-1', 'COMPLETED'), chapter('lesson-2', 'IN_PROGRESS')],
      'lesson-1',
      '',
    )).toBe('lesson-1');
  });

  it('selectedChapterId를 비운 뒤 재조회해도 이전 active 챕터를 보존한다', () => {
    expect(resolveActiveChapterId(
      [chapter('lesson-1', 'COMPLETED'), chapter('lesson-2', 'IN_PROGRESS')],
      '',
      'lesson-1',
    )).toBe('lesson-1');
  });

  it('이전 active가 없으면 진행 중인 챕터를 기본 선택한다', () => {
    expect(resolveActiveChapterId(
      [chapter('lesson-1', 'COMPLETED'), chapter('lesson-2', 'IN_PROGRESS')],
      '',
      '',
    )).toBe('lesson-2');
  });

  it('잠긴 챕터는 selected나 previous여도 선택하지 않는다', () => {
    expect(resolveActiveChapterId(
      [chapter('lesson-1', 'LOCKED'), chapter('lesson-2', 'AVAILABLE')],
      'lesson-1',
      'lesson-1',
    )).toBe('lesson-2');
  });
});
