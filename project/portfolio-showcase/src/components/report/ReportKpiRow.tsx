// KPI 카드(학습 시간 | 학습 활동과 정답률 링). week-summary 서브뷰에서 가로 2열로 쓴다.
import React from 'react';
import type { Palette } from '@/lib';
import { NumberCounter } from '@/components/animated';

interface ReportKpiRowProps {
  palette: Palette;
  totalMin: number;
  totalQ: number;
  avgScore: number;
  completedAssignments: number;
  assignmentAvgScore: number;
}

function ProgressRing({ percent, palette }: { percent: number; palette: Palette }) {
  const size = 64;
  const strokeWidth = 6;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (percent / 100) * circumference;

  return (
    <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }}>
      <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke={palette.wash} strokeWidth={strokeWidth} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={palette.primary}
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
        />
      </svg>
      <div style={{
        position: 'absolute', inset: 0,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: 13, fontWeight: 800, color: 'var(--ink)',
      }}>
        {Math.round(percent)}%
      </div>
    </div>
  );
}

const cardStyle: React.CSSProperties = {
  padding: '18px 22px',
  borderRadius: 14,
  background: 'var(--paper)',
  border: '1px solid var(--line)',
};

const labelStyle: React.CSSProperties = {
  fontSize: 11, fontWeight: 700, color: 'var(--muted)', letterSpacing: '.08em',
};

function ReportKpiRow({ palette, totalMin, totalQ, avgScore, completedAssignments }: ReportKpiRowProps) {
  // API 응답에 숫자 필드가 빠지면 Math.floor(undefined)가 NaN이 되므로 ?? 0으로 막는다
  const safeMin = totalMin ?? 0;
  const safeQ = totalQ ?? 0;
  const safeScore = avgScore ?? 0;
  const safeAssignments = completedAssignments ?? 0;
  const hours = Math.floor(safeMin / 60);
  const mins = safeMin % 60;

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.6fr', gap: 14 }}>
      {/* 학습 시간 */}
      <div style={{ ...cardStyle, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
        <div style={labelStyle}>학습 시간</div>
        <div style={{ fontSize: 28, fontWeight: 800, color: 'var(--ink)', letterSpacing: '-.02em', marginTop: 6 }}>
          <NumberCounter to={hours} format={(n) => String(Math.round(n))} />h{' '}
          <NumberCounter to={mins} format={(n) => String(Math.round(n))} />m
        </div>
      </div>

      {/* 학습 활동과 정답률 링 */}
      <div style={{ ...cardStyle, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <div style={labelStyle}>학습 활동</div>
          <div style={{ display: 'flex', gap: 18, marginTop: 8, alignItems: 'baseline' }}>
            <div>
              <span style={{ fontSize: 24, fontWeight: 800, color: 'var(--ink)' }}>
                <NumberCounter to={safeQ} format={(n) => String(Math.round(n))} />
              </span>
              <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', marginLeft: 3 }}>퀴즈</span>
            </div>
            <div style={{ width: 1, height: 20, background: 'var(--line)' }} />
            <div>
              <span style={{ fontSize: 24, fontWeight: 800, color: 'var(--ink)' }}>
                <NumberCounter to={safeAssignments} format={(n) => String(Math.round(n))} />
              </span>
              <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', marginLeft: 3 }}>과제</span>
            </div>
          </div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
          <ProgressRing percent={safeScore} palette={palette} />
          <span style={{ fontSize: 10, fontWeight: 700, color: 'var(--muted)' }}>정답률</span>
        </div>
      </div>
    </div>
  );
}

export { ReportKpiRow };
