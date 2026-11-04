// 서술형 답안 입력(ko_descriptive). Textarea와 글자수를 보여 준다.
import type { EssayAnswer } from './mockExam.types';

interface EssayInputProps {
  answer: EssayAnswer | undefined;
  questionId: string;
  onChange: (answer: EssayAnswer) => void;
}

// 권장 최대 글자수. 입력을 막지는 않고 표시 기준으로만 쓴다.
const MAX_CHARS = 1000;

export default function EssayInput({
  answer,
  questionId,
  onChange,
}: EssayInputProps) {
  const text = answer?.text ?? '';
  const textareaId = `essay-${questionId}`;
  const charCount = text.length;
  const nearLimit = charCount > MAX_CHARS * 0.9;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <label
        htmlFor={textareaId}
        style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-tertiary)' }}
      >
        서술 답안 입력
      </label>
      <textarea
        id={textareaId}
        value={text}
        onChange={(e) => onChange({ kind: 'essay', text: e.target.value })}
        placeholder="서술형 답안을 입력하세요. 논리적이고 구체적으로 작성하면 더 좋은 점수를 받을 수 있어요."
        aria-label="서술형 답안 입력란"
        rows={6}
        style={{
          width: '100%', padding: '12px 14px', borderRadius: 10, resize: 'vertical',
          fontSize: 13.5, fontWeight: 500, lineHeight: 1.7, color: 'var(--text-primary)',
          background: 'var(--surface-page)',
          border: '1px solid var(--border-default)',
          outline: 'none', fontFamily: 'var(--font-sans)',
        }}
      />
      {/* MAX_CHARS의 90%를 넘으면 경고색으로 바꾼다 */}
      <div style={{
        display: 'flex', justifyContent: 'flex-end',
        fontSize: 11, fontWeight: 700,
        color: nearLimit ? 'var(--state-danger)' : 'var(--text-tertiary)',
      }}>
        {charCount} / {MAX_CHARS}자
      </div>
    </div>
  );
}
