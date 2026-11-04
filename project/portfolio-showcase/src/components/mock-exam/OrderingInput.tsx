// 순서 배열 입력(ko_ordering). 키보드로도 쓸 수 있게 드래그 대신 버튼으로 순서를 바꾼다.
// 처음 순서는 orderingItems 원본이고, 옮길 때마다 answer에 반영한다.
import { useState } from 'react';
import type { Palette } from '@/lib';
import type { OrderingAnswer } from './mockExam.types';
import OrderingRow from './OrderingRow';

interface OrderingInputProps {
  items: string[];
  answer: OrderingAnswer | undefined;
  palette: Palette;
  onChange: (answer: OrderingAnswer) => void;
}

export default function OrderingInput({
  items,
  answer,
  palette,
  onChange,
}: OrderingInputProps) {
  // 문항이 바뀌어 items가 달라질 때만 순서를 초기화한다. react-hooks/refs 때문에 ref 대신
  // 이전 props를 state에 두고 렌더 중 조건부 setState를 하는 React 공식 패턴을 쓴다.
  const [prevItems, setPrevItems] = useState<string[]>(items);
  const initialOrder = answer?.order ?? [...items];
  const [ordered, setOrdered] = useState<string[]>(initialOrder);

  if (prevItems !== items) {
    setPrevItems(items);
    setOrdered(answer?.order ?? [...items]);
  }

  const moveItem = (idx: number, direction: 'up' | 'down') => {
    const next = [...ordered];
    const targetIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (targetIdx < 0 || targetIdx >= next.length) return;
    const temp = next[idx];
    next[idx] = next[targetIdx] ?? '';
    next[targetIdx] = temp ?? '';
    setOrdered(next);
    onChange({ kind: 'ordering', order: next });
  };

  if (items.length === 0) {
    return (
      <div
        style={{ fontSize: 12, color: 'var(--text-tertiary)', padding: '8px 0' }}
        role="alert"
      >
        항목을 불러올 수 없어요.
      </div>
    );
  }

  return (
    <div
      role="list"
      aria-label="순서 배열 — 위/아래 버튼으로 순서를 바꾸세요"
      aria-live="polite"
      style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
    >
      <div style={{ marginBottom: 4, fontSize: 11, fontWeight: 700, color: 'var(--text-tertiary)' }}>
        위/아래 버튼으로 순서를 조정하세요
      </div>
      {ordered.map((item, idx) => (
        <div key={`${item}-${idx}`} role="listitem">
          <OrderingRow
            index={idx}
            total={ordered.length}
            label=""
            text={item}
            palette={palette}
            onMoveUp={() => moveItem(idx, 'up')}
            onMoveDown={() => moveItem(idx, 'down')}
          />
        </div>
      ))}
    </div>
  );
}
