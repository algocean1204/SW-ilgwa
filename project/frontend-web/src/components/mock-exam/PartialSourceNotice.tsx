// "요청 20문항 중 N문항 생성됨 (자료 범위 기준)" 배지·배너.
// 응시에는 문제가 없어 경고색 대신 정보용 honey 톤을 쓴다.
import type { GenerationDisplayState } from './generationOutcome';
import { partialNoticeText } from './generationOutcome';

interface PartialSourceNoticeProps {
  state: GenerationDisplayState;
  /** 'badge'는 카드 안 작은 배지, 'banner'는 폭 넓은 안내 배너 */
  variant?: 'badge' | 'banner';
}

/**
 * partial이 아니면 null을 반환하므로 호출부는 조건 없이 마운트해도 된다.
 */
export default function PartialSourceNotice({ state, variant = 'banner' }: PartialSourceNoticeProps) {
  if (state.kind !== 'partial') return null;
  const text = partialNoticeText(state);

  if (variant === 'badge') {
    return (
      <span
        role="status"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          padding: '2px 8px',
          borderRadius: 6,
          fontSize: 10,
          fontWeight: 700,
          lineHeight: 1.4,
          background: 'var(--state-warning-bg)',
          color: 'var(--honey-ink)',
          border: '1px solid var(--honey-soft)',
        }}
      >
        {text}
      </span>
    );
  }

  return (
    <div
      role="status"
      aria-live="polite"
      style={{
        padding: '10px 14px',
        borderRadius: 10,
        background: 'var(--state-warning-bg)',
        border: '1px solid var(--honey-soft)',
        fontSize: 12,
        fontWeight: 700,
        color: 'var(--honey-ink)',
        lineHeight: 1.5,
      }}
    >
      {text} · 그대로 응시할 수 있어요.
    </div>
  );
}
