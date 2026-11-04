// 스프링 기반 숫자 카운트업. 뷰포트에 들어오면 from에서 to까지 올라간다.
import React, { useRef, useEffect } from 'react'
import { motion, useSpring, useTransform, useInView } from 'motion/react'
import { useReducedMotion } from './useReducedMotion'

export interface NumberCounterProps {
  to: number
  from?: number
  // 카운팅 시간(초). 실제 속도는 spring stiffness/damping이 정한다.
  duration?: number
  format?: (n: number) => string
  className?: string
  style?: React.CSSProperties
  once?: boolean
}

// 기본 포맷터. 로케일 천 단위 구분자를 붙인다.
const defaultFormat = (n: number) => Math.round(n).toLocaleString()

export function NumberCounter({
  to,
  from = 0,
  // duration은 기존 호출부 호환용으로만 받고, 속도는 spring 파라미터가 정한다
  duration: _duration = 1.2,
  format = defaultFormat,
  className,
  style,
  once = true,
}: NumberCounterProps) {
  const reduced = useReducedMotion()
  const containerRef = useRef<HTMLSpanElement>(null)
  const inView = useInView(containerRef, { once })

  // stiffness가 낮을수록 천천히, damping이 높을수록 진동 없이 멈춘다
  const springValue = useSpring(from, {
    stiffness: 50,
    damping: 20,
    mass: 1,
  })

  const displayValue = useTransform(springValue, (v) => format(v))

  useEffect(() => {
    if (reduced) return
    if (inView) {
      springValue.set(to)
    } else if (!once) {
      // once=false: 뷰포트 이탈 시 from으로 리셋(재진입 재생용)
      springValue.set(from)
    } else {
      // once=true인데 교차 콜백이 아직 안 왔어도 to로 보낸다.
      // IntersectionObserver가 늦거나 안 오는 환경에서 from(0)에 멈추지 않게 한다.
      springValue.set(to)
    }
  }, [inView, to, from, once, reduced, springValue])

  // 모션 감소 모드에서는 최종값을 즉시 표시한다
  if (reduced) {
    return (
      <span ref={containerRef} className={className} style={style}>
        {format(to)}
      </span>
    )
  }

  return (
    <span ref={containerRef} className={className} style={style}>
      <motion.span>{displayValue}</motion.span>
    </span>
  )
}
