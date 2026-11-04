// "AI 비서 하니의 한마디" 카드. Hero 아래, HomeStatsGrid 위에 놓인다.
// 문구는 규칙(if-else)으로만 고르고 LLM API는 부르지 않는다.
// 제목에 닉네임을 넣고, 닉네임이 없으면 "오늘의 한마디"를 쓴다.
import Icon from '@/Icon';
import type { CourseSummaryResponse } from '@/apis';

// pickHaniMessage 판단에 사용하는 사용자 학습 통계
export interface HaniUserStats {
  // 평균 정답률(%). WeeklyReportResponse.averageScorePercent
  averageScorePercent: number;
  // 이번 주 퀴즈 수. WeeklyReportResponse.totalQuizCount이며 0이면 정답률을 믿을 수 없다.
  totalQuizCount: number;
  // 연속 학습일. StreakResponse.currentStreak
  currentStreak: number;
  // 현재 수강 중인 과외 (제목 표시에 사용)
  activeCourse: CourseSummaryResponse | undefined;
}

// 정답률이 이 값(%) 미만이면 "오답 복습" 메시지(A)를 먼저 보여준다
const LOW_SCORE_THRESHOLD = 70;
// 연속 학습일이 이 값 이상이면 "연속 학습 응원" 메시지(B)를 보여준다
const STREAK_MIN_DAYS = 2;

// 학습 통계만으로 메시지를 고르는 순수 함수.
// 우선순위는 A(정답률 낮음), B(연속 학습 중), Default 순이다.
export function pickHaniMessage(stats: HaniUserStats): string {
  const { averageScorePercent, totalQuizCount, currentStreak, activeCourse } = stats;

  // A: 이번 주 퀴즈를 풀었고 평균 정답률이 기준 미만이면 오답 복습을 권한다
  if (totalQuizCount > 0 && averageScorePercent < LOW_SCORE_THRESHOLD) {
    return '오답이 있어요. 가볍게 복습하고 다음으로 넘어가세요.';
  }

  // B: 연속 학습 중이면 현재 과외 이름을 넣어 계속 공부하라고 안내한다
  if (currentStreak >= STREAK_MIN_DAYS && activeCourse) {
    return `${currentStreak}일 연속 학습 중입니다. '${activeCourse.title}'의 다음 강의를 이어가세요.`;
  }

  // Default: 판단할 근거가 없을 때의 기본 문구
  return '오늘 학습을 시작하세요. 현재 수업을 이어서 들을 수 있습니다.';
}

interface HaniAssistantCardProps {
  stats: HaniUserStats;
  nickname?: string | null;
}

function HaniAssistantCard({ stats, nickname }: HaniAssistantCardProps) {
  const message = pickHaniMessage(stats);

  // 닉네임이 있으면 "○○○ 학생을 위한 한마디", 없으면 기본 문구를 쓴다
  const trimmedNickname = nickname?.trim();
  const title = trimmedNickname ? `${trimmedNickname} 학생을 위한 한마디` : '오늘의 한마디';

  return (
    <section className="sh-hani-card" aria-label="AI 비서 하니의 한마디">
      <div className="sh-hani-card__icon" aria-hidden="true">
        <Icon name="sparkle" size={18} />
      </div>
      <div className="sh-hani-card__body">
        <span className="sh-hani-card__title">{title}</span>
        <p className="sh-hani-card__message">{message}</p>
      </div>
    </section>
  );
}

export default HaniAssistantCard;
