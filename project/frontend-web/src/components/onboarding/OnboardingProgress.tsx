// 온보딩 상단의 로고와 진행률 바
import type { Palette } from '@/lib';
import Icon from '@/Icon';

interface OnboardingProgressProps {
  step: number;
  totalSteps: number;
  palette: Palette;
}

export function OnboardingProgress({ step, totalSteps, palette }: OnboardingProgressProps) {
  const pct = (step / totalSteps) * 100;

  return (
    <div style={{ display: "flex", alignItems: "center", gap: 14, marginBottom: 32 }}>
      {/* 일과 로고 + 아이콘 */}
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <div style={{
          width: 28, height: 28, borderRadius: "50%", background: "var(--grass-wash)",
          border: "1px solid var(--grass-3)",
          display: "grid", placeItems: "center",
        }}>
          <Icon name="owl" size={15} color="var(--grass-1)" />
        </div>
        <span className="script" style={{ fontSize: 22, color: "var(--grass-1)", lineHeight: 1, transform: "translateY(-2px)" }}>
          일과
        </span>
      </div>

      {/* 진행 바 */}
      <div style={{ flex: 1, height: 4, background: "var(--sand-2)", borderRadius: 2, overflow: "hidden" }}>
        <div style={{ height: "100%", width: pct + "%", background: palette.primary2, transition: "width .4s" }} />
      </div>

      {/* step 카운터 */}
      <span style={{ fontSize: 12, fontWeight: 700, color: "var(--ink-3)" }}>
        {step} / {totalSteps}
      </span>
    </div>
  );
}
