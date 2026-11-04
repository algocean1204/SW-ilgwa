// 온보딩 5단계: 학습 기간, 난이도, 강의 수, 튜터를 고른다.
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import { SettingsPanel } from '@/components/course-explore/SettingsPanel';
import type { TeacherItem } from '@/components/course-explore/types';

interface OnboardingStep5Props {
  palette: Palette;
  setRoute: RouteSetter;
  duration: number;
  depth: string;
  teacher: string;
  lessonCount: number;
  defaultTutors: TeacherItem[];
  customTutors: TeacherItem[];
  onDurationChange: (d: number) => void;
  onDepthChange: (d: string) => void;
  onTeacherChange: (id: string) => void;
  onLessonCountChange: (n: number) => void;
}

export function OnboardingStep5({
  palette, setRoute, duration, depth, teacher, lessonCount,
  defaultTutors, customTutors,
  onDurationChange, onDepthChange, onTeacherChange, onLessonCountChange,
}: OnboardingStep5Props) {
  return (
    <div>
      <h1 style={{ fontSize: 32, fontWeight: 800, letterSpacing: '-.03em', margin: '0 0 6px' }}>
        세부 설정을 해주세요.
      </h1>
      <p style={{ fontSize: 14, color: 'var(--ink-3)', margin: '0 0 24px', lineHeight: 1.6 }}>
        학습 기간, 난이도, 강의 수, 튜터를 선택할 수 있어요.
      </p>
      <SettingsPanel
        duration={duration}
        depth={depth}
        teacher={teacher}
        lessonCount={lessonCount}
        defaultTutors={defaultTutors}
        customTutors={customTutors}
        palette={palette}
        setRoute={setRoute}
        onDurationChange={onDurationChange}
        onDepthChange={onDepthChange}
        onTeacherChange={onTeacherChange}
        onLessonCountChange={onLessonCountChange}
        hideCustomTutorLink
      />
    </div>
  );
}
