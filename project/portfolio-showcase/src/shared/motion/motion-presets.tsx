// FadeIn / SlideIn / ScaleIn 래퍼. variants와 transitions를 묶어서 호출처는 motion 설정을 몰라도 된다.

import React from 'react';
import { motion } from 'motion/react';
import type { HTMLMotionProps } from 'motion/react';
import { fadeIn, slideUp, slideDown, slideLeft, slideRight, scaleIn } from './variants';
import { TRANSITION_BASE } from './transitions';

// 공통 prop
// delay: AnimatePresence 안에서 순차 등장이 필요할 때만 쓴다 (stagger 대신 명시적 지연)
// duration: 기본값(TRANSITION_BASE)을 바꿀 때만 넘긴다
interface BasePresetProps {
  children: React.ReactNode;
  delay?: number;      // 초 단위, 기본 0
  duration?: number;   // 초 단위, 기본 TRANSITION_BASE.duration
  className?: string;
}

// 호출처가 onClick, style 같은 div 속성을 그대로 넘길 수 있게 나머지 HTMLMotionProps를 받는다
type PresetProps = BasePresetProps & Omit<HTMLMotionProps<'div'>, keyof BasePresetProps>;

// FadeIn: 위치 이동 없이 투명도만 바꾼다. 텍스트 블록, 이미지, 배경 레이어에 쓴다.
export function FadeIn({ children, delay = 0, duration, className, ...rest }: PresetProps) {
  const transition = {
    ...TRANSITION_BASE,
    ...(duration !== undefined ? { duration } : {}),
    ...(delay > 0 ? { delay } : {}),
  };
  return (
    <motion.div
      className={className}
      variants={fadeIn}
      initial="hidden"
      animate="visible"
      exit="exit"
      transition={transition}
      {...rest}
    >
      {children}
    </motion.div>
  );
}

// SlideIn: 지정한 방향으로 슬라이드해 등장한다 (기본 up). 카드, 패널, 알림에 쓴다.
type SlideDirection = 'up' | 'down' | 'left' | 'right';

interface SlideInProps extends BasePresetProps {
  // 슬라이드 방향 (기본 up)
  direction?: SlideDirection;
}

type SlideInMotionProps = SlideInProps & Omit<HTMLMotionProps<'div'>, keyof SlideInProps>;

// direction → variants 매핑
const DIRECTION_VARIANTS = {
  up:    slideUp,
  down:  slideDown,
  left:  slideLeft,
  right: slideRight,
} as const;

export function SlideIn({
  children,
  direction = 'up',
  delay = 0,
  duration,
  className,
  ...rest
}: SlideInMotionProps) {
  const variants = DIRECTION_VARIANTS[direction];
  const transition = {
    ...TRANSITION_BASE,
    ...(duration !== undefined ? { duration } : {}),
    ...(delay > 0 ? { delay } : {}),
  };
  return (
    <motion.div
      className={className}
      variants={variants}
      initial="hidden"
      animate="visible"
      exit="exit"
      transition={transition}
      {...rest}
    >
      {children}
    </motion.div>
  );
}

// ScaleIn: 크기와 투명도를 함께 바꾼다 (scale 0.95→1.0). 모달, 다이얼로그, 팝오버에 쓴다.
export function ScaleIn({ children, delay = 0, duration, className, ...rest }: PresetProps) {
  const transition = {
    ...TRANSITION_BASE,
    ...(duration !== undefined ? { duration } : {}),
    ...(delay > 0 ? { delay } : {}),
  };
  return (
    <motion.div
      className={className}
      variants={scaleIn}
      initial="hidden"
      animate="visible"
      exit="exit"
      transition={transition}
      {...rest}
    >
      {children}
    </motion.div>
  );
}
