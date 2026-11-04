// 결과지 문항 카드. 문제 본문, 내 답, 정답, 피드백, 해설, 루브릭을 보인다.
// 백엔드 GradingLeakGuard 이름을 따른다: correctAnswer→reviewAnswer, explanation→reviewSolution,
// options→displayOptions.
import { useState } from 'react';
import StatusBadge from '@/components/ui/StatusBadge';
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import type { QuestionResultItem } from '@/apis';
import RubricBreakdown from './RubricBreakdown';
import AnswerCompare from './AnswerCompare';

interface ExamResultQuestionCardProps {
  item: QuestionResultItem;
  /** 목록 내 0-based 인덱스. displayOrder가 없을 때 번호로 쓴다. */
  index: number;
  palette: Palette;
}

export default function ExamResultQuestionCard({ item, index, palette }: ExamResultQuestionCardProps) {
  const [rubricOpen, setRubricOpen] = useState(false);

  // 채점 전이나 레거시 경로에서는 templateId가 null로 올 수 있다.
  const isDescriptive = (item.templateId ?? '').includes('descriptive');
  const hasRubric = item.rubricBreakdown && item.rubricBreakdown.length > 0;
  const showRubricToggle = isDescriptive && (hasRubric || item.needsManualReview);

  const questionNumber = item.displayOrder ?? index + 1;

  // 부분 점수가 있어 score/maxScore를 그대로 보인다.
  const scoreDisplay = `${item.score} / ${item.maxScore}점`;

  return (
    <div style={{
      padding: '14px 16px',
      borderRadius: 12,
      background: 'var(--surface-page)',
      border: `1px solid ${item.correct ? palette.soft : 'var(--state-danger)'}`,
    }}>
      {/* 문항 번호, 정오답 뱃지, 채점 방식, 배점 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, flexWrap: 'wrap' }}>
        <span style={{ fontSize: 12, fontWeight: 800, color: palette.primary }}>
          문항 {questionNumber}
        </span>
        <StatusBadge
          tone={item.correct ? 'success' : item.needsManualReview ? 'warning' : 'danger'}
          label={item.correct ? '정답' : item.needsManualReview ? '검토 중' : '오답'}
          size="sm"
        />
        {item.gradingMode && (
          <StatusBadge tone="neutral" label={item.gradingMode} size="sm" />
        )}
        <span style={{ marginLeft: 'auto', fontSize: 11, color: 'var(--text-tertiary)', fontWeight: 700 }}>
          {scoreDisplay}
        </span>
      </div>

      {item.stem && (
        <div style={{
          padding: '10px 12px',
          borderRadius: 9,
          marginBottom: 10,
          background: 'var(--surface-card)',
          border: '1px solid var(--border-soft)',
          fontSize: 13,
          color: 'var(--text-primary)',
          lineHeight: 1.7,
          fontWeight: 500,
        }}>
          {item.stem}
        </div>
      )}

      {/* reviewAnswer는 GRADED일 때만 들어온다 */}
      {item.myAnswer !== undefined && item.myAnswer !== '' && (
        <AnswerCompare
          myAnswer={item.myAnswer}
          reviewAnswer={item.reviewAnswer}
          correct={item.correct}
          palette={palette}
        />
      )}

      {item.feedback && (
        <div style={{
          padding: '10px 12px',
          borderRadius: 9,
          marginBottom: 8,
          background: 'var(--surface-subtle)',
          border: '1px solid var(--border-soft)',
          fontSize: 12,
          color: 'var(--text-secondary)',
          lineHeight: 1.6,
        }}>
          <span style={{ fontWeight: 800, color: 'var(--text-primary)', marginRight: 6 }}>피드백</span>
          {item.feedback}
        </div>
      )}

      {/* 해설(reviewSolution)은 GRADED일 때만 들어오며 피드백과 따로 보인다 */}
      {item.reviewSolution && (
        <div style={{
          padding: '10px 12px',
          borderRadius: 9,
          marginBottom: 8,
          background: 'var(--sage-wash)',
          border: `1px solid ${palette.soft}`,
          fontSize: 12,
          color: 'var(--text-secondary)',
          lineHeight: 1.6,
        }}>
          <span style={{ fontWeight: 800, color: palette.primary, marginRight: 6 }}>해설</span>
          {item.reviewSolution}
        </div>
      )}

      {/* 서술형만 루브릭 세부를 펼칠 수 있다 */}
      {showRubricToggle && (
        <>
          <button
            onClick={() => setRubricOpen((v) => !v)}
            aria-expanded={rubricOpen}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              padding: '5px 10px',
              borderRadius: 7,
              fontSize: 11,
              fontWeight: 700,
              background: 'var(--surface-subtle)',
              border: '1px solid var(--border-soft)',
              color: palette.primary,
              cursor: 'pointer',
              marginBottom: 4,
            }}
          >
            <Icon name="arrowDown" size={11} style={{ transform: rubricOpen ? 'rotate(180deg)' : 'none', transition: 'transform .15s ease' }} />
            {rubricOpen ? '채점 기준 숨기기' : '채점 기준 보기'}
          </button>
          {rubricOpen && (
            <RubricBreakdown
              items={item.rubricBreakdown ?? []}
              needsManualReview={item.needsManualReview}
            />
          )}
        </>
      )}
    </div>
  );
}
