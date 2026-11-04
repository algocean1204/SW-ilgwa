// 클릭 가능한 미니 월간 달력.
// 선택 날짜는 selectedDate로 받고, 보여줄 달은 내부 calendarMonth state로 관리한다.
import { useState, useEffect } from 'react';
import type { Palette } from '@/lib';
import Icon from '@/Icon';

interface MiniCalendarProps {
  selectedDate: Date;
  onSelectDate: (d: Date) => void;
  palette: Palette;
  // 날짜 셀 한 변의 크기(px). 기본 32.
  cellSize?: number;
  hasEvents?: (d: Date) => boolean;
  onEventDateClick?: (d: Date) => void;
}

export function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate();
}

// 해당 주의 월요일 00:00:00을 돌려준다
export function getMonday(d: Date): Date {
  const day = d.getDay(); // 0=일
  const diff = day === 0 ? -6 : 1 - day;
  const result = new Date(d);
  result.setDate(d.getDate() + diff);
  result.setHours(0, 0, 0, 0);
  return result;
}

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

const NAV_BTN: React.CSSProperties = {
  background: 'none', border: 'none', padding: '2px 4px',
  cursor: 'pointer', borderRadius: 4,
  display: 'flex', alignItems: 'center', justifyContent: 'center',
};

function MiniCalendar({
  selectedDate,
  onSelectDate,
  palette,
  cellSize = 32,
  hasEvents,
  onEventDateClick,
}: MiniCalendarProps) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const [calYear, setCalYear] = useState(selectedDate.getFullYear());
  const [calMonth, setCalMonth] = useState(selectedDate.getMonth());
  const [todayBtnHover, setTodayBtnHover] = useState(false);

  // selectedDate 변경 시 달력 뷰가 해당 달로 따라간다
  useEffect(() => {
    setCalYear(selectedDate.getFullYear());
    setCalMonth(selectedDate.getMonth());
  }, [selectedDate]);

  const selectedWeekStart = getMonday(selectedDate);

  const prevMonth = () => {
    if (calMonth === 0) { setCalYear(y => y - 1); setCalMonth(11); }
    else setCalMonth(m => m - 1);
  };
  const nextMonth = () => {
    if (calMonth === 11) { setCalYear(y => y + 1); setCalMonth(0); }
    else setCalMonth(m => m + 1);
  };

  const dates = buildCalendarDates(calYear, calMonth);
  const isNotToday = !isSameDay(selectedDate, today);

  // 폰트 크기는 cellSize에 비례한다. 32일 때 날짜 12, 요일 11이다.
  const dateFontSize = Math.round(cellSize * 0.375);
  const headerFontSize = Math.round(cellSize * 0.344);

  // "오늘" pill 버튼. hover 시 배경을 palette.soft로 바꾼다.
  const todayPillStyle: React.CSSProperties = {
    display: 'flex', alignItems: 'center', gap: 3,
    background: todayBtnHover ? palette.soft : palette.wash,
    border: `1px solid ${palette.soft}`,
    borderRadius: 12,
    padding: '3px 10px',
    cursor: 'pointer',
    fontSize: 10, fontWeight: 700, color: palette.primary,
    transition: 'background .12s',
    marginLeft: 4,
  };

  return (
    <div style={{ background: 'var(--paper)', borderRadius: 14, border: '1px solid var(--line)', padding: '16px 18px' }}>
      {/* 헤더: 이전/다음 달, 오늘로 돌아가기 버튼 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 2, marginBottom: 12 }}>
        <button onClick={prevMonth} style={NAV_BTN} aria-label="이전 달">
          <Icon name="chevron-left" size={14} color="var(--ink-3)" />
        </button>
        <span style={{ flex: 1, textAlign: 'center', fontSize: 13, fontWeight: 700, color: 'var(--ink-3)', letterSpacing: '.06em' }}>
          {calYear}년 {calMonth + 1}월
        </span>
        <button onClick={nextMonth} style={NAV_BTN} aria-label="다음 달">
          <Icon name="chevron-right" size={14} color="var(--ink-3)" />
        </button>
        {/* "오늘" 버튼은 선택 날짜가 오늘이 아닐 때만 보인다 */}
        {isNotToday && (
          <button
            onClick={() => onSelectDate(today)}
            onMouseEnter={() => setTodayBtnHover(true)}
            onMouseLeave={() => setTodayBtnHover(false)}
            style={todayPillStyle}
            aria-label="오늘로 이동"
          >
            <Icon name="chevron-left" size={10} color={palette.primary} strokeWidth={2.2} />
            오늘
          </button>
        )}
      </div>

      {/* 요일 헤더 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 5, textAlign: 'center' }}>
        {['일', '월', '화', '수', '목', '금', '토'].map(d => (
          <div key={d} style={{ fontSize: headerFontSize, fontWeight: 700, color: 'var(--muted)', paddingBottom: 5 }}>{d}</div>
        ))}
      </div>

      {/* 날짜 셀. 오늘, 선택됨, 선택 주, 일반 4가지 상태가 있다 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 5 }}>
        {dates.map((date, i) => {
          const isT = isSameDay(date, today);
          const isSel = isSameDay(date, selectedDate);
          const inWeek = isSameDay(getMonday(date), selectedWeekStart);
          const inMonth = date.getMonth() === calMonth;
          const hasEvent = hasEvents?.(date) ?? false;

          let bg = 'transparent';
          let color = inMonth ? 'var(--ink-3)' : 'var(--muted)';
          let border = 'none';
          if (isT) {
            bg = palette.primary; color = 'var(--paper)';
          } else if (isSel) {
            bg = palette.wash; color = palette.primary; border = `1.5px solid ${palette.soft}`;
          } else if (inWeek && inMonth) {
            bg = palette.wash;
          }

          return (
            <div key={i} onClick={() => {
              onSelectDate(date);
              if (hasEvent) onEventDateClick?.(date);
            }} style={{
              width: cellSize, height: cellSize, borderRadius: Math.round(cellSize * 0.2),
              fontSize: dateFontSize, fontWeight: 700, margin: '0 auto',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              position: 'relative',
              background: bg, color, border,
              cursor: 'pointer', opacity: inMonth ? 1 : 0.35,
              transition: 'background .12s',
            }}>
              {date.getDate()}
              {hasEvent && (
                <span style={{
                  position: 'absolute', bottom: 4, left: '50%', transform: 'translateX(-50%)',
                  width: 4, height: 4, borderRadius: 4,
                  background: isT ? 'var(--paper)' : palette.primary,
                }} />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default MiniCalendar;
