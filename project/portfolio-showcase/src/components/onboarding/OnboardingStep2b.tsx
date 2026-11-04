// 온보딩 2b단계: 하루 목표 학습 시간 슬라이더. 상태는 부모(Onboarding.tsx)가 넘긴다.
import type { Palette } from '@/lib';

export interface Step2bProps {
  when: number;
  setWhen: (w: number) => void;
  palette: Palette;
}

export function OnboardingStep2b({ when, setWhen, palette }: Step2bProps) {
  return (
    <div>
      <h1 style={{ margin: 0, fontSize: 32, fontWeight: 800, letterSpacing: 0, color: "var(--ink)" }}>
        하루에 얼마나 공부할 수 있어요?
      </h1>
      <p style={{ marginTop: 10, fontSize: 14, color: "var(--ink-3)" }}>
        텔레그램 알림도 이 시각 근처로 설정돼요.
      </p>

      <div style={{ marginTop: 26 }}>
        <div style={{
          padding: "18px 22px", borderRadius: 14,
          background: palette.wash, border: `1px solid ${palette.soft}`,
        }}>
          <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
            <span style={{ fontSize: 42, fontWeight: 800, color: palette.primary, letterSpacing: 0 }}>{when}</span>
            <span style={{ fontSize: 14, color: palette.primary, fontWeight: 600 }}>분 / 일</span>
            <div style={{ flex: 1 }} />
            <div style={{ fontSize: 12, color: palette.primary, opacity: .8 }}>
              주 {(when * 7 / 60).toFixed(1)}시간
            </div>
          </div>
          <input
            type="range"
            min="0"
            max="60"
            step="5"
            value={when}
            onChange={e => setWhen(Math.max(5, Number(e.target.value)))}
            className="onboarding-slider"
            style={{ width: "100%", marginTop: 10, accentColor: palette.primary2 }}
          />
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: palette.primary, opacity: .6, marginTop: 4 }}>
            <span>0분</span>
            <span>20분</span>
            <span>40분</span>
            <span>60분</span>
          </div>
        </div>
      </div>
    </div>
  );
}
