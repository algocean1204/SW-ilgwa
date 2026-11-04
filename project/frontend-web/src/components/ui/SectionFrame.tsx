// 섹션 틀 공용 컴포넌트. 제목, 설명, 우측 액션, 본문 슬롯이 있다.
import type { ReactNode, CSSProperties } from 'react';

interface SectionFrameProps {
  /** DOM 앵커. PageContextSidebar가 scrollIntoView로 찾아간다. */
  id?: string;
  title?: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  padded?: boolean;
  tone?: 'default' | 'subtle' | 'transparent';
}

function getToneStyle(tone: 'default' | 'subtle' | 'transparent'): CSSProperties {
  switch (tone) {
    case 'subtle':
      // 보조 패널 배경은 메인 카드보다 한 톤 옅다
      return { background: 'var(--surface-subtle)', border: '1px solid var(--border-soft)' };
    case 'transparent':
      // 내용이 레이아웃 그 자체인 경우에만 사용
      return { background: 'transparent', border: 'none' };
    default:
      // 기본 카드 배경
      return { background: 'var(--surface-card)', border: '1px solid var(--border-default)' };
  }
}

export default function SectionFrame({
  id,
  title,
  description,
  action,
  children,
  padded = true,
  tone = 'default',
}: SectionFrameProps) {
  const toneStyle = getToneStyle(tone);
  return (
    <div id={id} style={{
      borderRadius: 16,
      boxShadow: tone === 'transparent' ? 'none' : 'var(--shadow-md)',
      overflow: 'hidden',
      ...toneStyle,
    }}>
      {/* 섹션 헤더. 제목, 설명, 액션 중 하나라도 있을 때만 그린다 */}
      {(title || description || action) && (
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          padding: padded ? '16px 20px 12px' : '12px 16px',
          borderBottom: tone !== 'transparent' ? '1px solid var(--border-soft)' : undefined,
          gap: 12,
        }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {title && (
              <div style={{
                fontSize: 14,
                fontWeight: 800,
                color: 'var(--text-primary)',
                lineHeight: 1.45,
              }}>
                {title}
              </div>
            )}
            {/* description: 한 문장으로 쓴다 */}
            {description && (
              <div style={{
                fontSize: 12,
                fontWeight: 500,
                color: 'var(--text-tertiary)',
                lineHeight: 1.6,
              }}>
                {description}
              </div>
            )}
          </div>
          {action && (
            <div style={{ flexShrink: 0 }}>
              {action}
            </div>
          )}
        </div>
      )}
      <div style={{ padding: padded ? '16px 20px' : 0 }}>
        {children}
      </div>
    </div>
  );
}
