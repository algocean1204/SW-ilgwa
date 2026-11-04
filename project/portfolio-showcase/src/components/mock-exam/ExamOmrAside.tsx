// OMR 답안지. 문항 이동 그리드, 타이머, 제출 버튼을 보인다.
// 유형과 관계없이 answer가 있으면 답한 것으로 본다.
import type { Palette } from '@/lib';
import type { AttemptQuestion } from '@/apis';
import ExamTimer from './ExamTimer';
import type { AnswerMap } from './mockExam.types';

interface ExamOmrAsideProps {
  questions: AttemptQuestion[];
  answers: AnswerMap;
  currentIdx: number;
  limitMinutes: number | null;
  palette: Palette;
  onJump: (idx: number) => void;
  onSubmit: () => void;
  onExpire: () => void;
}

/**
 * 답안이 있는지 본다. 빈칸 유형은 blanks에 값이 있어야 답한 것으로 본다.
 */
function isAnswered(answer: AnswerMap[string]): boolean {
  if (!answer) return false;
  switch (answer.kind) {
    case 'multiple_choice':
    case 'true_false':
      return answer.label.length > 0;
    case 'short_answer':
    case 'essay':
      return answer.text.trim().length > 0;
    case 'cloze':
      return answer.blanks.some((b) => b.trim().length > 0);
    case 'ordering':
      return answer.order.length > 0;
    case 'matching':
      return Object.keys(answer.pairs).length > 0;
  }
}

export default function ExamOmrAside({
  questions, answers, currentIdx, limitMinutes, palette, onJump, onSubmit, onExpire,
}: ExamOmrAsideProps) {
  const answeredCount = questions.filter((q) => {
    const ans = answers[q.questionId];
    return ans !== undefined && isAnswered(ans);
  }).length;
  const total = questions.length;
  const pct = total > 0 ? Math.round((answeredCount / total) * 100) : 0;

  return (
    <div style={{
      display: 'flex', flexDirection: 'column', gap: 12,
      background: 'var(--surface-subtle)', border: '1px solid var(--border-soft)',
      borderRadius: 14, padding: 14,
    }}>
      {/* 무제한이면 ExamTimer가 null을 반환한다 */}
      <ExamTimer limitMinutes={limitMinutes} primary={palette.primary} onExpire={onExpire} />

      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
          <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-tertiary)', letterSpacing: '.04em' }}>
            답안지
          </span>
          <span style={{ fontSize: 11, fontWeight: 800, color: palette.primary }}>
            {answeredCount}/{total}
          </span>
        </div>
        <div style={{ height: 5, background: 'var(--surface-page)', borderRadius: 3, overflow: 'hidden' }}>
          <div style={{ height: '100%', width: `${pct}%`, background: palette.primary2, borderRadius: 3 }} />
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 6 }}>
        {questions.map((q, idx) => {
          const isCurrent = idx === currentIdx;
          const rawAns = answers[q.questionId];
          const answered = rawAns !== undefined && isAnswered(rawAns);
          const bg = isCurrent
            ? palette.primary
            : answered
              ? palette.wash
              : 'var(--surface-page)';
          const color = isCurrent
            ? 'var(--paper)'
            : answered
              ? palette.primary
              : 'var(--text-tertiary)';
          const border = isCurrent
            ? palette.primary
            : answered
              ? palette.soft
              : 'var(--border-soft)';
          return (
            <button
              key={q.questionId}
              onClick={() => onJump(idx)}
              className="lift"
              aria-label={`문항 ${idx + 1}${answered ? ' (답함)' : ''}${isCurrent ? ' (현재)' : ''}`}
              aria-current={isCurrent ? 'true' : undefined}
              style={{
                aspectRatio: '1 / 1', borderRadius: 8,
                background: bg, color, border: `1px solid ${border}`,
                fontSize: 12, fontWeight: 800, cursor: 'pointer',
              }}
            >
              {idx + 1}
            </button>
          );
        })}
      </div>

      {/* 색약 사용자를 위해 색과 글자를 함께 쓴다 */}
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', fontSize: 10, color: 'var(--text-tertiary)' }}>
        <LegendDot color={palette.primary} label="현재" />
        <LegendDot color={palette.wash} label="답함" border={palette.soft} />
        <LegendDot color="var(--surface-page)" label="안 풂" border="var(--border-soft)" />
      </div>

      <button
        className="lift"
        onClick={onSubmit}
        style={{
          marginTop: 2, padding: '12px 16px', borderRadius: 10,
          background: palette.primary, color: 'var(--paper)',
          fontSize: 13, fontWeight: 800, cursor: 'pointer',
          boxShadow: 'var(--shadow-md)',
        }}
      >
        제출하기
      </button>
    </div>
  );
}

function LegendDot({ color, label, border }: { color: string; label: string; border?: string }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
      <span
        aria-hidden
        style={{
          width: 10, height: 10, borderRadius: 3, background: color,
          border: border ? `1px solid ${border}` : 'none',
        }}
      />
      {label}
    </span>
  );
}
