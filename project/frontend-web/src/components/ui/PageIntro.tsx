// 페이지 상단 헤더 공용 컴포넌트. eyebrow, 제목, 설명, 우측 액션 영역이 있다.
import type { ReactNode } from 'react';

interface PageIntroProps {
  eyebrow: string;
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  compact?: boolean;
}

// 타이포 규칙: eyebrow 11px/700/uppercase, title 32px(compact 28px)/850, description 13px/500
export default function PageIntro({ eyebrow, title, description, actions, compact = false }: PageIntroProps) {
  return (
    <div style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'flex-start',
      gap: 16,
      marginBottom: 18,
    }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {/* eyebrow: 11px/700/uppercase/0.14em letter-spacing */}
        <span style={{
          fontSize: 11,
          fontWeight: 700,
          textTransform: 'uppercase',
          letterSpacing: '0.14em',
          lineHeight: 1.4,
          color: 'var(--text-tertiary)',
        }}>
          {eyebrow}
        </span>
        {/* title: 32px/850 (compact면 28px/850) */}
        <div style={{
          fontSize: compact ? 28 : 32,
          fontWeight: 850,
          letterSpacing: 0,
          lineHeight: compact ? 1.18 : 1.15,
          color: 'var(--text-primary)',
        }}>
          {title}
        </div>
        {/* description: 13px/500, 한 문장으로 쓴다 */}
        {description && (
          <div style={{
            fontSize: 13,
            fontWeight: 500,
            lineHeight: 1.6,
            color: 'var(--text-secondary)',
          }}>
            {description}
          </div>
        )}
      </div>
      {/* 액션 영역. 버튼은 1~2개를 둔다 */}
      {actions && (
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexShrink: 0 }}>
          {actions}
        </div>
      )}
    </div>
  );
}
