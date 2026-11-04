// 연결하기 짝 번호 배지. 색약 사용자를 위해 색만으로 구분하지 않고 번호를 함께 쓴다.
import type { Palette } from '@/lib';

interface MatchPairBadgeProps {
  pairIndex: number; // 0-based 짝 번호
  palette: Palette;
}

// 짝 번호별 색상. 8쌍을 넘으면 색이 반복된다.
const PAIR_COLORS = [
  { bg: 'var(--state-success-bg)', color: 'var(--accent-primary)', border: 'var(--accent-soft)' },
  { bg: 'var(--state-danger-bg)', color: 'var(--state-danger)', border: 'var(--state-danger)' },
  { bg: 'var(--state-warning-bg)', color: 'var(--honey-ink)', border: 'var(--honey-soft)' },
  { bg: 'var(--retrieve-bg)', color: 'var(--retrieve-ink)', border: 'var(--retrieve-border)' },
  { bg: 'var(--surface-subtle)', color: 'var(--text-secondary)', border: 'var(--border-soft)' },
  { bg: 'var(--grass-wash)', color: 'var(--grass-1)', border: 'var(--grass-soft)' },
  { bg: 'var(--coral-wash)', color: 'var(--coral-ink-deep)', border: 'var(--coral-primary)' },
  { bg: 'var(--honey-wash)', color: 'var(--honey-ink)', border: 'var(--honey-soft)' },
] as const;

export default function MatchPairBadge({ pairIndex, palette: _palette }: MatchPairBadgeProps) {
  const safeIdx = pairIndex % PAIR_COLORS.length;
  const colors = PAIR_COLORS[safeIdx];
  if (!colors) return null;

  return (
    <span
      aria-label={`짝 ${pairIndex + 1}`}
      style={{
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        width: 20, height: 20, borderRadius: 10,
        fontSize: 10, fontWeight: 800, flexShrink: 0,
        background: colors.bg, color: colors.color,
        border: `1px solid ${colors.border}`,
      }}
    >
      {pairIndex + 1}
    </span>
  );
}
