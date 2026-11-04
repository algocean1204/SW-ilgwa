// 루브릭 기준 한 행. 기준명, 배점 대비 획득 점수, 평가 이유를 보인다.
import type { RubricBreakdownItem } from '@/apis';
import StatusBadge from '@/components/ui/StatusBadge';

interface RubricCriterionRowProps {
  item: RubricBreakdownItem;
}

export default function RubricCriterionRow({ item }: RubricCriterionRowProps) {
  const pct = item.maxScore > 0 ? Math.round((item.score / item.maxScore) * 100) : 0;
  const isFullScore = item.score >= item.maxScore;
  const isZero = item.score === 0;
  const tone = isFullScore ? 'success' : isZero ? 'danger' : 'warning';

  return (
    <div style={{
      padding: '10px 12px', borderRadius: 9,
      background: 'var(--surface-subtle)', border: '1px solid var(--border-soft)',
      display: 'flex', flexDirection: 'column', gap: 6,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
        <span style={{ flex: 1, fontSize: 12, fontWeight: 700, color: 'var(--text-primary)' }}>
          {item.criterion}
        </span>
        <StatusBadge
          tone={tone}
          label={`${item.score} / ${item.maxScore}점`}
          size="sm"
        />
      </div>

      <div style={{
        height: 4, background: 'var(--border-soft)', borderRadius: 2, overflow: 'hidden',
      }}>
        <div style={{
          height: '100%', width: `${pct}%`, borderRadius: 2,
          background: isFullScore
            ? 'var(--accent-primary)'
            : isZero
              ? 'var(--state-danger)'
              : 'var(--state-warning)',
        }} />
      </div>

      {item.reason && (
        <p style={{
          margin: 0, fontSize: 11.5, lineHeight: 1.6,
          color: 'var(--text-secondary)', whiteSpace: 'pre-wrap',
        }}>
          {item.reason}
        </p>
      )}
    </div>
  );
}
