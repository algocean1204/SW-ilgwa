// 이번 주 학습 시간, 퀴즈, 정답률을 한 줄로 보여 주는 주간 요약
import { useState, useEffect } from 'react';
import Icon from '@/Icon';
import { getWeeklyReport } from '@/apis';
import { useAppState } from '@/lib';

function WeeklySummary() {
  const { authReady } = useAppState();
  const [totalMin, setTotalMin] = useState(0);
  const [quizCount, setQuizCount] = useState(0);
  const [avgScore, setAvgScore] = useState(0);

  useEffect(() => {
    // authReady가 false면 부트스트랩 전이라 보호 API를 호출하지 않는다
    if (!authReady) return;
    void getWeeklyReport().then((res) => {
      if (res.data) {
        setTotalMin(res.data.totalStudyMinutes);
        setQuizCount(res.data.totalQuizCount);
        setAvgScore(res.data.averageScorePercent);
      }
    }).catch(() => {});
  }, [authReady]);

  const hours = Math.floor(totalMin / 60);
  const mins = totalMin % 60;

  return (
    <div className="home-weekly-summary">
      <div className="home-weekly-stat">
        <span className="home-weekly-stat__icon">
          <Icon name="clock" size={14} />
        </span>
        <div>
          <strong>{hours}h {mins}m</strong>
          <small>이번 주 학습</small>
        </div>
      </div>
      <div className="home-weekly-stat">
        <span className="home-weekly-stat__icon">
          <Icon name="note" size={14} />
        </span>
        <div>
          <strong>{quizCount}개</strong>
          <small>풀이한 퀴즈</small>
        </div>
      </div>
      <div className="home-weekly-stat">
        <span className="home-weekly-stat__icon">
          <Icon name="check" size={14} />
        </span>
        <div>
          <strong>{quizCount > 0 ? `${avgScore}%` : '—'}</strong>
          <small>평균 정답률</small>
        </div>
      </div>
    </div>
  );
}

export default WeeklySummary;
