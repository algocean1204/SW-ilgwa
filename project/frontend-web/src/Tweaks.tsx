// 우측 하단 플로팅 패널 (디자인 편집 모드 트위크).
// 펼치기/접기는 AnimatePresence + motion.div로 슬라이드다운·페이드인한다.
import React, { useState, useEffect } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import type { TweaksState } from '@/shared/types';
import { slideDown } from '@/shared/motion/variants';
import { TRANSITION_BASE } from '@/shared/motion/transitions';
import Icon from '@/Icon';

interface TweaksPanelProps {
  tweaks: TweaksState;
  setTweaks: React.Dispatch<React.SetStateAction<TweaksState>>;
}

// 우측 하단 플로팅 튜닝 패널
function TweaksPanel({ tweaks, setTweaks }: TweaksPanelProps) {
  const [open, setOpen] = useState(true);
  const [available, setAvailable] = useState(false);

  useEffect(() => {
    const handler = (e: MessageEvent) => {
      const m = e.data as { type?: string } | null;
      if (!m || typeof m !== "object") return;
      if (m.type === "__activate_edit_mode") setAvailable(true);
      if (m.type === "__deactivate_edit_mode") setAvailable(false);
    };
    window.addEventListener("message", handler);
    window.parent.postMessage({ type: "__edit_mode_available" }, "*");
    return () => window.removeEventListener("message", handler);
  }, []);

  const update = (k: keyof TweaksState, v: TweaksState[keyof TweaksState]) => {
    setTweaks(t => ({ ...t, [k]: v }));
    window.parent.postMessage({ type: "__edit_mode_set_keys", edits: { [k]: v } }, "*");
  };

  if (!available) return null;

  return (
    <div style={{
      position: "fixed", right: 20, bottom: 20, width: 300, zIndex: 100,
      background: "rgba(255,255,255,.98)", backdropFilter: "blur(10px)",
      border: "1px solid var(--line)", borderRadius: 14, boxShadow: "var(--shadow-lg)",
      overflow: "hidden", fontSize: 12,
    }}>
      <div onClick={() => setOpen(o => !o)} style={{
        padding: "12px 14px", display: "flex", alignItems: "center", justifyContent: "space-between",
        background: "var(--ink)", color: "#fff", cursor: "pointer",
      }}>
        <div style={{ fontWeight: 800, letterSpacing: ".04em" }}><><Icon name="settings" size={14} /> Tweaks</></div>
        <div style={{ fontSize: 10, opacity: .6 }}>{open ? "접기 ▾" : "펼치기 ▸"}</div>
      </div>

      {/* 패널 본문. 접기/펼치기에 slideDown 애니메이션을 쓴다. */}
      <AnimatePresence>
        {open && (
          <motion.div
            variants={slideDown}
            initial="hidden"
            animate="visible"
            exit="exit"
            transition={TRANSITION_BASE}
            style={{ padding: "14px", display: "flex", flexDirection: "column", gap: 16 }}
          >
            <Row label="뷰">
              <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 4 }}>
                {([
                  ["learning", "수업"],
                  ["home", "홈"],
                  ["editor", "에디터"],
                ] as [string, string][]).map(([k, l]) => (
                  <button key={k} onClick={() => update("view", k)} style={{
                    padding: "8px 4px", borderRadius: 8, fontSize: 11, fontWeight: 700,
                    background: tweaks.view === k ? "var(--ink)" : "#fff",
                    color: tweaks.view === k ? "#fff" : "var(--ink-2)",
                    border: "1px solid var(--line)",
                  }}>{l}</button>
                ))}
              </div>
            </Row>
            <Row label="팔레트">
              <div style={{ display: "flex", gap: 6 }}>
                {([
                  ["grass", "#36B36F"],
                  ["coral", "#FF7D87"],
                  ["honey", "#FEE87E"],
                ] as [string, string][]).map(([k, c]) => (
                  <button key={k} onClick={() => update("palette", k)} style={{
                    width: 36, height: 36, borderRadius: "50%", background: c,
                    border: tweaks.palette === k ? `3px solid var(--ink)` : "3px solid #fff",
                    boxShadow: "0 0 0 1px var(--line)",
                    transition: "all .2s",
                  }} />
                ))}
              </div>
            </Row>
            <Row label={"모션 강도 · " + tweaks.motionIntensity + "%"}>
              <input type="range" min="0" max="100" step="10" value={tweaks.motionIntensity}
                onChange={e => update("motionIntensity", +e.target.value)} style={{ width: "100%" }} />
            </Row>
            <Row label="">
              <div style={{ display: "flex", gap: 6 }}>
                <ToggleBtn label="오로라" on={tweaks.showAurora} onClick={() => update("showAurora", !tweaks.showAurora)} />
                <ToggleBtn label="스포트라이트" on={tweaks.showSpotlight} onClick={() => update("showSpotlight", !tweaks.showSpotlight)} />
              </div>
            </Row>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

interface RowProps {
  label: string;
  children: React.ReactNode;
}
function Row({ label, children }: RowProps) {
  return (
    <div>
      {label && <div style={{ fontSize: 10, fontWeight: 800, color: "var(--muted)", letterSpacing: ".08em", textTransform: "uppercase", marginBottom: 6 }}>{label}</div>}
      {children}
    </div>
  );
}

interface ToggleBtnProps {
  label: string;
  on: boolean;
  onClick: () => void;
}
function ToggleBtn({ label, on, onClick }: ToggleBtnProps) {
  return (
    <button onClick={onClick} style={{
      flex: 1, padding: "8px", borderRadius: 8, fontSize: 11, fontWeight: 700,
      background: on ? "var(--grass-1)" : "#fff",
      color: on ? "#fff" : "var(--ink-3)",
      border: "1px solid var(--line)",
    }}>{on ? "● " : "○ "}{label}</button>
  );
}

export { TweaksPanel };
