// 사이드바 chrome 화면의 상단 내비게이션 바.
// App.tsx에서 R.chrome === "sidebar"일 때만 렌더된다.
import AICoinIcon from '@/components/AICoinIcon';
import Icon from '@/Icon';
import { useTweaks, PALETTES } from '@/lib';
import type { AppState, RouteSetter } from '@/shared/types';

interface TopBarProps {
  route: string;
  setRoute: RouteSetter;
  appState: AppState;
}

export function TopBar({ setRoute, appState }: TopBarProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;
  return (
    <div style={{
      position: "sticky", top: 0, zIndex: 4,
      display: "flex", alignItems: "center", gap: 14,
      padding: "12px 28px",
      // 학습 화면에서는 상단 바가 눈에 띄지 않게 상태 요약만 보여준다
      background: "var(--nav-blur-bg)",
      backdropFilter: "blur(14px)", borderBottom: "1px solid var(--line-2)",
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <div style={{ width: 7, height: 7, borderRadius: 4, background: palette.primary2 }} />
        <span style={{ fontSize: 12, color: "var(--ink-3)", fontWeight: 600 }}>
          오늘 32분 학습 · 연속 7일
        </span>
      </div>
      <div style={{ flex: 1 }} />

      {/* 생성권 칩. 기술명 대신 쓰임새로 표기한다 */}
      <button onClick={() => setRoute("pricing")} className="lift" style={{
        padding: "7px 12px", borderRadius: 999, fontSize: 12, fontWeight: 800,
        background: "var(--state-warning-bg)", color: "var(--honey-ink)", border: "1px solid var(--amber-line)",
        display: "flex", alignItems: "center", gap: 6,
      }} title="생성권 — 강의자료 잠금 해제와 모의고사 생성에 사용">
        <AICoinIcon size={12} decorative />
        <span>{appState.coins}</span>
        <span style={{ fontSize: 12, opacity: .72, fontWeight: 700 }}>생성권</span>
      </button>

      {/* 알림 버튼. 다른 헤더 아이콘처럼 Icon 컴포넌트를 쓴다 */}
      <button style={{
        width: 34, height: 34, borderRadius: "50%", background: "var(--paper)", border: "1px solid var(--line)",
        display: "grid", placeItems: "center", position: "relative",
      }} className="lift" title="알림">
        <Icon name="bell" size={14} color="var(--ink-2)" />
        <span style={{ position: "absolute", top: 6, right: 8, width: 6, height: 6, borderRadius: 3, background: palette.primary }} />
      </button>
      <button onClick={() => setRoute("settings")} style={{
        width: 34, height: 34, borderRadius: "50%",
        background: "var(--brand-deep)",
        color: "var(--paper)", fontSize: 12, fontWeight: 800, display: "grid", placeItems: "center",
      }} className="lift">김</button>
    </div>
  );
}
