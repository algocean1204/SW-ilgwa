// 온보딩 2단계: 학습 목표 선택. 학습 시간은 OnboardingStep2b.tsx에 있다.
// 상태는 부모(Onboarding.tsx)가 넘긴다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';

export interface Step2Props {
  goal: string;
  setGoal: (g: string) => void;
  palette: Palette;
}

// 선택 가능한 학습 목표 목록
export const GOAL_LIST = [
  { id: "exam",   icon: "trophy",  label: "시험 합격",  desc: "정보처리기사 · 토익 등" },
  { id: "review", icon: "clock",   label: "수업 복습",  desc: "학교 교재를 다시 정리" },
  { id: "growth", icon: "sparkle", label: "자기계발",   desc: "매일 조금씩, 분야 상관없이" },
  { id: "quick",  icon: "bolt",    label: "벼락치기",   desc: "1~2주 집중 요약" },
];

export function OnboardingStep2({ goal, setGoal, palette }: Step2Props) {
  return (
    <div>
      <h1 style={{ margin: 0, fontSize: 32, fontWeight: 800, letterSpacing: 0, color: "var(--ink)" }}>
        학습 목표가 뭐예요?
      </h1>
      <p style={{ marginTop: 10, fontSize: 14, color: "var(--ink-3)" }}>
        매일 보낼 분량을 이 기준으로 맞춰요.
      </p>

      <div style={{ marginTop: 26 }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 10 }}>
          {GOAL_LIST.map(g => {
            const on = goal === g.id;
            return (
              <button key={g.id} onClick={() => setGoal(g.id)} className="lift" style={{
                padding: "14px 12px", borderRadius: 12, textAlign: "left",
                background: on ? palette.wash : "var(--paper)",
                border: on ? `1.5px solid ${palette.primary}` : "1px solid var(--line)",
              }}>
                <Icon name={g.icon} size={20} color={on ? palette.primary : "var(--ink-3)"} strokeWidth={1.7} />
                <div style={{ fontSize: 13, fontWeight: 700, color: on ? palette.primary : "var(--ink)", marginTop: 4 }}>
                  {g.label}
                </div>
                <div style={{ fontSize: 12, color: "var(--ink-3)", marginTop: 2 }}>
                  {g.desc}
                </div>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
