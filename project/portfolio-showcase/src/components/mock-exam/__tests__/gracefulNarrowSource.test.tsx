// 자료가 좁을 때의 안내 화면. PartialSourceNotice와 ExamGeneratingView(needs_more_source)를 렌더한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import PartialSourceNotice from '../PartialSourceNotice';
import ExamGeneratingView from '../ExamGeneratingView';
import type { GenerationDisplayState } from '../generationOutcome';
import type { Palette } from '@/lib';

// ExamResultPanel·ExamOverview의 partial 배지는 PartialSourceNotice를 그대로 쓴다.
// 두 패널은 IntersectionObserver에 기대는 motion 애니메이션이 있어 jsdom에서 띄우기 어렵다.
// 그래서 PartialSourceNotice만 테스트하고 연결은 tsc로 확인한다.

// 디자인 토큰 없이 타입만 맞춘 더미 팔레트
const dummyPalette: Palette = {
  primary: '#3D5AFE',
  primary2: '#536DFE',
  ink: '#1A237E',
  wash: '#E8EAF6',
  soft: '#9FA8DA',
  tag: '#C5CAE9',
} as unknown as Palette;

describe('PartialSourceNotice', () => {
  it('partial 상태면 "요청 N문항 중 M문항 생성됨" 문구를 렌더한다', () => {
    const state: GenerationDisplayState = { kind: 'partial', requested: 20, actual: 12 };
    render(<PartialSourceNotice state={state} variant="banner" />);
    expect(screen.getByText(/요청 20문항 중 12문항 생성됨/)).toBeTruthy();
  });

  it('ok 상태면 아무것도 렌더하지 않는다(회귀 방지)', () => {
    const state: GenerationDisplayState = { kind: 'ok', requested: null, actual: null };
    const { container } = render(<PartialSourceNotice state={state} />);
    expect(container.firstChild).toBeNull();
  });

  it('needs_more_source 상태면 배지/배너를 렌더하지 않는다(별도 화면 담당)', () => {
    const state: GenerationDisplayState = { kind: 'needs_more_source', requested: 20, actual: 2 };
    const { container } = render(<PartialSourceNotice state={state} />);
    expect(container.firstChild).toBeNull();
  });
});

describe('ExamGeneratingView — needs_more_source 분기', () => {
  it('needsMoreSource=true면 자료 부족 + 행동제안 UI를 표시한다(빨간 FAILED 아님)', () => {
    render(
      <ExamGeneratingView
        palette={dummyPalette}
        needsMoreSource
        onRetry={vi.fn()}
        onLater={vi.fn()}
      />,
    );
    expect(screen.getByText(/충분한 문항을 만들기 어려워요/)).toBeTruthy();
    // 행동 제안 3종: 문항 수 줄이기, 강의(챕터) 추가, 범위 확장
    expect(screen.getByText(/문항 수를 줄여서/)).toBeTruthy();
    expect(screen.getByText(/강의\(챕터\)를 더 듣고/)).toBeTruthy();
    expect(screen.getByText(/출제 범위를 더 넓게/)).toBeTruthy();
    expect(screen.queryByText(/AI 서버에서 생성 오류/)).toBeNull();
  });

  it('needsMoreSource=false, isFailed=true면 기존 일반 실패 UI를 표시한다(회귀 방지)', () => {
    render(
      <ExamGeneratingView
        palette={dummyPalette}
        isFailed
        onRetry={vi.fn()}
        onLater={vi.fn()}
      />,
    );
    expect(screen.getByText(/생성이 지연되고 있어요/)).toBeTruthy();
  });

  it('플래그 미지정이면 기존 진행 중 화면을 표시한다(회귀 방지)', () => {
    render(
      <ExamGeneratingView palette={dummyPalette} onRetry={vi.fn()} onLater={vi.fn()} />,
    );
    expect(screen.getByText(/문제를 출제하고 있어요/)).toBeTruthy();
  });
});
