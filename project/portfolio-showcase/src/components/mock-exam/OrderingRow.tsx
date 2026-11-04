// 순서 배열의 한 행. 위·아래 버튼과 키보드로 순서를 바꾼다.
import Icon from '@/Icon';
import type { Palette } from '@/lib';

interface OrderingRowProps {
  index: number;
  total: number;
  label: string;
  text: string;
  palette: Palette;
  onMoveUp: () => void;
  onMoveDown: () => void;
}

export default function OrderingRow({
  index,
  total,
  label,
  text,
  palette,
  onMoveUp,
  onMoveDown,
}: OrderingRowProps) {
  const isFirst = index === 0;
  const isLast = index === total - 1;

  return (
    <div
      style={{
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '10px 12px', borderRadius: 10,
        background: 'var(--surface-page)',
        border: '1px solid var(--border-soft)',
      }}
      // 스크린리더에 현재 순위를 알린다
      aria-label={`${index + 1}번째 항목: ${text}`}
    >
      <span
        aria-hidden
        style={{
          flexShrink: 0, width: 24, height: 24, borderRadius: 12,
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 11, fontWeight: 800, background: palette.wash, color: palette.primary,
        }}
      >
        {index + 1}
      </span>

      <span style={{
        flex: 1, fontSize: 13.5, fontWeight: 500, color: 'var(--text-primary)', lineHeight: 1.5,
      }}>
        {label && (
          <span style={{
            marginRight: 6, fontSize: 11, fontWeight: 800, color: 'var(--text-tertiary)',
          }}>
            {label}
          </span>
        )}
        {text}
      </span>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <button
          onClick={onMoveUp}
          disabled={isFirst}
          aria-label="위로 이동"
          style={{
            width: 24, height: 24, borderRadius: 6,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: isFirst ? 'transparent' : 'var(--surface-subtle)',
            border: isFirst ? 'none' : '1px solid var(--border-soft)',
            cursor: isFirst ? 'not-allowed' : 'pointer',
            opacity: isFirst ? 0.3 : 1,
          }}
        >
          <Icon name="chevron-up" size={12} />
        </button>
        <button
          onClick={onMoveDown}
          disabled={isLast}
          aria-label="아래로 이동"
          style={{
            width: 24, height: 24, borderRadius: 6,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: isLast ? 'transparent' : 'var(--surface-subtle)',
            border: isLast ? 'none' : '1px solid var(--border-soft)',
            cursor: isLast ? 'not-allowed' : 'pointer',
            opacity: isLast ? 0.3 : 1,
          }}
        >
          <Icon name="chevron-down" size={12} />
        </button>
      </div>
    </div>
  );
}
