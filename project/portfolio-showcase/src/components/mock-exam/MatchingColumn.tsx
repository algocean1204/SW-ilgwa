// 연결하기의 좌 또는 우 컬럼. 선택 상태, 짝 배지, 키보드 탭 이동을 처리한다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';
import MatchPairBadge from './MatchPairBadge';

interface MatchingColumnProps {
  items: string[];
  side: 'left' | 'right';
  selectedItem: string | null;
  // item → pairIndex (짝이 없으면 -1)
  pairMap: Record<string, number>;
  palette: Palette;
  onSelect: (item: string) => void;
}

export default function MatchingColumn({
  items,
  side,
  selectedItem,
  pairMap,
  palette,
  onSelect,
}: MatchingColumnProps) {
  return (
    <div
      style={{ display: 'flex', flexDirection: 'column', gap: 6, flex: 1 }}
      role="listbox"
      aria-label={side === 'left' ? '왼쪽 항목' : '오른쪽 항목'}
    >
      <div style={{
        display: 'flex', alignItems: 'center', gap: 4,
        fontSize: 10, fontWeight: 800, letterSpacing: '.06em',
        color: 'var(--text-tertiary)', marginBottom: 2,
        textTransform: 'uppercase',
      }}>
        <Icon name="play" size={9} />
        {side === 'left' ? '선택' : '연결'}
      </div>
      {items.map((item) => {
        const pairIdx = pairMap[item] ?? -1;
        const hasPair = pairIdx >= 0;
        const isSelected = selectedItem === item;

        return (
          <button
            key={item}
            role="option"
            aria-selected={isSelected || hasPair}
            aria-label={hasPair ? `${item} (짝 ${pairIdx + 1}번)` : item}
            onClick={() => onSelect(item)}
            className="lift"
            style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '10px 12px', borderRadius: 10, textAlign: 'left',
              cursor: 'pointer',
              background: isSelected
                ? palette.wash
                : hasPair
                  ? 'var(--state-success-bg)'
                  : 'var(--surface-page)',
              border: isSelected
                ? `2px solid ${palette.primary}`
                : hasPair
                  ? '1px solid var(--accent-soft)'
                  : '1px solid var(--border-soft)',
            }}
          >
            {hasPair && <MatchPairBadge pairIndex={pairIdx} palette={palette} />}
            <span style={{
              fontSize: 13, fontWeight: hasPair ? 700 : 500,
              color: hasPair ? 'var(--accent-primary)' : 'var(--text-primary)',
              lineHeight: 1.5, flex: 1,
            }}>
              {item}
            </span>
          </button>
        );
      })}
    </div>
  );
}
