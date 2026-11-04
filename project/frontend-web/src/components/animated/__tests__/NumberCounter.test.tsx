// IntersectionObserver 콜백이 오지 않아도 to 값까지 가는지 확인한다.
import { describe, it, expect, beforeAll, vi } from 'vitest'
import { render, screen, act, waitFor } from '@testing-library/react'
import { NumberCounter } from '../NumberCounter'

vi.mock('../useReducedMotion', () => ({
  useReducedMotion: () => false,
}))

/** observe()가 콜백을 부르지 않는 IntersectionObserver. 콜백이 늦거나 안 오는 환경을 흉내 낸다. */
class NeverFiringIntersectionObserver {
  constructor(_callback: IntersectionObserverCallback) {}
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('NumberCounter', () => {
  beforeAll(() => {
    globalThis.IntersectionObserver =
      NeverFiringIntersectionObserver as unknown as typeof IntersectionObserver
  })

  it('IntersectionObserver 콜백이 안 와도 to 값으로 수렴한다', async () => {
    render(<NumberCounter to={6} />)
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    await waitFor(() => {
      expect(screen.getByText('6')).toBeTruthy()
    })
  })

  it('커스텀 format으로도 IntersectionObserver 미발화 시 to에 수렴한다', async () => {
    render(<NumberCounter to={6} format={(n) => String(Math.round(n))} />)
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    await waitFor(() => {
      expect(screen.getByText('6')).toBeTruthy()
    })
  })
})