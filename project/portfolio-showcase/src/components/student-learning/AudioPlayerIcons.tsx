// 플레이어 전용 소형 아이콘

export function PauseIcon() {
  return (
    <svg width={11} height={11} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <rect x="5" y="4" width="4" height="16" rx="1" />
      <rect x="15" y="4" width="4" height="16" rx="1" />
    </svg>
  );
}

export function BackIcon() {
  return (
    <svg width={15} height={15} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M1 4v6h6" />
      <path d="M3.51 15a9 9 0 1 0 .49-3.69" />
    </svg>
  );
}

export function ForwardIcon() {
  return (
    <svg width={15} height={15} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M23 4v6h-6" />
      <path d="M20.49 15a9 9 0 1 1-.49-3.69" />
    </svg>
  );
}
