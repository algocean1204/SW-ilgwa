// 랜딩 히어로용 그라디언트 블롭 배경.
// 마우스 추적 없이 블롭 3개가 스스로 천천히 움직인다.
import React from 'react'
import { motion } from 'motion/react'
import type { Easing } from 'motion/react'
import type { Palette } from '@/lib'
import { useReducedMotion } from './useReducedMotion'

export interface AuroraBackgroundProps {
  palette: Palette
  active?: boolean
  children?: React.ReactNode
}

// motion.div의 animate/transition prop에 맞도록 ease를 Easing 타입으로 둔다
interface BlobConfig {
  style: React.CSSProperties
  animate: { x: number[]; y: number[] }
  transition: { duration: number; repeat: number; ease: Easing }
}

// 길이 3 튜플이라 구조분해한 값이 undefined가 되지 않는다
const BLOB_CONFIGS: [BlobConfig, BlobConfig, BlobConfig] = [
  {
    // 첫 번째 블롭: 좌상단 영역, palette.primary2 기반
    style: {
      width: '45%',
      height: '55%',
      top: '-10%',
      left: '-5%',
      borderRadius: '60% 40% 55% 45% / 50% 60% 40% 50%',
      filter: 'blur(50px)',
      pointerEvents: 'none',
    },
    // 22초 주기로 느리게 움직여 눈에 거슬리지 않게 한다
    animate: {
      x: [0, 30, -20, 15, 0],
      y: [0, -25, 20, -10, 0],
    },
    transition: { duration: 22, repeat: Infinity, ease: 'easeInOut' },
  },
  {
    // 두 번째 블롭: 우측 중앙, palette.soft 기반
    style: {
      width: '40%',
      height: '50%',
      top: '20%',
      right: '-8%',
      borderRadius: '45% 55% 40% 60% / 60% 40% 55% 45%',
      filter: 'blur(60px)',
      pointerEvents: 'none',
    },
    animate: {
      x: [0, -25, 18, -12, 0],
      y: [0, 20, -30, 15, 0],
    },
    transition: { duration: 26, repeat: Infinity, ease: 'easeInOut' },
  },
  {
    // 세 번째 블롭: 하단 중앙, --honey 기반 악센트
    style: {
      width: '38%',
      height: '45%',
      bottom: '-5%',
      left: '25%',
      borderRadius: '55% 45% 60% 40% / 45% 55% 40% 60%',
      filter: 'blur(45px)',
      pointerEvents: 'none',
    },
    animate: {
      x: [0, 20, -15, 25, 0],
      y: [0, 15, -20, 10, 0],
    },
    transition: { duration: 18, repeat: Infinity, ease: 'easeInOut' },
  },
]

export function AuroraBackground({ palette, active = true, children }: AuroraBackgroundProps) {
  const reduced = useReducedMotion()

  // active=false이면 블롭을 렌더하지 않는다
  const showBlobs = active

  const [blob0, blob1, blob2] = BLOB_CONFIGS

  return (
    <div style={{ position: 'relative', overflow: 'hidden' }}>
      {showBlobs && (
        <>
          {/* 첫 번째 블롭: palette.primary2, 낮은 opacity */}
          {reduced ? (
            <div
              style={{
                ...blob0.style,
                position: 'absolute',
                background: palette.primary2,
                opacity: 0.08,
              }}
            />
          ) : (
            <motion.div
              style={{
                ...blob0.style,
                position: 'absolute',
                background: palette.primary2,
                opacity: 0.08,
              }}
              animate={blob0.animate}
              transition={blob0.transition}
            />
          )}

          {/* 두 번째 블롭: palette.soft, 조금 더 높은 opacity */}
          {reduced ? (
            <div
              style={{
                ...blob1.style,
                position: 'absolute',
                background: palette.soft,
                opacity: 0.12,
              }}
            />
          ) : (
            <motion.div
              style={{
                ...blob1.style,
                position: 'absolute',
                background: palette.soft,
                opacity: 0.12,
              }}
              animate={blob1.animate}
              transition={blob1.transition}
            />
          )}

          {/* 세 번째 블롭: 하단 --honey 악센트 */}
          {reduced ? (
            <div
              style={{
                ...blob2.style,
                position: 'absolute',
                background: 'var(--honey)',
                opacity: 0.08,
              }}
            />
          ) : (
            <motion.div
              style={{
                ...blob2.style,
                position: 'absolute',
                background: 'var(--honey)',
                opacity: 0.08,
              }}
              animate={blob2.animate}
              transition={blob2.transition}
            />
          )}
        </>
      )}

      {/* 자식 요소가 블롭 위에 오도록 z-index 1을 준다 */}
      <div style={{ position: 'relative', zIndex: 1 }}>{children}</div>
    </div>
  )
}
