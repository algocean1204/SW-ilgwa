// 이번 주 학습 통계 3열 그리드. getWeeklyReport로 데이터를 불러온다.
import { useState, useEffect } from 'react';
import Icon from '@/Icon';
import { getWeeklyReport } from '@/apis';
import { useAppState } from '@/lib';
import type { WeeklyReportResponse } from '@/apis';

interface HomeStatsGridProps {
  // 주간 리포트를 불러오면 상위(AI 비서 카드)에 넘기는 콜백
  onLoaded?: (report: WeeklyReportResponse) => void;
}

function HomeStatsGrid({ onLoaded }: HomeStatsGridProps) {
  const { authReady } = useAppState();
  const [totalMin, setTotalMin] = useState(0);
  const [quizCount, setQuizCount] = useState(0);
  const [avgScore, setAvgScore] = useState(0);

  useEffect(() => {
    // 부트스트랩이 끝나기 전(authReady=false)에는 보호 API를 부르지 않는다
    if (!authReady) return;
    void getWeeklyReport().then((res) => {
      if (res.data) {
        setTotalMin(res.data.totalStudyMinutes);
        setQuizCount(res.data.totalQuizCount);
        setAvgScore(res.data.averageScorePercent);
        onLoaded?.(res.data);
      }
    }).catch((err) => {
      console.warn('주간 학습 리포트 조회 실패:', err);
    });
  }, [authReady, onLoaded]);

  const hours = Math.floor(totalMin / 60);
  const mins = totalMin % 60;

  return (
    <div className="sh-stats-grid" aria-label="이번 주 학습 통계">
      {/* 학습시간 */}
      <div className="sh-stat-card">
        <span className="sh-stat-card__icon" aria-hidden="true">
          <Icon name="clock" size={15} />
        </span>
        <div className="sh-stat-card__body">
          <strong className="sh-stat-card__value">{hours}h {mins}m</strong>
          <small className="sh-stat-card__label">이번 주 학습</small>
        </div>
      </div>

      {/* 풀이한 퀴즈 수 */}
      <div className="sh-stat-card">
        <span className="sh-stat-card__icon" aria-hidden="true">
          <Icon name="note" size={15} />
        </span>
        <div className="sh-stat-card__body">
          <strong className="sh-stat-card__value">{quizCount}개</strong>
          <small className="sh-stat-card__label">풀이한 퀴즈</small>
        </div>
      </div>

      {/* 평균 정답률 */}
      <div className="sh-stat-card">
        <span className="sh-stat-card__icon" aria-hidden="true">
          <Icon name="chart" size={15} />
        </span>
        <div className="sh-stat-card__body">
          <strong className="sh-stat-card__value">
            {quizCount > 0 ? `${avgScore}%` : '—'}
          </strong>
          <small className="sh-stat-card__label">평균 정답률</small>
        </div>
      </div>
    </div>
  );
}

export default HomeStatsGrid;
