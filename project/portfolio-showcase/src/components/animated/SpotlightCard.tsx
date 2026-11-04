// 카드 hover 피드백 래퍼. 방사형 빛 효과 없이 테두리와 배경만 바꾼다.
import React, { useState } from 'react'

export interface SpotlightCardProps {
  children: React.ReactNode
  className?: string
  style?: React.CSSProperties
  // radius는 기존 호출부 호환용으로만 받고 쓰지 않는다. opacity는 hover 배경 농도에 쓴다
  radius?: number
  opacity?: number
  // hover 상태에서 사용할 강조색
  color?: string
}

export function SpotlightCard({
  children,
  className,
  style,
  opacity = 0.06,
  color = 'var(--accent-primary)',
}: SpotlightCardProps) {
  const [hovered, setHovered] = useState(false)

  return (
    <div
      className={className}
      style={{
        position: 'relative',
        overflow: 'visible',
        transition: 'background .18s, border-color .18s, transform .18s',
        transform: hovered ? 'translateY(-1px)' : 'none',
        ...style,
        background: hovered ? `color-mix(in srgb, ${color} ${Math.round(opacity * 180)}%, var(--paper))` : style?.background,
        borderColor: hovered ? color : style?.borderColor,
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <div style={{ height: '100%' }}>{children}</div>
    </div>
  )
}
