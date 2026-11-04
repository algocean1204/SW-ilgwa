// 객관식 입력(ko_multiple_choice_4/5). 답안은 보기 label("A","B"...)로 저장한다.
import type { Palette } from '@/lib';
import type { QuestionOption } from '@/apis';
import type { MultipleChoiceAnswer } from './mockExam.types';

interface MultipleChoiceInputProps {
  options: QuestionOption[];
  answer: MultipleChoiceAnswer | undefined;
  palette: Palette;
  onSelect: (answer: MultipleChoiceAnswer) => void;
}

export default function MultipleChoiceInput({
  options,
  answer,
  palette,
  onSelect,
}: MultipleChoiceInputProps) {
  if (options.length === 0) {
    return (
      <div
        style={{ fontSize: 12, color: 'var(--text-tertiary)', padding: '8px 0' }}
        role="alert"
        aria-live="polite"
      >
        보기를 불러올 수 없어요.
      </div>
    );
  }

  return (
    <div
      role="radiogroup"
      aria-label="보기 선택"
      style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
    >
      {options.map((opt) => {
        const active = answer?.label === opt.label;
        return (
          <button
            key={opt.label}
            role="radio"
            aria-checked={active}
            onClick={() => onSelect({ kind: 'multiple_choice', label: opt.label })}
            className="lift"
            style={{
              display: 'flex', alignItems: 'center', gap: 12, width: '100%',
              textAlign: 'left', padding: '12px 14px', borderRadius: 10, cursor: 'pointer',
              background: active ? palette.wash : 'var(--surface-page)',
              border: active
                ? `2px solid ${palette.primary}`
                : '1px solid var(--border-soft)',
            }}
          >
            <span
              aria-hidden
              style={{
                flexShrink: 0, width: 24, height: 24, borderRadius: 12,
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 12, fontWeight: 800,
                background: active ? palette.primary : 'var(--surface-card)',
                color: active ? 'var(--paper)' : 'var(--text-tertiary)',
                border: active ? 'none' : '1px solid var(--border-soft)',
              }}
            >
              {opt.label}
            </span>
            <span style={{
              fontSize: 13.5, lineHeight: 1.5,
              fontWeight: active ? 700 : 500,
              color: active ? palette.primary : 'var(--text-primary)',
            }}>
              {opt.text}
            </span>
          </button>
        );
      })}
    </div>
  );
}
