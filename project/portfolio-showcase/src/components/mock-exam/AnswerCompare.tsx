// 내 답과 정답을 나란히 보여 준다. reviewAnswer가 없으면(미채점) 내 답만 보인다.
// correct=true이면 내 답을 성공 색으로, false이면 내 답 빨강·정답 초록으로 표시한다.
// 필드명은 Spring QuestionGradeResponse.reviewAnswer를 따른다.
import type { Palette } from '@/lib';

interface AnswerCompareProps {
  myAnswer: string;
  /** 정답. GRADED 복습 때만 들어오며, 없으면 정답 섹션을 숨긴다. */
  reviewAnswer: string | undefined;
  correct: boolean;
  palette: Palette;
}

export default function AnswerCompare({ myAnswer, reviewAnswer, correct, palette }: AnswerCompareProps) {
  const myAnswerBg = correct ? 'var(--state-success-bg)' : 'var(--state-danger-bg)';
  const myAnswerBorder = correct ? palette.soft : 'var(--state-danger)';
  const myAnswerColor = correct ? palette.primary : 'var(--coral-ink-deep)';

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 6,
      marginBottom: 10,
    }}>
      <div style={{
        display: 'flex',
        gap: 8,
        alignItems: 'flex-start',
        padding: '8px 12px',
        borderRadius: 9,
        background: myAnswerBg,
        border: `1px solid ${myAnswerBorder}`,
      }}>
        <span style={{
          fontSize: 11,
          fontWeight: 800,
          color: myAnswerColor,
          whiteSpace: 'nowrap',
          paddingTop: 1,
          minWidth: 40,
        }}>
          내 답
        </span>
        <span style={{
          fontSize: 13,
          fontWeight: 600,
          color: myAnswerColor,
          lineHeight: 1.5,
          wordBreak: 'break-word',
        }}>
          {myAnswer}
        </span>
      </div>

      {/* reviewAnswer가 있을 때만 정답을 보인다 */}
      {reviewAnswer !== undefined && !correct && (
        <div style={{
          display: 'flex',
          gap: 8,
          alignItems: 'flex-start',
          padding: '8px 12px',
          borderRadius: 9,
          background: 'var(--state-success-bg)',
          border: `1px solid ${palette.soft}`,
        }}>
          <span style={{
            fontSize: 11,
            fontWeight: 800,
            color: palette.primary,
            whiteSpace: 'nowrap',
            paddingTop: 1,
            minWidth: 40,
          }}>
            정답
          </span>
          <span style={{
            fontSize: 13,
            fontWeight: 600,
            color: palette.primary,
            lineHeight: 1.5,
            wordBreak: 'break-word',
          }}>
            {reviewAnswer}
          </span>
        </div>
      )}
    </div>
  );
}
