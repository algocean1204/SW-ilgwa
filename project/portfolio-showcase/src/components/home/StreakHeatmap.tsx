// 연속 학습일과 최근 7주 학습 히트맵
import { useState, useEffect } from 'react';
import Icon from '@/Icon';
import { getStreak } from '@/apis';
import { useAppState } from '@/lib';

// 학습 시간에 따른 강도 레벨 (0-3)
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

// 최근 7주 히트맵 격자를 만든다. 7행(요일) × 7열(주).
function buildGrid(heatmap: Record<string, number> | null | undefined): number[][] {
  // API에서 heatmap 필드가 빠져도 throw 없이 빈 히트맵으로 그린다
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

function StreakHeatmap() {
  const { authReady } = useAppState();
  const [streak, setStreak] = useState(0);
  const [longest, setLongest] = useState(0);
  const [totalDays, setTotalDays] = useState(0);
  const [heatmap, setHeatmap] = useState<Record<string, number>>({});

  useEffect(() => {
    // authReady가 false면 부트스트랩 전이라 보호 API를 호출하지 않는다
    if (!authReady) return;
    void getStreak().then((res) => {
      if (res.data) {
        setStreak(res.data.currentStreak ?? 0);
        setLongest(res.data.longestStreak ?? 0);
        setTotalDays(res.data.totalStudyDays ?? 0);
        // buildGrid가 heatmap[key]를 읽으므로 null이면 빈 객체로 바꾼다
        setHeatmap(res.data.heatmap ?? {});
      }
    }).catch(() => {});
  }, [authReady]);

  const grid = buildGrid(heatmap);

  return (
    <aside className="home-streak-pane">
      <div className="home-pane-label">
        <Icon name="calendar" size={15} />
        <span>학습 스트릭</span>
      </div>

      <div className="home-streak-stats">
        <div>
          <strong>{streak}</strong>
          <small>연속일</small>
        </div>
        <div>
          <strong>{longest}</strong>
          <small>최장 기록</small>
        </div>
        <div>
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
    </aside>
  );
}

export default StreakHeatmap;
