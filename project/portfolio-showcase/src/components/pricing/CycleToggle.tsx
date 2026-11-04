// 구독 주기(월간/연간) 전환 버튼 그룹
import type { Palette } from '@/lib';

interface CycleToggleProps {
  // 현재 선택된 주기 식별자 ("monthly" | "yearly")
  cycle: string;
  onCycleChange: (cycle: string) => void;
  // 연간 할인 배지 색상 계산에 사용하는 팔레트
  palette: Palette;
}

export function CycleToggle({ cycle, onCycleChange, palette }: CycleToggleProps) {
  return (
    // 표면 패널이라 var(--paper) 토큰을 쓴다
    <div style={{
      display: "flex", gap: 4, padding: 4, background: "var(--paper)",
      borderRadius: 999, border: "1px solid var(--line)", width: "fit-content",
      margin: "0 auto 22px",
    }}>
      <button
        onClick={() => onCycleChange("monthly")}
        style={{
          padding: "8px 18px", borderRadius: 999, fontSize: 12, fontWeight: 700,
          // 선택되면 palette.primary 배경에 흰 글자
          background: cycle === "monthly" ? palette.primary : "transparent",
          color: cycle === "monthly" ? "var(--paper)" : "var(--ink-2)",
        }}
      >
        월간
      </button>
      <button
        onClick={() => onCycleChange("yearly")}
        style={{
          padding: "8px 18px", borderRadius: 999, fontSize: 12, fontWeight: 700,
          background: cycle === "yearly" ? palette.primary : "transparent",
          color: cycle === "yearly" ? "var(--paper)" : "var(--ink-2)",
          display: "flex", alignItems: "center", gap: 5,
        }}
      >
        연간{" "}
        <span style={{
          fontSize: 9, padding: "2px 5px", borderRadius: 4,
          // 연간이 선택되면 배지는 primary 위 반투명 배경, 아니면 var(--honey) 배경
          background: cycle === "yearly" ? "var(--featured-overlay-badge)" : "var(--honey)",
          color: cycle === "yearly" ? "var(--paper)" : "var(--honey-ink)",
        }}>
          -17%
        </span>
      </button>
    </div>
  );
}
