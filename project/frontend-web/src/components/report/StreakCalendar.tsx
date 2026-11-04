// 월간 학습 달력. 목표 대비 학습량을 셀 배경 진하기로 보여준다.
// 학습 데이터가 없어도 현재 월 달력은 그린다.
import { useState, useMemo } from 'react';
import type { Palette } from '@/lib';
import type { WeekDay } from './types';
import Icon from '@/Icon';

interface ReportCalendarProps {
  palette: Palette;
  days: WeekDay[];
  dailyGoalMinutes: number;
}

const DAY_HEADERS = ['일', '월', '화', '수', '목', '금', '토'] as const;

// 해당 연/월의 달력 날짜를 일요일 시작 7×N 그리드로 생성한다 (앞뒤 달 패딩 포함)
function buildCalendarDates(year: number, month: number): Date[] {
  const firstDay = new Date(year, month, 1);
  const startOffset = firstDay.getDay();
  const dates: Date[] = [];
  for (let i = startOffset - 1; i >= 0; i--) {
    dates.push(new Date(year, month, -i));
  }
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  for (let d = 1; d <= daysInMonth; d++) {
    dates.push(new Date(year, month, d));
  }
  const rem = dates.length % 7;
  if (rem !== 0) {
    for (let i = 1; i <= 7 - rem; i++) {
      dates.push(new Date(year, month + 1, i));
    }
  }
  return dates;
}

function toDateKey(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

// 목표 대비 달성률로 셀 배경색을 정한다
function getCellBg(minutes: number, goal: number, palette: Palette, inMonth: boolean): string {
  if (minutes === 0) return inMonth ? 'var(--surface-subtle)' : 'transparent';
  const ratio = minutes / goal;
  if (ratio < 0.5) return `${palette.wash}cc`;
  if (ratio < 1) return palette.wash;
  return palette.soft;
}

const NAV_BTN: React.CSSProperties = {
  background: 'none', border: 'none', padding: '2px 4px',
  cursor: 'pointer', borderRadius: 4,
  display: 'flex', alignItems: 'center', justifyContent: 'center',
};

function ReportCalendar({ palette, days, dailyGoalMinutes }: ReportCalendarProps) {
  const today = new Date();
  const [calYear, setCalYear] = useState(today.getFullYear());
  const [calMonth, setCalMonth] = useState(today.getMonth());

  const minuteMap = useMemo(() => {
    const map = new Map<string, number>();
    for (const entry of days) {
      map.set(entry.d, entry.m);
    }
    return map;
  }, [days]);

  const dates = useMemo(() => buildCalendarDates(calYear, calMonth), [calYear, calMonth]);

  // 현재 보고 있는 달의 학습 통계
  const { studyDays, totalMin } = useMemo(() => {
    let sd = 0;
    let tm = 0;
    for (const date of dates) {
      if (date.getMonth() !== calMonth) continue;
      const m = minuteMap.get(toDateKey(date)) ?? 0;
      if (m > 0) { sd++; tm += m; }
    }
    return { studyDays: sd, totalMin: tm };
  }, [dates, calMonth, minuteMap]);

  const todayKey = toDateKey(today);

  const prevMonth = () => {
    if (calMonth === 0) { setCalYear(y => y - 1); setCalMonth(11); }
    else setCalMonth(m => m - 1);
  };
  const nextMonth = () => {
    if (calMonth === 11) { setCalYear(y => y + 1); setCalMonth(0); }
    else setCalMonth(m => m + 1);
  };

  return (
    <div style={{ padding: '12px 14px' }}>
      {/* 헤더: 월 이동과 학습 요약(한 줄) */}
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <button onClick={prevMonth} style={NAV_BTN} aria-label="이전 달">
            <Icon name="chevron-left" size={13} color="var(--ink-3)" />
          </button>
          <span style={{
            fontSize: 11, fontWeight: 700, minWidth: 72, textAlign: 'center',
            color: 'var(--muted)', letterSpacing: '.06em',
          }}>
            {calYear}년 {calMonth + 1}월
          </span>
          <button onClick={nextMonth} style={NAV_BTN} aria-label="다음 달">
            <Icon name="chevron-right" size={13} color="var(--ink-3)" />
          </button>
        </div>
        <div style={{ marginLeft: 6 }}>
          <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink)' }}>
            {studyDays}일 학습 · 총 {totalMin}분
          </span>
        </div>
        <div style={{ flex: 1 }} />
        {/* 범례 */}
        <div style={{ display: 'flex', gap: 3, alignItems: 'center', fontSize: 10, color: 'var(--ink-3)' }}>
          <div style={{ width: 10, height: 10, borderRadius: 2, background: 'var(--surface-subtle)' }} />
          <div style={{ width: 10, height: 10, borderRadius: 2, background: `${palette.wash}cc` }} />
          <div style={{ width: 10, height: 10, borderRadius: 2, background: palette.wash }} />
          <div style={{ width: 10, height: 10, borderRadius: 2, background: palette.soft }} />
          <span>목표</span>
        </div>
      </div>

      {/* 요일 헤더 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 4, marginBottom: 4 }}>
        {DAY_HEADERS.map((label) => (
          <div key={label} style={{
            textAlign: 'center', fontSize: 11, fontWeight: 700,
            color: 'var(--ink-3)', padding: '2px 0',
          }}>
            {label}
          </div>
        ))}
      </div>

      {/* 달력 그리드 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 4 }}>
        {dates.map((date, i) => {
          const inMonth = date.getMonth() === calMonth;
          const key = toDateKey(date);
          const minutes = minuteMap.get(key) ?? 0;
          const isToday = key === todayKey;

          return (
            <div
              key={i}
              style={{
                aspectRatio: '1 / 0.48',
                borderRadius: 7,
                background: getCellBg(minutes, dailyGoalMinutes, palette, inMonth),
                border: isToday ? `2px solid ${palette.primary}` : '2px solid transparent',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 1,
                opacity: inMonth ? 1 : 0.35,
                transition: 'background .2s',
              }}
            >
              <span style={{
                fontSize: 11, fontWeight: 600,
                color: minutes >= dailyGoalMinutes && minutes > 0
                  ? palette.primary
                  : 'var(--ink-2)',
              }}>
                {date.getDate()}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export { ReportCalendar };
