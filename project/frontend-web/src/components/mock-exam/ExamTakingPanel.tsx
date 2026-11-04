// CBT 응시 패널. 한 문항과 답안 영역(ExamAnswerZone), 이전·다음 이동을 보인다.
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import type { AttemptDetailResponse } from '@/apis';
import type { AnswerMap, ExamAnswer } from './mockExam.types';
import ExamAnswerZone from './ExamAnswerZone';

interface ExamTakingPanelProps {
  detail: AttemptDetailResponse;
  currentIdx: number;
  answers: AnswerMap;
  palette: Palette;
  onAnswer: (questionId: string, answer: ExamAnswer) => void;
  onPrev: () => void;
  onNext: () => void;
}

export default function ExamTakingPanel({
  detail, currentIdx, answers, palette, onAnswer, onPrev, onNext,
}: ExamTakingPanelProps) {
  const total = detail.questions.length;
  const q = detail.questions[currentIdx];
  if (!q) {
    return (
      <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--text-tertiary)', fontSize: 13 }}>
        문항을 불러올 수 없어요.
      </div>
    );
  }

  return (
    <div style={{
      background: 'var(--surface-card)', border: '1px solid var(--border-default)',
      borderRadius: 16, boxShadow: 'var(--shadow-md)', overflow: 'hidden',
    }}>
      {/* 번호 N/총, 배점 */}
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        padding: '14px 20px', borderBottom: '1px solid var(--border-soft)',
      }}>
        <span style={{ fontSize: 13, fontWeight: 800, color: palette.primary }}>
          문항 {currentIdx + 1}
          <span style={{ color: 'var(--text-tertiary)', fontWeight: 700 }}> / {total}</span>
        </span>
        <span style={{
          fontSize: 11, fontWeight: 700, padding: '3px 8px', borderRadius: 6,
          background: 'var(--surface-page)', border: '1px solid var(--border-soft)',
          color: 'var(--text-secondary)',
        }}>
          {q.points}점
        </span>
      </div>

      <div style={{ padding: '20px 20px 8px' }}>
        <p style={{ margin: 0, fontSize: 15, fontWeight: 600, lineHeight: 1.7, color: 'var(--text-primary)' }}>
          {q.stem}
        </p>
      </div>

      <div style={{ padding: '8px 20px 16px' }}>
        <ExamAnswerZone
          question={q}
          answers={answers}
          palette={palette}
          onAnswer={onAnswer}
        />
      </div>

      <div style={{
        display: 'flex', justifyContent: 'space-between', gap: 8,
        padding: '14px 20px', borderTop: '1px solid var(--border-soft)',
      }}>
        <button
          onClick={onPrev}
          disabled={currentIdx === 0}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: 4,
            padding: '9px 14px', borderRadius: 9, fontSize: 12, fontWeight: 700,
            background: 'var(--surface-card)', border: '1px solid var(--border-default)',
            color: currentIdx === 0 ? 'var(--text-tertiary)' : 'var(--text-secondary)',
            cursor: currentIdx === 0 ? 'not-allowed' : 'pointer',
            opacity: currentIdx === 0 ? 0.5 : 1,
          }}
        >
          <Icon name="chevron-left" size={14} /> 이전
        </button>
        <button
          onClick={onNext}
          disabled={currentIdx === total - 1}
          className="lift"
          style={{
            display: 'inline-flex', alignItems: 'center', gap: 4,
            padding: '9px 14px', borderRadius: 9, fontSize: 12, fontWeight: 800,
            background: currentIdx === total - 1 ? 'var(--surface-card)' : palette.primary,
            border: currentIdx === total - 1 ? '1px solid var(--border-default)' : 'none',
            color: currentIdx === total - 1 ? 'var(--text-tertiary)' : 'var(--paper)',
            cursor: currentIdx === total - 1 ? 'not-allowed' : 'pointer',
            opacity: currentIdx === total - 1 ? 0.5 : 1,
          }}
        >
          다음 <Icon name="chevron-right" size={14} color={currentIdx === total - 1 ? undefined : 'var(--paper)'} />
        </button>
      </div>
    </div>
  );
}
