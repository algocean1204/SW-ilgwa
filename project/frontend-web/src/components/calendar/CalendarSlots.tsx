// StudyCalendar 전용 프레젠테이션 컴포넌트 모음. 데이터는 useCalendarData 훅이 불러온다.
import type { RouteSetter } from '@/shared/types';
import type { MissionItem } from '@/apis';
import type { UpcomingExam } from './useCalendarData';

// 요일 레이블. 월요일이 0이다.
export const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'] as const;

// 주간 타임라인 헤더용 날짜 범위. "5월 19일 ~ 25일" 형식이다.
export function weekRangeLabel(weekStart: Date): string {
  const end = new Date(weekStart);
  end.setDate(weekStart.getDate() + 6);
  if (weekStart.getMonth() === end.getMonth()) {
    return `${weekStart.getMonth() + 1}월 ${weekStart.getDate()}일 ~ ${end.getDate()}일`;
  }
  return `${weekStart.getMonth() + 1}월 ${weekStart.getDate()}일 ~ ${end.getMonth() + 1}월 ${end.getDate()}일`;
}

// 달성률 원형 링. 카드 우측을 채우도록 120px, 반지름 48로 그린다.
export function ProgressRing({ pct, primary }: { pct: number; primary: string }) {
  const r = 48;
  const circ = 2 * Math.PI * r;
  return (
    <svg width={120} height={120} viewBox="0 0 120 120">
      <circle cx={60} cy={60} r={r} fill="none" stroke="var(--sand)" strokeWidth={10} />
      {/* 진행 링. 12시 방향에서 시계 방향으로 채워진다 */}
      <circle cx={60} cy={60} r={r} fill="none" stroke={primary} strokeWidth={10}
        strokeDasharray={`${pct / 100 * circ} ${circ}`} strokeLinecap="round"
        transform="rotate(-90 60 60)" />
      <text x={60} y={66} textAnchor="middle" fontSize={24} fontWeight={800} fill={primary}>
        {pct}%
      </text>
    </svg>
  );
}

interface TimeSlotProps {
  label: string;
  done: boolean;
  primary: string;
  onClick?: () => void;
}

// 학습 슬롯 배지. 완료면 채움, 미완료면 실선 테두리로 그린다.
export function TimeSlot({ label, done, primary, onClick }: TimeSlotProps) {
  const clickable = typeof onClick === 'function';
  return (
    <button type="button" onClick={onClick} style={{
      width: '100%',
      textAlign: 'left',
      padding: '6px 8px',
      borderRadius: 8,
      fontSize: 10.5,
      fontWeight: done ? 800 : 700,
      lineHeight: 1.35,
      background: done ? primary : 'var(--paper)',
      color: done ? 'var(--paper)' : 'var(--ink-2)',
      border: done ? 'none' : '1px solid var(--line)',
      boxShadow: done ? 'none' : '0 1px 0 rgba(0,0,0,0.03)',
      cursor: clickable ? 'pointer' : 'default',
    }}>{label}</button>
  );
}

interface UpcomingExamsPanelProps {
  exams: UpcomingExam[];
  // 첫 번째 항목 강조색
  palette: { primary: string; wash: string };
}

// 다가오는 시험 D-day 카드를 가로로 늘어놓는 패널
export function UpcomingExamsPanel({ exams, palette }: UpcomingExamsPanelProps) {
  return (
    <div id="deadline-schedule" style={{ background: 'var(--paper)', borderRadius: 14, border: '1px solid var(--line)', padding: '14px 16px' }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', letterSpacing: '.1em', textTransform: 'uppercase', marginBottom: 10 }}>
        다가오는 시험
      </div>
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        {exams.length === 0 ? (
          <div style={{ fontSize: 13, color: 'var(--ink-3)', padding: '4px 0' }}>예정된 시험이 없어요</div>
        ) : exams.map((u, i) => (
          <div key={u.name} style={{
            flex: '1 1 0', minWidth: 130, padding: '10px 14px', borderRadius: 10,
            display: 'flex', alignItems: 'center', gap: 10,
            background: i === 0 ? palette.wash : 'var(--bg)',
            border: `1px solid ${i === 0 ? palette.primary : 'var(--line)'}`,
          }}>
            {/* D-day 숫자 박스. 첫 번째 시험은 primary 배경으로 강조한다 */}
            <div style={{
              width: 46, height: 46, borderRadius: 9, flexShrink: 0,
              background: i === 0 ? palette.primary : 'var(--sand)',
              display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
            }}>
              <span style={{ fontSize: 15, fontWeight: 800, color: i === 0 ? 'var(--paper)' : palette.primary }}>{u.dday}</span>
              <span style={{ fontSize: 9, fontWeight: 700, color: i === 0 ? 'var(--paper)' : 'var(--ink-3)' }}>days</span>
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 800, color: 'var(--ink)', lineHeight: 1.35 }}>{u.name}</div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>{u.date}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

interface TodaySchedulePanelProps {
  missions: MissionItem[];
  palette: { primary: string; wash: string; soft: string };
  setRoute: RouteSetter;
}

// 오늘 학습 일정 패널. 체크리스트와 달성률 링을 보여준다.
export function TodaySchedulePanel({ missions, palette, setRoute }: TodaySchedulePanelProps) {
  const remaining = missions.filter(m => !m.isCompleted);
  const done = missions.filter(m => m.isCompleted);
  // 미션이 없으면 달성률은 0%다
  const pct = missions.length > 0 ? Math.round((done.length / missions.length) * 100) : 0;
  return (
    <div id="today-schedule" style={{ background: 'var(--paper)', borderRadius: 14, border: `1px solid ${palette.soft}`, padding: '18px 20px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: palette.primary, letterSpacing: '.1em', textTransform: 'uppercase' }}>오늘 학습 일정</div>
            {remaining.length > 0 && <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)' }}>{remaining.length}개 남음</span>}
          </div>
          {missions.length === 0 ? (
            <div style={{ fontSize: 14, color: 'var(--ink-3)', padding: '6px 0' }}>오늘은 쉬는 날이에요</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {/* 미완료 슬롯 */}
              {remaining.map(m => (
                <div key={m.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px', borderRadius: 9, background: palette.wash, border: `1px solid ${palette.soft}`, fontSize: 14, fontWeight: 800, color: 'var(--ink-2)' }}>
                  <span style={{ width: 8, height: 8, borderRadius: 4, flexShrink: 0, background: palette.primary }} />
                  {m.title}
                </div>
              ))}
              {/* 완료 슬롯 */}
              {done.map(m => (
                <div key={m.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px', borderRadius: 9, fontSize: 14, fontWeight: 600, color: 'var(--muted)', textDecoration: 'line-through' }}>
                  <span style={{ width: 8, height: 8, borderRadius: 4, flexShrink: 0, background: 'var(--muted)' }} />
                  {m.title}
                </div>
              ))}
            </div>
          )}
          {remaining.length > 0 && (
            <button onClick={() => setRoute('learning')} style={{ marginTop: 12, padding: '9px 18px', borderRadius: 9, background: palette.primary, color: 'var(--paper)', fontSize: 14, fontWeight: 800, border: 'none', cursor: 'pointer' }}>
              학습 시작
            </button>
          )}
        </div>
        <div style={{ flexShrink: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5 }}>
          <ProgressRing pct={pct} primary={palette.primary} />
          <div style={{ fontSize: 13, color: 'var(--ink-3)', fontWeight: 600 }}>달성률</div>
        </div>
      </div>
    </div>
  );
}
