// 뷰포트에 들어오면 단어마다 차례로 페이드인·슬라이드업하는 텍스트
import React, { useRef } from 'react'
import { motion, useInView } from 'motion/react'
import { DURATION, EASING } from '@/shared/motion/transitions'
import { useReducedMotion } from './useReducedMotion'

export interface TextRevealProps {
  text: string
  className?: string
  style?: React.CSSProperties
  // 단어 사이 등장 간격(초). 길면 문장이 다 나오기까지 오래 걸린다.
  staggerDelay?: number
  // true이면 최초 1회만 애니메이션 실행, false이면 뷰포트 재진입 시 반복
  once?: boolean
}

export function TextReveal({
  text,
  className,
  style,
  staggerDelay = 0.08,
  once = true,
}: TextRevealProps) {
  const reduced = useReducedMotion()
  const containerRef = useRef<HTMLSpanElement>(null)

  const inView = useInView(containerRef, { once })

  const words = text.split(' ').filter(Boolean)

  // 모션 감소 모드에서는 단순 텍스트로 즉시 렌더한다
  if (reduced) {
    return (
      <span ref={containerRef} className={className} style={style}>
        {text}
      </span>
    )
  }

  return (
    <span ref={containerRef} className={className} style={style}>
      {words.map((word, i) => (
        <motion.span
          key={`${word}-${i}`}
          // inline-block이어야 translateY가 단어 단위로 동작한다
          style={{ display: 'inline-block', marginRight: '0.25em' }}
          initial={{ opacity: 0, y: 8 }}
          animate={inView ? { opacity: 1, y: 0 } : { opacity: 0, y: 8 }}
          transition={{
            duration: DURATION.base,
            ease: EASING.easeOut,
            delay: i * staggerDelay,
          }}
        >
          {word}
        </motion.span>
      ))}
    </span>
  )
}
