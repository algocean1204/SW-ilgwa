// 연결하기 입력(ko_matching). 왼쪽을 누르고 오른쪽을 누르면 짝이 생긴다.
// 폭이 400px 미만이면 select로 바꾼다.
import { useState } from 'react';
import type { Palette } from '@/lib';
import type { MatchingAnswer } from './mockExam.types';
import MatchingColumn from './MatchingColumn';

interface MatchingInputProps {
  leftItems: string[];
  rightItems: string[];
  answer: MatchingAnswer | undefined;
  palette: Palette;
  onChange: (answer: MatchingAnswer) => void;
}

export default function MatchingInput({
  leftItems,
  rightItems,
  answer,
  palette,
  onChange,
}: MatchingInputProps) {
  const [selectedLeft, setSelectedLeft] = useState<string | null>(null);

  const pairs = answer?.pairs ?? {};

  const handleLeftSelect = (item: string) => {
    setSelectedLeft((prev) => (prev === item ? null : item));
  };

  // 왼쪽을 고른 상태에서만 짝을 만든다.
  const handleRightSelect = (rightItem: string) => {
    if (!selectedLeft) return;
    const next = { ...pairs, [selectedLeft]: rightItem };
    onChange({ kind: 'matching', pairs: next });
    setSelectedLeft(null);
  };

  const handleRemovePair = (leftItem: string) => {
    const next = { ...pairs };
    delete next[leftItem];
    onChange({ kind: 'matching', pairs: next });
    setSelectedLeft(null);
  };

  // 짝 번호는 leftItems 순서를 따른다.
  const leftPairMap: Record<string, number> = {};
  const rightPairMap: Record<string, number> = {};
  leftItems.forEach((lItem, idx) => {
    if (pairs[lItem] !== undefined) {
      leftPairMap[lItem] = idx;
      rightPairMap[pairs[lItem]!] = idx;
    }
  });

  // 이미 짝이 있는 왼쪽 항목을 다시 누르면 짝을 푼다.
  const handleLeftClick = (item: string) => {
    if (pairs[item] !== undefined) {
      handleRemovePair(item);
    } else {
      handleLeftSelect(item);
    }
  };

  const isMobile = typeof window !== 'undefined' && window.innerWidth < 400;

  if (isMobile) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-tertiary)' }}>
          각 왼쪽 항목에 맞는 오른쪽 항목을 선택하세요
        </div>
        {leftItems.map((lItem) => (
          <div key={lItem} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ flex: 1, fontSize: 13, fontWeight: 500, color: 'var(--text-primary)' }}>
              {lItem}
            </span>
            <select
              aria-label={`${lItem}에 연결할 항목`}
              value={pairs[lItem] ?? ''}
              onChange={(e) => {
                const val = e.target.value;
                if (!val) {
                  const next = { ...pairs };
                  delete next[lItem];
                  onChange({ kind: 'matching', pairs: next });
                } else {
                  onChange({ kind: 'matching', pairs: { ...pairs, [lItem]: val } });
                }
              }}
              style={{
                flex: 1, padding: '8px 10px', borderRadius: 8,
                fontSize: 13, color: 'var(--text-primary)',
                background: 'var(--surface-page)',
                border: '1px solid var(--border-default)',
              }}
            >
              <option value="">-- 선택 --</option>
              {rightItems.map((rItem) => (
                <option key={rItem} value={rItem}>{rItem}</option>
              ))}
            </select>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{
        fontSize: 11, fontWeight: 700, color: 'var(--text-tertiary)',
        padding: '6px 10px', borderRadius: 8,
        background: 'var(--surface-subtle)', border: '1px solid var(--border-soft)',
      }}>
        {selectedLeft
          ? `"${selectedLeft}" 선택됨 — 오른쪽 항목을 클릭해 연결하세요`
          : '왼쪽 항목을 선택한 후 오른쪽 항목과 연결하세요. 연결된 항목을 다시 클릭하면 짝이 해제됩니다.'}
      </div>
      <div style={{ display: 'flex', gap: 16 }}>
        <MatchingColumn
          items={leftItems}
          side="left"
          selectedItem={selectedLeft}
          pairMap={leftPairMap}
          palette={palette}
          onSelect={handleLeftClick}
        />
        <div style={{
          width: 1, background: 'var(--border-soft)', flexShrink: 0, alignSelf: 'stretch',
        }} aria-hidden />
        <MatchingColumn
          items={rightItems}
          side="right"
          selectedItem={null}
          pairMap={rightPairMap}
          palette={palette}
          onSelect={handleRightSelect}
        />
      </div>
    </div>
  );
}
