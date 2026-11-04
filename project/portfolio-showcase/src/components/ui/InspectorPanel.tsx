// 오른쪽 보조 패널 공용 컴포넌트.
// 배경(--surface-subtle), 보더, 그림자 모두 메인 카드보다 약하게 둔다.
import type { ReactNode } from 'react';

interface InspectorPanelProps {
  title?: ReactNode;
  children: ReactNode;
  sticky?: boolean;
}

export default function InspectorPanel({ title, children, sticky = false }: InspectorPanelProps) {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      background: 'var(--surface-subtle)',
      border: '1px solid var(--border-soft)',
      borderRadius: 14,
      overflow: 'hidden',
      ...(sticky ? {
        position: 'sticky',
        top: 'var(--aside-top-offset)',
        alignSelf: 'flex-start',
      } : {}),
    }}>
      {/* 패널 제목. 작고 옅게 쓴다 */}
      {title && (
        <div style={{
          padding: '12px 16px 10px',
          borderBottom: '1px solid var(--border-soft)',
        }}>
          <span style={{
            fontSize: 11,
            fontWeight: 700,
            textTransform: 'uppercase',
            letterSpacing: '0.08em',
            color: 'var(--text-tertiary)',
          }}>
            {title}
          </span>
        </div>
      )}
      <div style={{ padding: '14px 16px', flex: 1 }}>
        {children}
      </div>
    </div>
  );
}
