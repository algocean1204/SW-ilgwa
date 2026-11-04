// 홈 우측 사이드바. 학습 스트릭(StreakWidget) 카드 하나만 있다.
import StreakWidget from '@/components/student-home/StreakWidget';
import type { StreakInfo } from '@/components/student-home/StreakWidget';

interface HomeSidebarProps {
  // 스트릭 수치를 불러오면 상위(AI 비서 카드)에 넘기는 콜백
  onStreakLoaded?: (info: StreakInfo) => void;
}

function HomeSidebar({ onStreakLoaded }: HomeSidebarProps) {
  return (
    <aside className="sh-sidebar" aria-label="학습 스트릭">
      <StreakWidget onLoaded={onStreakLoaded} />
    </aside>
  );
}

export default HomeSidebar;
