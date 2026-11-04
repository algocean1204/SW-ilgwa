// prefers-reduced-motion 미디어 쿼리 상태를 반환하는 공통 훅
import { useReducedMotion as useMotionReducedMotion } from 'motion/react'

export function useReducedMotion(): boolean {
  return useMotionReducedMotion() ?? false
}
