// 잘한 점·개선할 점 피드백 열 하나(제목과 항목 목록)
import Icon from '@/Icon';
import type { Palette } from '@/lib';

interface FeedbackColProps {
  title: string;
  items: string[];
  icon: string;
  tone: 'grass' | 'coral';
  palette: Palette;
}

function FeedbackCol({ title, items, icon, tone, palette }: FeedbackColProps) {
  const tones = {
    grass: { bg: palette.wash, br: palette.soft, fg: palette.primary },
    coral: { bg: 'var(--coral-wash)', br: 'var(--coral-soft)', fg: 'var(--coral-primary)' },
  }[tone];
  return (
    <div style={{ padding: '14px 16px', borderRadius: 12, background: tones.bg, border: `1px solid ${tones.br}` }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10, fontSize: 12, fontWeight: 800, color: tones.fg, letterSpacing: '-.01em' }}>
        <Icon name={icon} size={12} color={tones.fg} /> {title}
      </div>
      <ul style={{ margin: 0, padding: '0 0 0 2px', listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
        {items.map((it, i) => (
          <li key={i} style={{ display: 'flex', gap: 8, fontSize: 12, lineHeight: 1.55, color: 'var(--ink-2)' }}>
            <span style={{ color: tones.fg, fontWeight: 800 }}>•</span>
            <span>{it}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default FeedbackCol;
