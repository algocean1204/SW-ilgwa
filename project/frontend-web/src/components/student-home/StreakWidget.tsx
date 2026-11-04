// 학습 스트릭(연속일, 최장, 총 학습일)과 히트맵 카드.
// HomeSidebar에서 따로 뺐다.
import { useState, useEffect } from 'react';
import Icon from '@/Icon';
import { getStreak } from '@/apis';
import { useAppState } from '@/lib';

// 학습 시간(분)에 따른 히트맵 강도 레벨 (0~3)
function getIntensity(minutes: number): number {
  if (minutes === 0) return 0;
  if (minutes <= 30) return 1;
  if (minutes <= 60) return 2;
  return 3;
}

const INTENSITY_CLASS = [
  'home-heatmap-cell--0',
  'home-heatmap-cell--1',
  'home-heatmap-cell--2',
  'home-heatmap-cell--3',
] as const;

const DAY_LABELS = ['일', '월', '화', '수', '목', '금', '토'] as const;

// 최근 7주(7행 요일 × 7열 주) 히트맵 격자. heatmap이 null이면 빈 격자를 만든다.
function buildGrid(heatmap: Record<string, number> | null | undefined): number[][] {
  const safeHeatmap = heatmap ?? {};
  const today = new Date();
  const todayDay = today.getDay();
  const grid: number[][] = [];
  for (let row = 0; row < 7; row++) {
    const rowCells: number[] = [];
    for (let col = 0; col < 7; col++) {
      const daysAgo = (6 - col) * 7 + (todayDay - row);
      if (daysAgo < 0) {
        rowCells[col] = -1;
        continue;
      }
      const d = new Date(today);
      d.setDate(d.getDate() - daysAgo);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      rowCells[col] = safeHeatmap[key] ?? 0;
    }
    grid.push(rowCells);
  }
  return grid;
}

export interface StreakInfo {
  currentStreak: number;
  longestStreak: number;
  totalStudyDays: number;
}

interface StreakWidgetProps {
  // 스트릭 수치를 상위(AI 비서 카드)에도 넘긴다
  onLoaded?: (info: StreakInfo) => void;
}

function StreakWidget({ onLoaded }: StreakWidgetProps) {
  const { authReady } = useAppState();
  const [streak, setStreak] = useState(0);
  const [longest, setLongest] = useState(0);
  const [totalDays, setTotalDays] = useState(0);
  const [heatmap, setHeatmap] = useState<Record<string, number>>({});

  useEffect(() => {
    // 부트스트랩이 끝나기 전(authReady=false)에는 보호 API를 부르지 않는다
    if (!authReady) return;
    void getStreak().then((res) => {
      if (res.data) {
        const currentStreak = res.data.currentStreak ?? 0;
        const longestStreak = res.data.longestStreak ?? 0;
        const totalStudyDays = res.data.totalStudyDays ?? 0;
        setStreak(currentStreak);
        setLongest(longestStreak);
        setTotalDays(totalStudyDays);
        setHeatmap(res.data.heatmap ?? {});
        onLoaded?.({ currentStreak, longestStreak, totalStudyDays });
      }
    }).catch((err) => {
      console.warn('학습 스트릭 조회 실패:', err);
    });
  }, [authReady, onLoaded]);

  const grid = buildGrid(heatmap);

  return (
    <div className="sh-sidebar__streak">
      <div className="sh-sidebar__section-label">
        <Icon name="calendar" size={14} />
        <span>학습 스트릭</span>
      </div>

      {/* 스트릭 3가지 수치 */}
      <div className="sh-streak-stats">
        <div className="sh-streak-stat">
          <strong>{streak}</strong>
          <small>연속일</small>
        </div>
        <div className="sh-streak-stat">
          <strong>{longest}</strong>
          <small>최장</small>
        </div>
        <div className="sh-streak-stat">
          <strong>{totalDays}</strong>
          <small>총 학습일</small>
        </div>
      </div>

      <div className="home-heatmap">
        <div className="home-heatmap-labels">
          {DAY_LABELS.map((d) => (
            <span key={d}>{d}</span>
          ))}
        </div>
        <div className="home-heatmap-grid">
          {grid.map((row, ri) => (
            <div key={ri} className="home-heatmap-row">
              {row.map((val, ci) => (
                <span
                  key={ci}
                  className={`home-heatmap-cell ${val < 0 ? 'home-heatmap-cell--future' : INTENSITY_CLASS[getIntensity(val)]}`}
                />
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default StreakWidget;
