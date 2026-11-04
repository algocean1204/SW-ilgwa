// 호버하면 대각선 빛줄기가 왼쪽에서 오른쪽으로 지나가는 CTA 버튼
import React, { useState } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { DURATION, EASING } from '@/shared/motion/transitions'
import { useReducedMotion } from './useReducedMotion'

export interface ShimmerButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  children: React.ReactNode
  // 빛줄기 색상. rgba 값이라 CSS 변수 대신 직접 지정한다.
  shimmerColor?: string
  className?: string
}

export function ShimmerButton({
  children,
  shimmerColor = 'rgba(255,255,255,0.15)',
  className,
  style,
  ...rest
}: ShimmerButtonProps) {
  const reduced = useReducedMotion()
  const [hovered, setHovered] = useState(false)

  return (
    <button
      className={className}
      style={{
        position: 'relative',
        overflow: 'hidden',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        border: 'none',
        cursor: 'pointer',
        ...style,
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      {...rest}
    >
      {/* 빛줄기 오버레이. 호버할 때만 나타나고, 호버가 끝나면 바로 처음 위치로 돌아간다 */}
      {!reduced && (
        <AnimatePresence>
          {hovered && (
            <motion.div
              aria-hidden
              key="shimmer"
              initial={{ x: '-100%', opacity: 1 }}
              animate={{ x: '200%', opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{
                duration: DURATION.panel,
                ease: EASING.easeOut,
              }}
              style={{
                position: 'absolute',
                inset: 0,
                // skewX로 기울인 좁은 그라디언트 띠로 대각선 빛줄기를 만든다
                background: `linear-gradient(
                  105deg,
                  transparent 30%,
                  ${shimmerColor} 50%,
                  transparent 70%
                )`,
                pointerEvents: 'none',
                zIndex: 0,
              }}
            />
          )}
        </AnimatePresence>
      )}

      {/* 텍스트가 가려지지 않게 버튼 내용을 오버레이 위에 둔다 */}
      <span style={{ position: 'relative', zIndex: 1, display: 'inherit' }}>{children}</span>
    </button>
  )
}
