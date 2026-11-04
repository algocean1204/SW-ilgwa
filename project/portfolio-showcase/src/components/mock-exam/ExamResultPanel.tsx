// 모의고사 결과 화면. 점수 요약, 문항별 결과, "틀린 문제만 보기" 필터를 보인다.
import { useState } from 'react';
import { NumberCounter } from '@/components/animated';
import SectionFrame from '@/components/ui/SectionFrame';
import type { Palette } from '@/lib';
import type { ExamResultResponse } from '@/apis';
import ExamResultQuestionCard from './ExamResultQuestionCard';
import PartialSourceNotice from './PartialSourceNotice';
import type { GenerationDisplayState } from './generationOutcome';

interface ExamResultPanelProps {
  result: ExamResultResponse;
  palette: Palette;
  /**
   * 자료가 좁아 일부만 생성된 경우 partial. 점수 요약 카드에 배지를 붙인다.
   * 없으면 'ok'로 보고 배지를 달지 않는다.
   */
  generationDisplay?: GenerationDisplayState;
  onRetry: () => void;
  onBackToList: () => void;
}

/**
 * 약점 주제 목록을 정리한다. FastAPI 파싱 결과에 빈 문자열·공백·중복이 섞일 수 있어
 * trim, 빈 값 제거, 중복 제거 순으로 칩 목록을 만든다.
 */
export function normalizeWeakTopics(weakTopics: string[] | null | undefined): string[] {
  if (!weakTopics) return [];
  return Array.from(new Set(weakTopics.map((t) => t.trim()).filter((t) => t.length > 0)));
}

export default function ExamResultPanel({ result, palette, generationDisplay, onRetry, onBackToList }: ExamResultPanelProps) {
  const [wrongOnly, setWrongOnly] = useState(false);

  const correctCount = result.results.filter((r) => r.correct).length;
  const total = result.results.length;
  const manualReviewCount = result.results.filter((r) => r.needsManualReview).length;
  const wrongCount = total - correctCount;

  const visibleResults = wrongOnly
    ? result.results.filter((r) => !r.correct)
    : result.results;

  const weakTopicChips = normalizeWeakTopics(result.weakTopics);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--section-gap)' }}>
      <SectionFrame>
        <div style={{ display: 'flex', alignItems: 'center', gap: 24, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
            <NumberCounter
              to={result.totalScore}
              style={{ fontSize: 56, fontWeight: 800, color: palette.primary, lineHeight: 1, letterSpacing: 0 }}
            />
            <span style={{ fontSize: 18, fontWeight: 700, color: 'var(--text-tertiary)' }}>
              / {result.maxScore}점
            </span>
          </div>
          <div style={{ flex: 1, minWidth: 160 }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
              <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-tertiary)', letterSpacing: '.08em' }}>
                결과
              </span>
              <span style={{
                fontSize: 14,
                fontWeight: 800,
                padding: '3px 12px',
                borderRadius: 8,
                background: result.passed ? palette.wash : 'var(--state-danger-bg)',
                color: result.passed ? palette.primary : 'var(--state-danger)',
                border: `1px solid ${result.passed ? palette.soft : 'var(--state-danger)'}`,
              }}>
                {result.passed ? '합격' : '불합격'}
              </span>
            </div>
            <div style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
              정답 {correctCount} / {total}문항 · {result.percentage}%
              {manualReviewCount > 0 && (
                <span style={{ color: 'var(--honey-ink)', fontWeight: 700 }}>
                  {' '}(수동 검토 {manualReviewCount}개)
                </span>
              )}
            </div>
            {/* "요청 N문항 중 M문항 생성됨" 배지 */}
            {generationDisplay && generationDisplay.kind === 'partial' && (
              <div style={{ marginTop: 8 }}>
                <PartialSourceNotice state={generationDisplay} variant="badge" />
              </div>
            )}
          </div>
        </div>

        {manualReviewCount > 0 && (
          <div
            style={{
              marginTop: 12,
              padding: '10px 14px',
              borderRadius: 9,
              background: 'var(--state-warning-bg)',
              border: '1px solid var(--honey-soft)',
              fontSize: 12,
              fontWeight: 700,
              color: 'var(--honey-ink)',
            }}
            role="status"
            aria-live="polite"
          >
            서술형 문항 {manualReviewCount}개가 수동 검토 중입니다. 최종 점수가 달라질 수 있어요.
          </div>
        )}

        <div style={{ display: 'flex', gap: 8, marginTop: 18 }}>
          <button
            onClick={onRetry}
            className="lift"
            style={{
              padding: '10px 18px',
              borderRadius: 10,
              fontSize: 13,
              fontWeight: 800,
              background: palette.primary,
              color: 'var(--paper)',
              border: 'none',
              boxShadow: 'var(--shadow-md)',
              cursor: 'pointer',
            }}
          >
            다시 풀기
          </button>
          <button
            onClick={onBackToList}
            style={{
              padding: '10px 18px',
              borderRadius: 10,
              fontSize: 13,
              fontWeight: 700,
              background: 'var(--surface-card)',
              border: '1px solid var(--border-default)',
              color: 'var(--text-secondary)',
              cursor: 'pointer',
            }}
          >
            목록으로
          </button>
        </div>
      </SectionFrame>

      {/* AI 총평이 없거나(null) 분석에 실패하면 섹션을 숨긴다 */}
      {result.analysis && (
        <SectionFrame
          title="AI 총평"
          description="채점 결과를 바탕으로 한 맞춤 학습 진단이에요"
        >
          <p style={{
            margin: 0,
            fontSize: 13,
            lineHeight: 1.8,
            color: 'var(--text-secondary)',
            whiteSpace: 'pre-wrap',
          }}>
            {result.analysis}
          </p>
          {/* 정리 후 남은 약점 주제가 없으면 그리지 않는다 */}
          {weakTopicChips.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 12 }}>
              {weakTopicChips.map((topic) => (
                <span
                  key={topic}
                  style={{
                    fontSize: 11,
                    fontWeight: 700,
                    padding: '4px 10px',
                    borderRadius: 999,
                    background: palette.wash,
                    color: palette.primary,
                    border: `1px solid ${palette.soft}`,
                  }}
                >
                  {topic}
                </span>
              ))}
            </div>
          )}
        </SectionFrame>
      )}

      <SectionFrame
        title="문항별 결과"
        description="각 문항의 점수와 채점 피드백을 확인하세요"
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 12,
          flexWrap: 'wrap',
          gap: 8,
        }}>
          <span style={{ fontSize: 12, color: 'var(--text-tertiary)', fontWeight: 600 }}>
            {wrongOnly
              ? `틀린 문제 ${wrongCount}개 표시 중`
              : `전체 ${total}문항 표시 중`}
          </span>
          <button
            onClick={() => setWrongOnly((v) => !v)}
            aria-pressed={wrongOnly}
            style={{
              padding: '6px 14px',
              borderRadius: 8,
              fontSize: 12,
              fontWeight: 700,
              cursor: 'pointer',
              border: `1px solid ${wrongOnly ? 'var(--state-danger)' : 'var(--border-default)'}`,
              background: wrongOnly ? 'var(--state-danger-bg)' : 'var(--surface-card)',
              color: wrongOnly ? 'var(--coral-ink-deep)' : 'var(--text-secondary)',
              transition: 'background 0.15s, color 0.15s, border-color 0.15s',
            }}
          >
            {wrongOnly ? '전체 보기' : '틀린 문제만 보기'}
          </button>
        </div>

        {visibleResults.length === 0 && (
          <div style={{
            padding: '20px 0',
            textAlign: 'center',
            fontSize: 13,
            color: 'var(--text-tertiary)',
          }}>
            {wrongOnly ? '틀린 문제가 없습니다.' : '문항이 없습니다.'}
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {visibleResults.map((item, idx) => (
            <ExamResultQuestionCard
              key={item.questionId}
              item={item}
              index={idx}
              palette={palette}
            />
          ))}
        </div>
      </SectionFrame>
    </div>
  );
}
