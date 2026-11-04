// KPI, 성과 수치, 잔액 카드 공용 컴포넌트.
// label, value, meta만 담고 delta나 설명, 보조 통계는 넣지 않는다.
import type { ReactNode } from 'react';

interface MetricTileProps {
  label: string;
  value: ReactNode;
  meta?: ReactNode;
  tone?: 'neutral' | 'accent' | 'danger' | 'warning';
}

function getValueColor(tone: MetricTileProps['tone']): string {
  switch (tone) {
    case 'accent':  return 'var(--accent-primary)';
    case 'danger':  return 'var(--state-danger)';
    case 'warning': return 'var(--state-warning)';
    default:        return 'var(--text-primary)';
  }
}

function getTileBg(tone: MetricTileProps['tone']): string {
  switch (tone) {
    case 'accent':  return 'var(--accent-soft)';
    case 'danger':  return 'var(--state-danger-bg)';
    case 'warning': return 'var(--state-warning-bg)';
    default:        return 'var(--surface-card)';
  }
}

export default function MetricTile({ label, value, meta, tone = 'neutral' }: MetricTileProps) {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 6,
      padding: '16px 20px',
      borderRadius: 14,
      background: getTileBg(tone),
      border: '1px solid var(--border-soft)',
      boxShadow: 'var(--shadow-sm)',
    }}>
      {/* label: 11px/700/uppercase */}
      <span style={{
        fontSize: 11,
        fontWeight: 700,
        textTransform: 'uppercase',
        letterSpacing: '0.08em',
        color: 'var(--text-tertiary)',
        lineHeight: 1.3,
      }}>
        {label}
      </span>
      {/* value: 28px/850 */}
      <div style={{
        fontSize: 28,
        fontWeight: 850,
        letterSpacing: 0,
        lineHeight: 1.1,
        color: getValueColor(tone),
      }}>
        {value}
      </div>
      {/* meta: 보조 정보 (12px/500) */}
      {meta && (
        <div style={{
          fontSize: 12,
          fontWeight: 500,
          color: 'var(--text-muted)',
          lineHeight: 1.45,
        }}>
          {meta}
        </div>
      )}
    </div>
  );
}
