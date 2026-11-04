// SVG 원형 진행률 링. 사이드바 코스 트리와 주간 학습 달력에서 쓴다.
import React, { useEffect } from 'react'
import { motion, useSpring, useTransform } from 'motion/react'
import { useReducedMotion } from './useReducedMotion'

export interface ProgressRingProps {
  // 0~100 사이의 진행률 값
  value: number
  size?: number
  strokeWidth?: number
  color?: string
  // 원형 트랙(배경 원) 색상
  trackColor?: string
  // true이면 중앙에 퍼센트 텍스트를 표시한다
  showLabel?: boolean
  className?: string
  style?: React.CSSProperties
}

export function ProgressRing({
  value,
  size = 40,
  strokeWidth = 4,
  color = 'var(--accent-primary)',
  trackColor = 'var(--border-soft)',
  showLabel = false,
  className,
  style,
}: ProgressRingProps) {
  const reduced = useReducedMotion()

  const radius = (size - strokeWidth) / 2
  const circumference = 2 * Math.PI * radius

  // 0~100 값을 strokeDashoffset으로 바꾼다. offset 0이면 꽉 찬 원, circumference면 빈 원이다.
  const targetOffset = circumference - (value / 100) * circumference

  const springOffset = useSpring(reduced ? targetOffset : circumference, {
    stiffness: 50,
    damping: 20,
    mass: 1,
  })

  // 레이블은 정수 motion value로 따로 둬 텍스트 갱신을 줄인다
  const labelValue = useTransform(springOffset, (v) =>
    String(Math.round(100 - (v / circumference) * 100)),
  )

  useEffect(() => {
    springOffset.set(targetOffset)
  }, [targetOffset, springOffset])

  // 모션 감소 모드에서는 스프링 없이 최종 상태를 즉시 렌더한다
  const staticOffset = targetOffset

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      className={className}
      style={style}
    >
      {/* 배경 트랙은 움직이지 않으므로 일반 circle을 쓴다 */}
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={trackColor}
        strokeWidth={strokeWidth}
      />

      {/* 진행률 원. -90도 회전해 12시 방향에서 시작한다 */}
      {reduced ? (
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={staticOffset}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      ) : (
        <motion.circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          style={{ strokeDashoffset: springOffset }}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      )}

      {showLabel && (
        <motion.text
          x={size / 2}
          y={size / 2}
          textAnchor="middle"
          dominantBaseline="central"
          style={{
            fontSize: size * 0.28,
            fill: 'var(--text-primary)',
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          {reduced ? `${Math.round(value)}` : labelValue}
        </motion.text>
      )}
    </svg>
  )
}
