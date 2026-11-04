// 단답형 한 줄 입력(ko_short_answer)
import type { ShortAnswerAnswer } from './mockExam.types';

interface ShortAnswerInputProps {
  answer: ShortAnswerAnswer | undefined;
  questionId: string;
  onChange: (answer: ShortAnswerAnswer) => void;
}

export default function ShortAnswerInput({
  answer,
  questionId,
  onChange,
}: ShortAnswerInputProps) {
  const inputId = `short-answer-${questionId}`;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <label
        htmlFor={inputId}
        style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-tertiary)' }}
      >
        답안 입력
      </label>
      <input
        id={inputId}
        type="text"
        value={answer?.text ?? ''}
        onChange={(e) =>
          onChange({ kind: 'short_answer', text: e.target.value })
        }
        placeholder="답을 입력하세요"
        aria-label="단답 입력란"
        style={{
          width: '100%', padding: '11px 14px', borderRadius: 10,
          fontSize: 14, fontWeight: 500, color: 'var(--text-primary)',
          background: 'var(--surface-page)',
          border: '1px solid var(--border-default)',
          outline: 'none',
        }}
      />
    </div>
  );
}
