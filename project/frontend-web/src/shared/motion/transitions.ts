// 공통 easing·duration 상수. 모든 motion 애니메이션이 이 값을 쓴다.

import type { Transition } from 'motion/react';

// duration 프리셋. 160ms 이하는 즉각적으로, 400ms 이상은 느리게 느껴진다.
// fast: 버튼 hover, 칩 클릭 같은 마이크로 인터랙션
// base: 패널·카드 등장/사라짐
// panel: 사이드 패널·서랍 등장
// slow: 페이지 전환, 큰 레이아웃 변화. 420ms를 상한으로 둔다.
export const DURATION = {
  fast:  0.16,  // 160ms
  base:  0.22,  // 220ms
  panel: 0.28,  // 280ms
  slow:  0.42,  // 420ms
} as const;

// easing 프리셋. easeOut은 빠르게 시작해 천천히 끝난다.
// 커스텀 cubic-bezier(.2,.7,.2,1)는 CSS의 .lift, .rise, flip-inner와 같은 곡선이다.
export const EASING = {
  // 기본 easing. 대부분의 등장/사라짐에 쓴다.
  easeOut: [0.22, 1, 0.36, 1] as [number, number, number, number],
  // 퇴장할 때는 빠르게 가속해 사라진다
  easeIn:  [0.32, 0, 0.67, 0] as [number, number, number, number],
  // 바운스 없는 스프링 곡선
  spring:  [0.16, 1, 0.3, 1]  as [number, number, number, number],
} as const;

// 미리 묶은 Transition 객체. animate prop에 바로 넘기고, 매 렌더 새 객체를 만들지 않는다.

/** 버튼 피드백, 칩 상태 변화 같은 빠른 전환 */
export const TRANSITION_FAST: Transition = {
  duration: DURATION.fast,
  ease: EASING.easeOut,
};

/** 기본 패널·카드 전환. 대부분의 UI 등장/사라짐에 쓴다. */
export const TRANSITION_BASE: Transition = {
  duration: DURATION.base,
  ease: EASING.easeOut,
};

/** 사이드 패널, 모달 서랍 전환 */
export const TRANSITION_PANEL: Transition = {
  duration: DURATION.panel,
  ease: EASING.easeOut,
};

/** 라우트 변경, 전체 레이아웃 이동 같은 느린 전환 */
export const TRANSITION_SLOW: Transition = {
  duration: DURATION.slow,
  ease: EASING.easeOut,
};

/** 퇴장 전환. 빠르게 가속해 사라진다. */
export const TRANSITION_EXIT: Transition = {
  duration: DURATION.fast,
  ease: EASING.easeIn,
};
