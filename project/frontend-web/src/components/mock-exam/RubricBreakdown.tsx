// 서술형(ko_descriptive) 채점의 rubricBreakdown 기준 목록.
// needsManualReview이면 수동 검토 안내를 함께 보인다.
import type { RubricBreakdownItem } from '@/apis';
import RubricCriterionRow from './RubricCriterionRow';

interface RubricBreakdownProps {
  items: RubricBreakdownItem[];
  needsManualReview: boolean;
}

export default function RubricBreakdown({
  items,
  needsManualReview,
}: RubricBreakdownProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 10 }}>
      {needsManualReview && (
        <div style={{
          padding: '8px 12px', borderRadius: 9,
          background: 'var(--state-warning-bg)', border: '1px solid var(--honey-soft)',
          fontSize: 11.5, fontWeight: 700, color: 'var(--honey-ink)',
        }}
          role="alert"
          aria-live="polite"
        >
          이 문항은 강사 수동 검토가 필요합니다. 최종 점수가 달라질 수 있어요.
        </div>
      )}

      {items.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{
            fontSize: 11, fontWeight: 800, letterSpacing: '.04em',
            color: 'var(--text-tertiary)', marginBottom: 2,
          }}>
            채점 기준 세부 내역
          </div>
          {items.map((item, idx) => (
            <RubricCriterionRow key={idx} item={item} />
          ))}
        </div>
      ) : (
        !needsManualReview && (
          <div style={{ fontSize: 11.5, color: 'var(--text-tertiary)', fontStyle: 'italic' }}>
            세부 채점 기준이 없습니다.
          </div>
        )
      )}
    </div>
  );
}
