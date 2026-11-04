// AnimatePresence용 Variants. hidden/visible/exit 3단계라 mount와 unmount 모두 애니메이션된다.

import type { Variants } from 'motion/react';
import { TRANSITION_BASE, TRANSITION_EXIT } from './transitions';

// fadeIn: 위치 이동 없이 투명도만 바꾼다. 텍스트 오버레이, 배경 전환, 툴팁에 쓴다.
export const fadeIn: Variants = {
  hidden:  { opacity: 0 },
  visible: { opacity: 1, transition: TRANSITION_BASE },
  // 다음 콘텐츠를 가리지 않게 빠르게 사라진다
  exit:    { opacity: 0, transition: TRANSITION_EXIT },
};

// slideUp: 아래에서 위로 10px 슬라이드한다. 카드·패널·알림의 기본 등장이다.
export const slideUp: Variants = {
  hidden:  { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0, transition: TRANSITION_BASE },
  // 퇴장은 위쪽으로 빠르게
  exit:    { opacity: 0, y: -6, transition: TRANSITION_EXIT },
};

// slideDown: 위에서 아래로. 드롭다운, 헤더 알림, 탑다운 패널
export const slideDown: Variants = {
  hidden:  { opacity: 0, y: -10 },
  visible: { opacity: 1, y: 0, transition: TRANSITION_BASE },
  exit:    { opacity: 0, y: 6, transition: TRANSITION_EXIT },
};

// slideLeft: 오른쪽에서 왼쪽으로. 사이드 패널 열기, 다음 스텝
export const slideLeft: Variants = {
  hidden:  { opacity: 0, x: 20 },
  visible: { opacity: 1, x: 0, transition: TRANSITION_BASE },
  exit:    { opacity: 0, x: -12, transition: TRANSITION_EXIT },
};

// slideRight: 왼쪽에서 오른쪽으로. 뒤로가기, 이전 스텝
export const slideRight: Variants = {
  hidden:  { opacity: 0, x: -20 },
  visible: { opacity: 1, x: 0, transition: TRANSITION_BASE },
  exit:    { opacity: 0, x: 12, transition: TRANSITION_EXIT },
};

// scaleIn: 크기(0.985→1.0)와 투명도를 함께 바꾼다. 모달, 다이얼로그, 팝오버에 쓴다.
export const scaleIn: Variants = {
  hidden:  { opacity: 0, scale: 0.985 },
  visible: { opacity: 1, scale: 1,     transition: TRANSITION_BASE },
  exit:    { opacity: 0, scale: 0.992, transition: TRANSITION_EXIT },
};

// 목록 아이템을 순서대로 등장시킬 때 부모에 쓰는 variants. motion은 초 단위를 쓴다.
export const staggerContainer: Variants = {
  hidden:  { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      // 자식은 50ms 간격으로 등장한다
      staggerChildren: 0.05,
      // 컨테이너 등장 40ms 뒤 첫 자식이 시작한다
      delayChildren: 0.04,
    },
  },
  exit: { opacity: 0 },
};
