// 생성 노드 → 한국어 단계 라벨 매핑 단위 테스트.
// currentNode는 완료된 노드라 다음 단계 라벨이 나오는지, null 폴백이 맞는지 확인한다.
import { describe, it, expect } from 'vitest';
import { resolveLessonGenerationStage } from '../lessonGenerationStage';
import type { LessonGenerationStatusResponse } from '@/apis';

function status(partial: Partial<LessonGenerationStatusResponse>): LessonGenerationStatusResponse {
  return {
    lessonId: 'lesson-1',
    status: 'running',
    currentNode: null,
    completedNodes: 0,
    totalNodes: 5,
    progressPercent: 0,
    errorMessage: null,
    ...partial,
  };
}

describe('resolveLessonGenerationStage', () => {
  it('null 응답이면 null로 폴백한다', () => {
    expect(resolveLessonGenerationStage(null)).toBeNull();
  });

  it('unknown/done/failed 상태는 null로 폴백한다', () => {
    expect(resolveLessonGenerationStage(status({ status: 'unknown' }))).toBeNull();
    expect(resolveLessonGenerationStage(status({ status: 'done', progressPercent: 100 }))).toBeNull();
    expect(resolveLessonGenerationStage(status({ status: 'failed' }))).toBeNull();
  });

  it('pending 상태는 생성 준비 중 0%를 반환한다', () => {
    expect(resolveLessonGenerationStage(status({ status: 'pending' })))
      .toEqual({ label: '생성 준비 중', percent: 0 });
  });

  it('running 시작 마커(generate_chapter_state)는 자료 분석 중이다', () => {
    expect(resolveLessonGenerationStage(status({ currentNode: 'generate_chapter_state' })))
      .toEqual({ label: '자료 분석 중', percent: 0 });
  });

  it('완료 노드별로 다음 진행 단계 라벨을 반환한다', () => {
    const cases: Array<[string, number, string]> = [
      ['prepare_context', 10, '슬라이드 생성 중'],
      ['generate_lesson', 55, '검증 중'],
      ['content_verify', 70, '음성 합성 중'],
      ['synthesize_audio', 85, '마무리 중'],
      ['postprocess_slides', 95, '마무리 중'],
    ];
    for (const [node, percent, label] of cases) {
      expect(resolveLessonGenerationStage(status({ currentNode: node, progressPercent: percent })))
        .toEqual({ label, percent });
    }
  });

  it('알 수 없는 노드명은 슬라이드 생성 중으로, 진행률은 0~100으로 클램프한다', () => {
    expect(resolveLessonGenerationStage(status({ currentNode: 'mystery_node', progressPercent: 120 })))
      .toEqual({ label: '슬라이드 생성 중', percent: 100 });
  });

  it('progressPercent가 NaN이면 0으로 폴백한다 (UI에 NaN% 누출 방지)', () => {
    expect(resolveLessonGenerationStage(status({ currentNode: 'prepare_context', progressPercent: Number.NaN })))
      .toEqual({ label: '슬라이드 생성 중', percent: 0 });
  });
});
