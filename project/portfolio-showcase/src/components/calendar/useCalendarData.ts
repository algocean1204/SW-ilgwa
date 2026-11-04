// StudyCalendar가 쓰는 API 데이터를 불러오는 훅.
// 뷰 컴포넌트는 화면만 그리게 하려고 따로 뺐다.
import { useState, useEffect, useCallback } from 'react';
import { getCalendar, getMissionsToday, getStreak } from '@/apis';
import type { CalendarEventResponse, MissionItem, StreakResponse } from '@/apis';
import { isSameDay } from './MiniCalendar';

// 주간 타임라인의 하루치 슬롯
export interface DaySlot {
  label: string;
  done: boolean;
  event: CalendarEventResponse;
}

// 다가오는 시험 D-day
export interface UpcomingExam {
  name: string;
  date: string;       // "5월 14일" 형식 표시용
  dday: number;       // 오늘 기준 남은 일수
}

// "2026-05-25"를 "5월 25일"로 바꾼다
function formatDateKo(dateStr: string): string {
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

// 오늘 자정 기준 남은 일수를 계산한다 (당일=0, 과거=음수)
function calcDday(dateStr: string, today: Date): number {
  const target = new Date(dateStr);
  target.setHours(0, 0, 0, 0);
  return Math.round((target.getTime() - today.getTime()) / 86_400_000);
}

interface CalendarData {
  // 월별 이벤트 원본. 주간 타임라인을 만들 때 쓴다.
  events: CalendarEventResponse[];
  // 오늘 일정 패널에 쓰는 미션 목록
  missions: MissionItem[];
  // 예정된 EXAM 이벤트 D-day 목록 (오름차순 정렬)
  upcomingExams: UpcomingExam[];
  // 연속 학습 스트릭. API가 실패하면 null이다.
  streak: Pick<StreakResponse, 'currentStreak' | 'longestStreak' | 'totalStudyDays'> | null;
  calendarError: string | null;
  refreshCalendar: () => Promise<void>;
}

// 선택 날짜의 연/월이 바뀔 때마다 getCalendar를 다시 부르고, getMissionsToday는 한 번만 부른다.
// authReady가 false(부트스트랩 전)이면 401이 쌓이지 않게 보호 API를 부르지 않는다.
export function useCalendarData(selectedDate: Date, today: Date, authReady = true): CalendarData {
  const [events, setEvents] = useState<CalendarEventResponse[]>([]);
  const [missions, setMissions] = useState<MissionItem[]>([]);
  const [streak, setStreak] = useState<CalendarData['streak']>(null);
  const [calendarError, setCalendarError] = useState<string | null>(null);
  const selectedYear = selectedDate.getFullYear();
  const selectedMonth = selectedDate.getMonth() + 1;

  const refreshCalendar = useCallback(async () => {
    if (!authReady) return;
    try {
      const res = await getCalendar(selectedYear, selectedMonth);
      setEvents(res.data?.events ?? []);
      setCalendarError(null);
    } catch {
      setEvents([]);
      setCalendarError('캘린더 일정을 불러오지 못했습니다. 서버 상태를 확인한 뒤 다시 시도해 주세요.');
    }
  }, [selectedYear, selectedMonth, authReady]);

  // 선택한 달이 바뀔 때마다 이벤트를 다시 조회한다. authReady 전에는 부르지 않는다.
  useEffect(() => {
    if (!authReady) return;
    void getCalendar(selectedYear, selectedMonth).then((res) => {
      setEvents(res.data?.events ?? []);
      setCalendarError(null);
    }).catch(() => {
      setEvents([]);
      setCalendarError('캘린더 일정을 불러오지 못했습니다. 서버 상태를 확인한 뒤 다시 시도해 주세요.');
    });
  }, [selectedYear, selectedMonth, authReady]);

  // 오늘 미션은 authReady 후 한 번 조회한다
  useEffect(() => {
    if (!authReady) return;
    void getMissionsToday().then((res) => {
      if (res.data?.missions) setMissions(res.data.missions);
    }).catch(() => {});
  }, [authReady]);

  // 스트릭은 authReady 후 한 번 조회하고, 실패하면 null로 둔다
  useEffect(() => {
    if (!authReady) return;
    void getStreak().then((res) => {
      if (res.data) {
        const { currentStreak, longestStreak, totalStudyDays } = res.data;
        setStreak({ currentStreak, longestStreak, totalStudyDays });
      }
    }).catch(() => {});
  }, [authReady]);

  // EXAM 중 scheduled 이벤트를 D-day 오름차순으로 정렬한다
  const upcomingExams: UpcomingExam[] = events
    .filter(e => e.type === 'EXAM' && e.status === 'scheduled')
    .map(e => ({
      name: e.title,
      date: formatDateKo(e.date),
      dday: calcDday(e.date, today),
    }))
    .sort((a, b) => a.dday - b.dday);

  return { events, missions, upcomingExams, streak, calendarError, refreshCalendar };
}

// 주어진 주의 i번째 날(월=0)에 해당하는 이벤트를 DaySlot 배열로 변환한다
export function buildDaySlots(
  events: CalendarEventResponse[],
  cellDate: Date,
): DaySlot[] {
  return events
    .filter(e => isSameDay(new Date(e.date), cellDate))
    .map(e => ({ label: e.title, done: e.status === 'completed', event: e }));
}
