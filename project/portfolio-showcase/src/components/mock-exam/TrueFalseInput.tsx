// OX 입력(ko_true_false). 색약 사용자를 위해 색과 함께 기호·글자로 구분한다.
import type { Palette } from '@/lib';
import type { TrueFalseAnswer } from './mockExam.types';

interface TrueFalseInputProps {
  answer: TrueFalseAnswer | undefined;
  palette: Palette;
  onSelect: (answer: TrueFalseAnswer) => void;
}

export default function TrueFalseInput({
  answer,
  palette,
  onSelect,
}: TrueFalseInputProps) {
  return (
    <div
      role="radiogroup"
      aria-label="O/X 선택"
      style={{ display: 'flex', gap: 12 }}
    >
      {(['O', 'X'] as const).map((label) => {
        const active = answer?.label === label;
        // O는 팔레트 primary, X는 위험색
        const activeColor =
          label === 'O' ? palette.primary : 'var(--state-danger)';
        const activeBg =
          label === 'O' ? palette.wash : 'var(--state-danger-bg)';
        const activeBorder =
          label === 'O' ? palette.primary : 'var(--state-danger)';

        return (
          <button
            key={label}
            role="radio"
            aria-checked={active}
            aria-label={label === 'O' ? '맞다(O)' : '틀리다(X)'}
            onClick={() => onSelect({ kind: 'true_false', label })}
            className="lift"
            style={{
              flex: 1, padding: '18px 0', borderRadius: 12,
              display: 'flex', flexDirection: 'column',
              alignItems: 'center', justifyContent: 'center', gap: 6,
              cursor: 'pointer',
              background: active ? activeBg : 'var(--surface-page)',
              border: active
                ? `2px solid ${activeBorder}`
                : '1px solid var(--border-soft)',
            }}
          >
            <span style={{
              fontSize: 36, fontWeight: 900, lineHeight: 1,
              color: active ? activeColor : 'var(--text-tertiary)',
              fontFamily: 'var(--font-sans)',
            }}>
              {label}
            </span>
            <span style={{
              fontSize: 12, fontWeight: 700,
              color: active ? activeColor : 'var(--text-tertiary)',
            }}>
              {label === 'O' ? '맞다' : '틀리다'}
            </span>
          </button>
        );
      })}
    </div>
  );
}
