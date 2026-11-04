// 백엔드가 templateId=null을 보내도 TypeError 없이 렌더되는지 본다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ExamResultQuestionCard from '../ExamResultQuestionCard';
import type { QuestionResultItem } from '@/apis';
import type { Palette } from '@/lib';

// 픽스처

/** 디자인 토큰 없이 타입만 맞춘 더미 팔레트 */
const dummyPalette: Palette = {
  primary: '#3D5AFE',
  ink: '#1A237E',
  wash: '#E8EAF6',
  soft: '#9FA8DA',
  tag: '#C5CAE9',
} as unknown as Palette;

/**
 * 기본 QuestionResultItem 픽스처. 필요한 필드만 Partial로 덮어쓴다.
 */
function makeItem(
  overrides: Partial<QuestionResultItem> = {},
): QuestionResultItem {
  return {
    questionId: 'q-001',
    templateId: 'ko_multiple_choice_4',
    // reviewAnswer 등 필드명은 Spring QuestionGradeResponse를 따른다.
    displayOrder: 1,
    stem: '다음 중 옳은 것을 고르시오.',
    myAnswer: 'A',
    reviewAnswer: 'A',
    score: 2,
    maxScore: 4,
    correct: true,
    gradingMode: 'EXACT',
    feedback: '정답입니다.',
    rubricBreakdown: [],
    needsManualReview: false,
    ...overrides,
  };
}

// templateId가 null/undefined인 경우

describe('ExamResultQuestionCard — templateId null 방어', () => {
  it('templateId가 null이어도 TypeError 없이 렌더된다', () => {
    // 백엔드가 null을 보내는 상황을 재현한다.
    const item = makeItem({ templateId: null });

    expect(() => {
      render(<ExamResultQuestionCard item={item} index={0} palette={dummyPalette} />);
    }).not.toThrow();
  });

  it('templateId가 null인 경우 displayOrder 기반 문항 번호와 점수 표기가 정상 렌더된다', () => {
    // "틀린 문제만 보기"로 index가 어긋나도 원래 문항 번호가 보여야 해서
    // displayOrder가 index보다 먼저다.
    const item = makeItem({ templateId: null, displayOrder: 7, score: 3, maxScore: 5 });
    render(<ExamResultQuestionCard item={item} index={2} palette={dummyPalette} />);

    expect(screen.getByText('문항 7')).toBeTruthy();

    expect(screen.getByText('3 / 5점')).toBeTruthy();
  });

  it('displayOrder가 없으면(legacy 응답) index+1로 폴백해 문항 번호를 표시한다', () => {
    // 레거시 경로에서 displayOrder가 없으면 index+1을 쓴다.
    const item = makeItem({ templateId: null, displayOrder: undefined });
    render(<ExamResultQuestionCard item={item} index={2} palette={dummyPalette} />);

    expect(screen.getByText('문항 3')).toBeTruthy();
  });

  it('templateId가 null이면 루브릭 토글 버튼이 표시되지 않는다 (서술형이 아닌 것으로 처리)', () => {
    // templateId가 null이면 서술형인지 알 수 없어 rubricBreakdown이 있어도 토글을 숨긴다.
    const item = makeItem({
      templateId: null,
      rubricBreakdown: [{ criterion: '논리성', score: 2, maxScore: 3, reason: '논리적' }],
    });
    render(<ExamResultQuestionCard item={item} index={0} palette={dummyPalette} />);

    expect(screen.queryByText(/채점 기준/)).toBeNull();
  });

  it('templateId가 null이어도 피드백 텍스트가 정상 표시된다', () => {
    const item = makeItem({ templateId: null, feedback: '아쉽지만 오답입니다.' });
    render(<ExamResultQuestionCard item={item} index={0} palette={dummyPalette} />);

    expect(screen.getByText('아쉽지만 오답입니다.')).toBeTruthy();
  });

  it('templateId가 null이고 오답인 경우 오답 뱃지가 표시된다', () => {
    const item = makeItem({ templateId: null, correct: false });
    render(<ExamResultQuestionCard item={item} index={0} palette={dummyPalette} />);

    expect(screen.getByText('오답')).toBeTruthy();
  });

  it('templateId가 null이고 수동검토 중인 경우 "검토 중" 뱃지가 표시된다', () => {
    const item = makeItem({ templateId: null, correct: false, needsManualReview: true });
    render(<ExamResultQuestionCard item={item} index={0} palette={dummyPalette} />);

    expect(screen.getByText('검토 중')).toBeTruthy();
  });
});

// templateId가 정상인 경우

describe('ExamResultQuestionCard — 정상 templateId 렌더 회귀', () => {
  it('ko_descriptive templateId이면 루브릭 토글 버튼이 표시된다', () => {
    const item = makeItem({
      templateId: 'ko_descriptive',
      rubricBreakdown: [{ criterion: '논리성', score: 2, maxScore: 3, reason: '논리적' }],
    });
    render(<ExamResultQuestionCard item={item} index={0} palette={dummyPalette} />);

    expect(screen.getByText('▼ 채점 기준 보기')).toBeTruthy();
  });

  it('ko_multiple_choice_4 templateId이면 루브릭 토글 버튼이 없다', () => {
    const item = makeItem({ templateId: 'ko_multiple_choice_4' });
    render(<ExamResultQuestionCard item={item} index={0} palette={dummyPalette} />);

    expect(screen.queryByText(/채점 기준/)).toBeNull();
  });

  it('정답인 경우 "정답" 뱃지가 표시된다', () => {
    const item = makeItem({ templateId: 'ko_true_false', correct: true });
    render(<ExamResultQuestionCard item={item} index={0} palette={dummyPalette} />);

    expect(screen.getByText('정답')).toBeTruthy();
  });

  it('gradingMode가 있으면 gradingMode 뱃지가 표시된다', () => {
    const item = makeItem({ templateId: 'ko_short_answer', gradingMode: 'AI' });
    render(<ExamResultQuestionCard item={item} index={0} palette={dummyPalette} />);

    expect(screen.getByText('AI')).toBeTruthy();
  });
});
