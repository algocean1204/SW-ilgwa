// 천천히 밝아졌다 어두워지는 글로우 테두리. "이어서 학습" 카드에 쓴다.
import React from 'react'
import { motion } from 'motion/react'
import { useReducedMotion } from './useReducedMotion'

export interface GlowBorderProps {
  children: React.ReactNode
  color?: string
  className?: string
  style?: React.CSSProperties
  // 글로우 강도(0~1). 클수록 선명하다.
  intensity?: number
  // 밝아졌다 어두워지는 한 주기(초)
  duration?: number
}

export function GlowBorder({
  children,
  color = 'var(--accent-primary)',
  className,
  style,
  intensity = 0.3,
  duration = 3,
}: GlowBorderProps) {
  const reduced = useReducedMotion()

  // 가장 밝을 때와 꺼졌을 때의 그림자. color에 CSS 변수를 그대로 넣을 수 있다.
  const peakShadow = `0 0 20px -5px ${color}`
  const troughShadow = `0 0 0px 0px transparent`

  // 모션 감소 모드에서는 움직이지 않는 약한 글로우만 보여준다
  const staticShadow = `0 0 12px -6px ${color}`

  if (reduced) {
    return (
      <div className={className} style={{ position: 'relative', borderRadius: 'inherit', ...style }}>
        <div style={{ position: 'absolute', inset: 0, borderRadius: 'inherit', boxShadow: staticShadow, opacity: intensity, pointerEvents: 'none' }} />
        <div style={{ position: 'relative', borderRadius: 'inherit', overflow: 'hidden' }}>{children}</div>
      </div>
    )
  }

  return (
    <div className={className} style={{ position: 'relative', borderRadius: 'inherit', ...style }}>
      {/* 글로우 전용 레이어. opacity 애니메이션이 콘텐츠에 번지지 않게 분리한다 */}
      <motion.div
        style={{ position: 'absolute', inset: 0, borderRadius: 'inherit', pointerEvents: 'none' }}
        animate={{
          boxShadow: [troughShadow, peakShadow, troughShadow],
          opacity: [intensity * 0.4, intensity, intensity * 0.4],
        }}
        transition={{ duration, repeat: Infinity, ease: 'easeInOut' }}
      />
      <div style={{ position: 'relative', borderRadius: 'inherit', overflow: 'hidden' }}>{children}</div>
    </div>
  )
}
