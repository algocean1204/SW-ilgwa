// 상단 탭, 필터 탭, 범위 전환에 쓰는 공용 세그먼트 컨트롤.
// PricingTabs, ReportHeader, LearningRightPanel 우측 탭에서 쓴다.
import type { ReactNode } from 'react';

interface SegmentItem {
  id: string;
  label: ReactNode;
  icon?: ReactNode;
}

interface SegmentedControlProps {
  items: SegmentItem[];
  value: string;
  onChange: (id: string) => void;
  compact?: boolean;
}

export default function SegmentedControl({ items, value, onChange, compact = false }: SegmentedControlProps) {
  return (
    <div style={{
      display: 'inline-flex',
      background: 'var(--surface-subtle)',
      border: '1px solid var(--border-soft)',
      borderRadius: 8,
      padding: 2,
      gap: 1,
    }}>
      {items.map((item) => {
        const isActive = item.id === value;
        return (
          <button
            key={item.id}
            onClick={() => onChange(item.id)}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              padding: compact ? '4px 10px' : '5px 12px',
              borderRadius: 6,
              fontSize: 12,
              fontWeight: 700,
              lineHeight: 1.45,
              color: isActive ? 'var(--accent-primary)' : 'var(--text-secondary)',
              background: isActive ? 'var(--surface-card)' : 'transparent',
              boxShadow: isActive ? 'var(--shadow-sm)' : 'none',
              border: 'none',
              cursor: 'pointer',
              transition: 'background 0.15s, color 0.15s',
            }}
          >
            {item.icon && (
              <span style={{ display: 'flex', alignItems: 'center', width: 14, height: 14 }}>
                {item.icon}
              </span>
            )}
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
