// 학습 캘린더 페이지. 상단 2열(미니 달력, 오늘 일정과 다가오는 시험)과 하단 주간 타임라인으로 구성된다.
import { useCallback, useMemo, useState } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import type { AppState, RouteSetter } from '@/shared/types';
import MiniCalendar, { isSameDay, getMonday } from '@/components/calendar/MiniCalendar';
import {
  DAY_LABELS, TimeSlot, weekRangeLabel,
  UpcomingExamsPanel, TodaySchedulePanel,
} from '@/components/calendar/CalendarSlots';
import { useCalendarData, buildDaySlots } from '@/components/calendar/useCalendarData';
import { CalendarEventDialog } from '@/components/calendar/CalendarEventDialog';
import { createCalendarEvent, updateCalendarEvent } from '@/apis';
import type {
  CalendarEventResponse,
  CreateCalendarEventRequest,
  UpdateCalendarEventRequest,
} from '@/apis';
import Icon from '@/Icon';

interface StudyCalendarProps { setRoute: RouteSetter; appState: AppState; }

function dateInputValue(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function eventDateKey(event: CalendarEventResponse): string {
  return event.date.slice(0, 10);
}

function StudyCalendar({ setRoute, appState }: StudyCalendarProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const [selectedDate, setSelectedDate] = useState<Date>(today);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState<'create' | 'edit'>('create');
  const [editingEvent, setEditingEvent] = useState<CalendarEventResponse | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const viewWeekStart = getMonday(selectedDate);
  const isCurrentWeek = isSameDay(viewWeekStart, getMonday(today));

  // 캘린더 이벤트, 미션, 시험 D-day, 스트릭을 가져온다.
  // authReady를 넘겨 부트스트랩 전 호출로 401이 쌓이지 않게 한다.
  const { events, missions, upcomingExams, streak, calendarError, refreshCalendar } = useCalendarData(selectedDate, today, appState.authReady);
  const activeCourseId = appState.activeCourseId;
  const selectedDateValue = dateInputValue(selectedDate);
  const eventsByDate = useMemo(() => {
    const map = new Map<string, CalendarEventResponse[]>();
    for (const event of events) {
      const key = eventDateKey(event);
      map.set(key, [...(map.get(key) ?? []), event]);
    }
    return map;
  }, [events]);

  const openCreateDialog = useCallback((date = selectedDate) => {
    setSelectedDate(date);
    setDialogMode('create');
    setEditingEvent(null);
    setActionError(null);
    setDialogOpen(true);
  }, [selectedDate]);

  const openEditDialog = useCallback((event: CalendarEventResponse) => {
    setDialogMode('edit');
    setEditingEvent(event);
    setActionError(null);
    setDialogOpen(true);
  }, []);

  const hasEventsOnDate = useCallback((date: Date) => {
    return (eventsByDate.get(dateInputValue(date))?.length ?? 0) > 0;
  }, [eventsByDate]);

  const openFirstEventOnDate = useCallback((date: Date) => {
    const first = eventsByDate.get(dateInputValue(date))?.[0];
    if (first) openEditDialog(first);
  }, [eventsByDate, openEditDialog]);

  const handleCreate = useCallback(async (body: CreateCalendarEventRequest) => {
    setActionError(null);
    const res = await createCalendarEvent(body);
    if (!res.data) throw new Error('Calendar event response is empty');
    await refreshCalendar();
    setDialogOpen(false);
  }, [refreshCalendar]);

  const handleUpdate = useCallback(async (eventId: string, body: UpdateCalendarEventRequest) => {
    setActionError(null);
    const res = await updateCalendarEvent(eventId, body);
    if (!res.data) throw new Error('Calendar event response is empty');
    await refreshCalendar();
    setDialogOpen(false);
  }, [refreshCalendar]);

  return (
    <div style={{ maxWidth: 'var(--page-max-w)', margin: '0 auto', padding: 'var(--page-py) var(--page-px) 48px' }}>
      {/* 페이지 헤더 */}
      <header style={{ display: 'flex', alignItems: 'flex-end', gap: 18, marginBottom: 'var(--section-gap)' }}>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: palette.primary, letterSpacing: '.1em', textTransform: 'uppercase' }}>STUDY CALENDAR</div>
          <h1 style={{ margin: '6px 0 0', fontSize: 32, fontWeight: 800, letterSpacing: '-.03em' }}>캘린더</h1>
          <div style={{ marginTop: 5, fontSize: 13, color: 'var(--ink-3)', lineHeight: 1.5, maxWidth: 600 }}>
            오늘 학습 현황과 다가오는 시험 일정을 한 눈에 확인하세요.
          </div>
        </div>
        <button onClick={() => openCreateDialog()} className="lift" style={{
          height: 36, padding: '0 13px', borderRadius: 9,
          background: palette.primary, color: 'var(--paper)',
          border: 'none', fontSize: 12, fontWeight: 800,
          display: 'flex', alignItems: 'center', gap: 6,
        }}>
          <Icon name="plus" size={14} color="var(--paper)" />
          일정 추가
        </button>
        {/* 스트릭 배지는 API 응답이 성공했을 때만 보인다 */}
        {streak && (
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexShrink: 0 }}>
            {/* 현재 스트릭 강조 배지 */}
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 4,
              padding: '5px 12px', borderRadius: 999,
              fontSize: 13, fontWeight: 700,
              background: palette.wash,
              color: palette.primary,
              border: `1px solid ${palette.soft}`,
            }}>
              <Icon name="flame" size={13} /> {streak.currentStreak}일 연속
            </span>
            {/* 최장 스트릭: 중립 배지 */}
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 4,
              padding: '5px 12px', borderRadius: 999,
              fontSize: 12, fontWeight: 600,
              background: 'var(--bg)',
              color: 'var(--ink-2)',
              border: '1px solid var(--line)',
            }}>
              최장 {streak.longestStreak}일
            </span>
          </div>
        )}
      </header>

      {(calendarError || actionError) && (
        <div style={{
          marginBottom: 12, padding: '10px 12px',
          borderRadius: 10, border: '1px solid var(--danger)',
          background: 'var(--coral-wash)', color: 'var(--coral-ink-deep)',
          fontSize: 12, fontWeight: 700,
        }}>
          {actionError ?? calendarError}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--section-gap)' }}>

        {/* 상단 2열: 미니 달력, 오늘 일정과 다가오는 시험 */}
        <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 16, alignItems: 'flex-start' }}>

          {/* 미니 달력. 날짜를 고르면 주간 뷰가 따라간다 */}
          <div id="monthly-calendar">
            {/* 카드 안 빈 공간을 줄이고 날짜를 읽기 쉽게 cellSize를 46으로 둔다 */}
            <MiniCalendar
              selectedDate={selectedDate}
              onSelectDate={setSelectedDate}
              palette={palette}
              cellSize={46}
              hasEvents={hasEventsOnDate}
              onEventDateClick={openFirstEventOnDate}
            />
          </div>

          {/* 당일 정보가 먼저 보이게 오늘 일정을 위, 다가오는 시험을 아래에 둔다 */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <TodaySchedulePanel missions={missions} palette={palette} setRoute={setRoute} />
            <UpcomingExamsPanel exams={upcomingExams} palette={palette} />
          </div>
        </div>

        {/* 하단 전체 너비: 이번 주 타임라인 */}
        <div id="weekly-timeline" style={{ background: 'var(--paper)', borderRadius: 14, border: '1px solid var(--line)', boxShadow: 'var(--shadow-md)', overflow: 'hidden' }}>
          <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--line-2)' }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-3)' }}>
              {isCurrentWeek ? '이번 주 타임라인' : `${weekRangeLabel(viewWeekStart)} 타임라인`}
            </div>
            <div style={{ fontSize: 11, color: 'var(--muted)', marginTop: 2 }}>완료=채워진 배지 · 미완료=실선</div>
          </div>
          <div style={{ padding: 14, display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 8 }}>
            {DAY_LABELS.map((day, i) => {
              const cellDate = new Date(viewWeekStart);
              cellDate.setDate(viewWeekStart.getDate() + i);
              const isToday = isCurrentWeek && isSameDay(cellDate, today);
              const isSelected = isSameDay(cellDate, selectedDate) && !isToday;
              const daySlots = buildDaySlots(events, cellDate);
              return (
                <div key={day} style={{
                  borderRadius: 10, padding: '10px 8px',
                  background: isToday || isSelected ? palette.wash : 'var(--bg)',
                  border: isToday
                    ? `1.5px solid ${palette.primary}`
                    : isSelected ? `1.5px solid ${palette.soft}` : '1px solid var(--line-2)',
                }}>
                  <div style={{ fontSize: 12, fontWeight: 800, textAlign: 'center', marginBottom: 8,
                    color: isToday || isSelected ? palette.primary : 'var(--ink-2)' }}>
                    {day}
                    {isToday
                      ? <span style={{ display: 'block', fontSize: 9, fontWeight: 700, color: palette.primary }}>오늘</span>
                      : <span style={{ display: 'block', fontSize: 9, fontWeight: 600, color: 'var(--ink-3)' }}>{cellDate.getDate()}</span>
                    }
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    {daySlots.map(s => (
                      <TimeSlot
                        key={s.event.id}
                        label={s.label}
                        done={s.done}
                        primary={palette.primary}
                        onClick={() => openEditDialog(s.event)}
                      />
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

      </div>
      <CalendarEventDialog
        open={dialogOpen}
        mode={dialogMode}
        event={editingEvent}
        defaultDate={selectedDateValue}
        activeCourseId={activeCourseId}
        palette={palette}
        onOpenChange={setDialogOpen}
        onCreate={async (body) => {
          try {
            await handleCreate(body);
          } catch {
            setActionError('일정을 추가하지 못했습니다. 입력값과 서버 상태를 확인해 주세요.');
            throw new Error('create calendar event failed');
          }
        }}
        onUpdate={async (eventId, body) => {
          try {
            await handleUpdate(eventId, body);
          } catch {
            setActionError('일정을 수정하지 못했습니다. 입력값과 서버 상태를 확인해 주세요.');
            throw new Error('update calendar event failed');
          }
        }}
      />
    </div>
  );
}

export default StudyCalendar;
