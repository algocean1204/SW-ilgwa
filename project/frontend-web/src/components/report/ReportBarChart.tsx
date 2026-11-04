// 요일별 학습 시간 막대 차트 패널. 막대는 SVG 없이 div로 그린다.
import type { Palette } from '@/lib';
import type { WeekDay } from './types';
import Icon from '@/Icon';

interface LegendProps {
  c: string;
  t: string;
}

interface ReportBarChartProps {
  palette: Palette;
  // 7일 학습 데이터
  week: WeekDay[];
  totalMin: number;
  dailyGoalMinutes: number;
}

const CHART_HEIGHT = 200;
const GOAL_LINE_TOP_GUTTER = 4;
const GOAL_LINE_BOTTOM_GUTTER = 34;
const GOAL_LINE_USABLE_HEIGHT = CHART_HEIGHT - GOAL_LINE_TOP_GUTTER - GOAL_LINE_BOTTOM_GUTTER;

// 범례 아이템: 색 점과 텍스트
function Legend({ c, t }: LegendProps) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <span style={{ width: 10, height: 10, borderRadius: 3, background: c }} />
      {t}
    </span>
  );
}

function ReportBarChart({ palette, week, totalMin, dailyGoalMinutes }: ReportBarChartProps) {
  return (
    <div style={{ padding: '16px 20px' }}>
      {/* 차트 헤더: 제목, 평균, 범례 */}
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--muted)', letterSpacing: '.08em', textTransform: 'uppercase' }}>
            요일별 학습 시간
          </div>
          <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--ink)', marginTop: 3 }}>
            평균 {Math.round(totalMin / 7)}분 · 목표 {dailyGoalMinutes}분
          </div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={{ display: 'flex', gap: 14, fontSize: 12, color: 'var(--ink-3)' }}>
          <Legend c={palette.primary} t="학습" />
          <Legend c={palette.soft} t="목표선" />
        </div>
      </div>

      {/* 막대 차트. 그라데이션 없이 primary 단색 */}
      <div style={{
        display: 'flex', alignItems: 'flex-end', gap: 12,
        height: CHART_HEIGHT, paddingBottom: 8,
        borderBottom: '1px solid var(--line-2)', position: 'relative',
      }}>
        {/* 목표 기준선. 목표값이 낮아도 요일 축을 덮지 않게 하단 여백을 둔다 */}
        {dailyGoalMinutes > 0 && (
          <div style={{
            position: 'absolute', left: 0, right: 0,
            top: Math.max(
              GOAL_LINE_TOP_GUTTER,
              Math.min(
                GOAL_LINE_TOP_GUTTER + GOAL_LINE_USABLE_HEIGHT - (dailyGoalMinutes / 60) * GOAL_LINE_USABLE_HEIGHT,
                CHART_HEIGHT - GOAL_LINE_BOTTOM_GUTTER,
              ),
            ),
            borderTop: `2px dashed ${palette.soft}`, pointerEvents: 'none',
          }}>
            <span style={{
              position: 'absolute', right: 0, top: -8, fontSize: 12, color: palette.primary,
              padding: '1px 6px', background: palette.wash, borderRadius: 4, fontWeight: 700,
            }}>{dailyGoalMinutes}분</span>
          </div>
        )}

        {week.map((d, i) => {
          const h = d.m === 0 ? 4 : (d.m / 60) * CHART_HEIGHT;
          // 정답률 80% 이상이면 위에 체크 배지를 단다
          const ok = d.r >= 80;
          return (
            <div key={d.d} style={{
              flex: 1, display: 'flex', flexDirection: 'column',
              alignItems: 'center', gap: 6, height: '100%', justifyContent: 'flex-end',
            }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)' }}>
                {d.m > 0 ? `${d.m}m` : '—'}
              </div>
              <div style={{
                width: '100%', height: h, borderRadius: '8px 8px 4px 4px',
                background: d.m === 0 ? 'var(--sand-2)' : palette.primary,
                position: 'relative',
                boxShadow: d.m > 0 ? `0 4px 12px -4px ${palette.primary}` : 'none',
                // 막대가 60ms 간격으로 차례로 올라온다
                transition: `height .8s cubic-bezier(.2,.9,.35,1) ${i * 60}ms`,
              }}>
                {ok && (
                  <div style={{
                    position: 'absolute', top: -8, left: '50%', transform: 'translateX(-50%)',
                    width: 14, height: 14, borderRadius: '50%', background: palette.primary,
                    color: 'var(--paper)', fontSize: 12, fontWeight: 800, display: 'grid', placeItems: 'center',
                    border: `2px solid var(--paper)`,
                  }}><Icon name="check" size={10} color="var(--paper)" /></div>
                )}
              </div>
              <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-3)' }}>{d.d}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export { ReportBarChart };
