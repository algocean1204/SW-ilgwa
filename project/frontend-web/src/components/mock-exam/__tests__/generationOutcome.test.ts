// 생성 결과 메타 판정. 코드값 표기가 달라지거나 빠져도 같은 결과를 내는지 본다.
import { describe, it, expect } from 'vitest';
import {
  resolveGenerationDisplay,
  partialNoticeText,
} from '../generationOutcome';

describe('resolveGenerationDisplay — 좁은 소스 결과 정규화', () => {
  // 메타가 없으면 항상 ok
  it('메타가 undefined면 ok (기존 동작 보존)', () => {
    expect(resolveGenerationDisplay(undefined).kind).toBe('ok');
  });

  it('메타가 null이면 ok', () => {
    expect(resolveGenerationDisplay(null).kind).toBe('ok');
  });

  it('모든 필드가 비어 있으면 ok', () => {
    const r = resolveGenerationDisplay({
      generationOutcome: null,
      requestedQuestionCount: null,
      actualQuestionCount: null,
      generationFailureReason: null,
    });
    expect(r.kind).toBe('ok');
  });

  it('passed 코드 + 동일 카운트면 ok', () => {
    const r = resolveGenerationDisplay({
      generationOutcome: 'passed',
      requestedQuestionCount: 20,
      actualQuestionCount: 20,
    });
    expect(r.kind).toBe('ok');
  });

  // partial
  it('passed_partial 코드(FastAPI 어휘)면 partial', () => {
    const r = resolveGenerationDisplay({
      generationOutcome: 'passed_partial',
      requestedQuestionCount: 20,
      actualQuestionCount: 12,
    });
    expect(r.kind).toBe('partial');
    expect(r.requested).toBe(20);
    expect(r.actual).toBe(12);
  });

  it('PARTIAL_SUCCESS 대문자 코드도 partial로 정규화', () => {
    const r = resolveGenerationDisplay({ generationOutcome: 'PARTIAL_SUCCESS' });
    expect(r.kind).toBe('partial');
  });

  it('코드 없이 actual < requested 면 카운트만으로 partial 판정', () => {
    const r = resolveGenerationDisplay({
      requestedQuestionCount: 20,
      actualQuestionCount: 8,
    });
    expect(r.kind).toBe('partial');
  });

  it('actual === requested 면 partial 아님(ok)', () => {
    const r = resolveGenerationDisplay({
      requestedQuestionCount: 20,
      actualQuestionCount: 20,
    });
    expect(r.kind).toBe('ok');
  });

  // needs_more_source
  it('failed_minimum_threshold 코드(FastAPI 어휘)면 needs_more_source', () => {
    const r = resolveGenerationDisplay({
      generationOutcome: 'failed_minimum_threshold',
      requestedQuestionCount: 20,
      actualQuestionCount: 2,
    });
    expect(r.kind).toBe('needs_more_source');
  });

  it('NEEDS_MORE_SOURCE 상위 표기 코드도 needs_more_source', () => {
    const r = resolveGenerationDisplay({ generationOutcome: 'NEEDS_MORE_SOURCE' });
    expect(r.kind).toBe('needs_more_source');
  });

  it('generationFailureReason에 needs_more_source가 와도 인식', () => {
    const r = resolveGenerationDisplay({
      generationOutcome: 'failed',
      generationFailureReason: 'needs_more_source',
    });
    expect(r.kind).toBe('needs_more_source');
  });

  it('needs_more_source가 partial 코드보다 우선한다', () => {
    // 신호가 엇갈리면 자료 부족 안내를 먼저 보인다.
    const r = resolveGenerationDisplay({
      generationOutcome: 'partial',
      generationFailureReason: 'insufficient_source',
    });
    expect(r.kind).toBe('needs_more_source');
  });
});

describe('partialNoticeText — partial 안내 문구', () => {
  it('requested/actual 둘 다 있으면 "요청 N문항 중 M문항 생성됨"', () => {
    const text = partialNoticeText({ kind: 'partial', requested: 20, actual: 12 });
    expect(text).toBe('요청 20문항 중 12문항 생성됨 (자료 범위 기준)');
  });

  it('actual만 있으면 actual 기준 문구', () => {
    const text = partialNoticeText({ kind: 'partial', requested: null, actual: 9 });
    expect(text).toContain('9문항');
  });

  it('카운트가 전혀 없으면 일반 문구로 폴백', () => {
    const text = partialNoticeText({ kind: 'partial', requested: null, actual: null });
    expect(text).toBe('자료 범위에 맞춰 일부 문항만 생성됨');
  });
});
