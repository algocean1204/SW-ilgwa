// Tweaks 전역 컨텍스트와 훅. 기본값 TWEAKS_DEFAULTS도 여기 둔다.
import { createContext, useContext } from 'react';
import type { TweaksState } from '@/shared/types';

// 초기값은 null이지만 Provider가 항상 값을 넣는다
export const TweaksCtx = createContext<TweaksState | null>(null);

// Provider 밖에서 호출하면 오류를 던진다
export function useTweaks(): TweaksState {
  const ctx = useContext(TweaksCtx);
  if (!ctx) throw new Error('useTweaks must be used within TweaksCtx.Provider');
  return ctx;
}

// ez/index.html의 window.TWEAKS_DEFAULTS를 옮긴 상수
export const TWEAKS_DEFAULTS: TweaksState = {
  "view": "home",
  "palette": "grass",
  "motionIntensity": 70,
  "showAurora": true,
  "showSpotlight": true,
  "accent": "FF7D87",
};
