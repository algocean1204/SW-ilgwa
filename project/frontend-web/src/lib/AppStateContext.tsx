// 앱 전역 상태 컨텍스트와 훅
import { createContext, useContext } from 'react';
import type { AppState } from '@/shared/types';

// 초기값은 null이지만 Provider가 항상 값을 넣는다
export const AppStateCtx = createContext<AppState | null>(null);

// Provider 밖에서 호출하면 오류를 던진다
export function useAppState(): AppState {
  const ctx = useContext(AppStateCtx);
  if (!ctx) throw new Error('useAppState must be used within AppStateCtx.Provider');
  return ctx;
}
