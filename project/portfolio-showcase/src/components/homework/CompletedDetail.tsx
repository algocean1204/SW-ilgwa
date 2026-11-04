// 채점 완료(completed) 과제 상세 패널. GradeResponse로 점수 다이얼과 피드백을 보여준다.
import { formatDateTime } from '@/lib';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';

// 피드백의 한국어 섹션 라벨 앞에 빈 줄을 넣는다.
// 라벨이 없으면 원문 그대로 두고, 줄바꿈이 이미 있으면 더 넣지 않는다.
const FEEDBACK_LABEL_RE = /((?:^|\n)[ \t]*)(잘한\s*점|부족한\s*점|개선\s*방법|개선할\s*점)([ \t]*[:：])/gm;

function formatFeedback(text: string): string {
  if (!text) return text;
  // 라벨 앞에 줄바꿈이 없거나 하나뿐이면 두 줄바꿈으로 보강한다
  const result = text.replace(FEEDBACK_LABEL_RE, (_match, leading: string, label: string, colon: string) => {
    // 문자열 맨 앞(leading이 빈 문자열)이면 줄바꿈 삽입 안 함
    const newLeading = leading.trimStart() === '' && leading.length === 0 ? '' : '\n\n';
    return `${newLeading}${label}${colon}`;
  });
  // 3개 이상 이어진 줄바꿈은 2개로 줄인다
  return result.replace(/\n{3,}/g, '\n\n');
}

export interface CompletedDetailProps {
  title: string;
  /** 만점 기준 점수 */
  maxScore: number;
  /** 획득 점수. null이면 아직 점수가 없다. */
  score: number | null;
  /** AI 피드백 텍스트 */
  feedback: string;
  /** 제출 시각 (ISO 문자열) */
  submittedAt: string;
  /** 채점 완료 시각 (ISO 문자열) */
  gradedAt: string;
  palette: Palette;
  setRoute: RouteSetter;
}

/** 채점 완료 과제 상세. 점수 다이얼, 피드백, 이어서 학습 버튼을 보여준다. */
export default function CompletedDetail({ title, maxScore, score, feedback, submittedAt, gradedAt, palette, setRoute }: CompletedDetailProps) {
  const pct = score != null && maxScore > 0 ? Math.round((score / maxScore) * 100) : null;
  const radius = 56;
  const circ = 2 * Math.PI * radius;
  const offset = pct != null ? circ * (1 - pct / 100) : circ;
  // 점수 구간별 강조 색: 우수(palette), 양호(노랑), 미흡(코랄)
  const tone = pct == null
    ? 'var(--ink-3)'
    : pct >= 85 ? palette.primary
    : pct >= 70 ? 'var(--yellow-primary)'
    : 'var(--coral-primary)';

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 22, marginBottom: 22 }}>
        {/* 점수 다이얼(SVG 원형) */}
        <div style={{ position: 'relative', width: 140, height: 140, flexShrink: 0 }}>
          <svg width="140" height="140" viewBox="0 0 140 140">
            <circle cx="70" cy="70" r={radius} fill="none" stroke="var(--sand-2)" strokeWidth="10" />
            <circle cx="70" cy="70" r={radius} fill="none" stroke={tone} strokeWidth="10"
              strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={offset}
              transform="rotate(-90 70 70)" style={{ transition: 'stroke-dashoffset 1s cubic-bezier(.2,.7,.2,1)' }} />
          </svg>
          <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center' }}>
            <div style={{ textAlign: 'center' }}>
              {pct != null ? (
                <>
                  <div style={{ fontSize: 32, fontWeight: 800, color: tone, letterSpacing: '-.03em', lineHeight: 1 }}>{score}</div>
                  <div style={{ fontSize: 12, color: 'var(--ink-3)', fontWeight: 700, marginTop: 3 }}>/ {maxScore}점</div>
                </>
              ) : (
                <div style={{ fontSize: 12, color: 'var(--ink-3)', fontWeight: 700 }}>점수 없음</div>
              )}
            </div>
          </div>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <h2 style={{ margin: '0 0 6px', fontSize: 22, fontWeight: 800, letterSpacing: '-.02em' }}>{title}</h2>
          <div style={{ display: 'flex', gap: 12, marginTop: 6, fontSize: 12, color: 'var(--ink-3)' }}>
            {submittedAt && <span>제출 {formatDateTime(submittedAt)}</span>}
            {gradedAt && <><span style={{ opacity: .3 }}>·</span><span>채점 {formatDateTime(gradedAt)}</span></>}
          </div>
          {feedback && (
            <div style={{
              marginTop: 14, padding: '12px 14px', borderRadius: 10,
              background: palette.wash, border: `1px solid ${palette.soft}`,
              fontSize: 13, lineHeight: 1.6, color: 'var(--ink-2)',
              // pre-wrap으로 \n을 줄바꿈으로 보여준다
              whiteSpace: 'pre-wrap',
            }}>
              <b style={{ color: palette.primary }}>피드백 · </b>{formatFeedback(feedback)}
            </div>
          )}
        </div>
      </div>

      {/* 액션 버튼 */}
      <div style={{ display: 'flex', gap: 8, paddingTop: 18, borderTop: '1px solid var(--line)' }}>
        <button onClick={() => setRoute('learning')} className="lift" style={{
          padding: '11px 18px', borderRadius: 10, fontSize: 13, fontWeight: 700,
          background: 'var(--surface)', border: '1px solid var(--line)', color: 'var(--ink)',
        }}>이어서 학습</button>
      </div>
    </div>
  );
}
