// 리포트 사이드 패널. AI 코멘트, 추천, 요약 수치를 range(주간/월간)에 맞춰 보여준다.
import type { Palette } from '@/lib';
import type { WeekDay } from './types';
import InspectorPanel from '@/components/ui/InspectorPanel';

interface ReportAsideProps {
  palette: Palette;
  week: WeekDay[];
  totalMin: number;
  totalQ: number;
  avgScore: number;
  dailyGoalMinutes: number;
  completedAssignmentCount: number;
  assignmentAvgScore: number;
  range: 'week' | 'month';
}

// 달성률로 코멘트를 만든다. range에 따라 "이번 주"나 "이번 달"을 쓴다.
function getAchievementComment(rate: number, range: 'week' | 'month'): string {
  const period = range === 'week' ? '이번 주' : '이번 달';
  const next = range === 'week' ? '다음 주' : '다음 달';
  if (rate >= 100) return `${period} 목표를 초과 달성했어요! 훌륭합니다.`;
  if (rate >= 80) return `${period} 목표에 거의 도달했어요. 조금만 더 힘내세요!`;
  if (rate >= 50) return '절반 이상 학습했어요. 내일은 조금 더 해볼까요?';
  return `${period}는 학습이 부족했어요. ${next}엔 꾸준히 해봐요!`;
}

// Date.getDay() 순서(0=일~6=토)의 요일명
const DAY_NAMES_ASIDE = ['일', '월', '화', '수', '목', '금', '토'] as const;

// d 필드를 요일명으로 바꾼다. 요일명("목")은 그대로, ISO 날짜("2026-05-28")는 요일명으로 바꾼다.
// 월간 모드는 d에 ISO 날짜를 담아서, 이 변환이 없으면 "2026-05-28요일에"처럼 나온다.
function normalizeDayLabel(d: string): string {
  // 한 글자 요일('일'~'토')은 그대로 둔다
  if (d.length === 1) return d;
  // new Date("YYYY-MM-DD")는 UTC로 읽어 로컬 날짜가 밀릴 수 있어 직접 나눠 읽는다
  const parts = d.split('-');
  if (parts.length === 3) {
    const y = Number(parts[0]);
    const m = Number(parts[1]);
    const day = Number(parts[2]);
    const idx = new Date(y, m - 1, day).getDay();
    return DAY_NAMES_ASIDE[idx] ?? d;
  }
  return d;
}

// 주간 데이터에서 가장 많이, 가장 적게 공부한 날로 코멘트를 만든다
function getDayInsight(week: WeekDay[]): string {
  if (week.length === 0) return '';
  const sorted = [...week].sort((a, b) => b.m - a.m);
  const best = sorted[0]!;
  const worst = sorted[sorted.length - 1]!;
  if (best.m === 0) return '';
  const bestLabel = normalizeDayLabel(best.d);
  const worstLabel = normalizeDayLabel(worst.d);
  if (best.d === worst.d) return `${bestLabel}요일에 ${best.m}분 학습했어요.`;
  return `${bestLabel}요일에 가장 많이(${best.m}분), ${worstLabel}요일에 가장 적게(${worst.m}분) 학습했어요.`;
}

// 데이터로 추천 항목을 만든다. range에 따라 주간·월간 추천이 다르다.
function buildTips(week: WeekDay[], dailyGoalMinutes: number, assignmentAvgScore: number, range: 'week' | 'month'): { title: string; items: string[] } {
  const nextLabel = range === 'week' ? '다음 주 추천' : '다음 달 추천';
  const tips: string[] = [];
  const activeDays = week.filter(d => d.m > 0).length;
  const totalDays = week.length;

  if (range === 'month') {
    if (activeDays === 0) {
      tips.push('매일 10분이라도 학습 습관 만들기');
    } else if (activeDays < totalDays * 0.5) {
      tips.push(`학습한 날이 ${activeDays}일뿐이에요 — 꾸준함이 핵심!`);
    }
    if (dailyGoalMinutes > 0) {
      const goalDays = week.filter(d => d.m >= dailyGoalMinutes).length;
      if (goalDays < activeDays) {
        tips.push(`목표(${dailyGoalMinutes}분) 달성일 늘려보기`);
      }
    }
  } else {
    const lowDays = week.filter(d => d.m < dailyGoalMinutes && d.m > 0);
    if (lowDays.length > 0) {
      tips.push(`${normalizeDayLabel(lowDays[0]!.d)}요일 학습량 늘려보기 (목표: ${dailyGoalMinutes}분)`);
    }
    const zeroDays = week.filter(d => d.m === 0);
    if (zeroDays.length > 0) {
      tips.push(`${zeroDays.map(d => normalizeDayLabel(d.d)).join('·')}요일에도 짧게라도 학습하기`);
    }
  }

  if (assignmentAvgScore > 0 && assignmentAvgScore < 80) {
    tips.push('과제 점수 올리기 — 틀린 문제 복습 추천');
  }
  if (tips.length === 0) {
    tips.push('현재 페이스 유지하기');
  }
  return { title: nextLabel, items: tips };
}

function ReportAside({ palette, week, totalMin, totalQ, avgScore, dailyGoalMinutes, completedAssignmentCount, assignmentAvgScore, range }: ReportAsideProps) {
  // 학습 시간이나 퀴즈가 1건 이상인 날 수
  const activeDays = week.filter(d => d.m > 0 || d.q > 0).length;

  // 목표 대비 달성률(%). range에 따라 7일이나 30일을 기준으로 한다.
  const goalMultiplier = range === 'week' ? 7 : 30;
  const periodGoal = dailyGoalMinutes * goalMultiplier;
  const achievementRate = periodGoal > 0 ? Math.round(totalMin / periodGoal * 100) : 0;

  const summaryItems = [
    { label: '총 학습 시간', value: `${Math.floor(totalMin / 60)}h ${totalMin % 60}m` },
    { label: '완료한 퀴즈',  value: `${totalQ}개` },
    { label: '완료한 과제',  value: `${completedAssignmentCount}개` },
    { label: '평균 정답률',  value: `${avgScore}%` },
    { label: '학습한 날',    value: `${activeDays}일` },
  ];

  const tips = buildTips(week, dailyGoalMinutes, assignmentAvgScore, range);
  const summaryTitle = range === 'week' ? '이번 주 한눈에' : '이번 달 한눈에';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <InspectorPanel title="학습 코멘트">
        <div style={{ fontSize: 14, color: 'var(--ink-2)', lineHeight: 1.65, marginBottom: 14 }}>
          {getAchievementComment(achievementRate, range)}
          {' '}
          {getDayInsight(week)}
        </div>

        <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-tertiary)', letterSpacing: '.08em', textTransform: 'uppercase', marginBottom: 8 }}>
          {tips.title}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {tips.items.map((text) => (
            <div key={text} style={{
              padding: '8px 10px', borderRadius: 8,
              background: palette.wash, border: `1px solid ${palette.soft}`,
              fontSize: 13, fontWeight: 600, color: 'var(--ink-2)',
            }}>
              {text}
            </div>
          ))}
        </div>
      </InspectorPanel>

      {/* 주간/월간 요약 수치. '이번 달/주 한눈에'가 우측의 주요 요약이라 글자를 한 단계 키운다 */}
      <InspectorPanel title={summaryTitle}>
        {summaryItems.map(item => (
          <div key={item.label} style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            padding: '9px 0', borderBottom: '1px solid var(--border-soft)',
          }}>
            <span style={{ fontSize: 13, color: 'var(--text-tertiary)' }}>{item.label}</span>
            <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)' }}>{item.value}</span>
          </div>
        ))}
      </InspectorPanel>
    </div>
  );
}

export { ReportAside };
