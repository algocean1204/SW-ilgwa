// 공유 UI 프리미티브 컴포넌트
import React, { useRef, useState, useEffect } from 'react';
import type { Palette } from './palettes';

// 스포트라이트 래퍼. 커서 좌표를 --mx/--my CSS 변수로 넘긴다
export interface SpotProps {
  as?: keyof React.JSX.IntrinsicElements;
  className?: string;
  children?: React.ReactNode;
  enabled?: boolean;
  style?: React.CSSProperties;
  [key: string]: unknown;
}

export function Spot({ as = "div", className = "", children, enabled = true, style = {}, ...rest }: SpotProps) {
  const ref = useRef<HTMLElement>(null);
  const onMove = (e: React.MouseEvent) => {
    if (!ref.current) return;
    const r = ref.current.getBoundingClientRect();
    ref.current.style.setProperty('--mx', (e.clientX - r.left) + 'px');
    ref.current.style.setProperty('--my', (e.clientY - r.top) + 'px');
  };
  const onLeave = () => {
    if (!ref.current) return;
    ref.current.style.setProperty('--mx', '-200px');
    ref.current.style.setProperty('--my', '-200px');
  };
  const Tag = as as React.ElementType;
  return (
    <Tag
      ref={ref}
      className={(enabled ? "spot " : "") + className}
      onMouseMove={enabled ? onMove : undefined}
      onMouseLeave={enabled ? onLeave : undefined}
      style={style}
      {...rest}
    >
      {children}
    </Tag>
  );
}

// 오로라 배경
export interface AuroraProps {
  active?: boolean;
  palette: Palette;
}

export function Aurora({ active = true, palette }: AuroraProps) {
  if (!active) return null;
  /* radial-gradient 대신 단색 blur 구체로 오로라를 표현한다 */
  return (
    <div style={{ position: "absolute", inset: 0, overflow: "hidden", pointerEvents: "none", zIndex: 0 }} aria-hidden>
      <div style={{
        position: "absolute", width: "40%", height: "50%", left: "5%", top: "10%",
        borderRadius: "50%", background: palette.primary2, opacity: 0.08,
        animation: "auroraA 18s ease-in-out infinite", filter: "blur(40px)"
      }} />
      <div style={{
        position: "absolute", width: "35%", height: "45%", right: "5%", top: "5%",
        borderRadius: "50%", background: palette.soft, opacity: 0.12,
        animation: "auroraB 22s ease-in-out infinite", filter: "blur(50px)"
      }} />
      <div style={{
        position: "absolute", width: "50%", height: "40%", left: "30%", bottom: "5%",
        borderRadius: "50%", background: "var(--honey)", opacity: 0.08,
        animation: "auroraC 26s ease-in-out infinite", filter: "blur(60px)"
      }} />
    </div>
  );
}

// 애니메이션 진행 바
export interface ProgressProps {
  value?: number;
  color?: string;
  track?: string;
}

export function Progress({ value = 62, color = "#36B36F", track = "var(--paper)" }: ProgressProps) {
  const [w, setW] = useState(0);
  useEffect(() => {
    const t = setTimeout(() => setW(value), 120);
    return () => clearTimeout(t);
  }, [value]);
  return (
    <div style={{ height: 6, background: track, borderRadius: 3, overflow: "hidden", position: "relative" }}>
      <div style={{
        /* 그라데이션 스트라이프 없이 단색 fill과 애니메이션만 쓴다 */
        height: "100%", width: w + "%", background: color, borderRadius: 3,
        transition: "width 1.2s cubic-bezier(.2,.7,.2,1)",
      }} />
    </div>
  );
}

// 카운트업 숫자
export interface CountUpProps {
  to?: number;
  duration?: number;
  format?: (n: number) => string;
}

export function CountUp({ to = 1240, duration = 1200, format = (n: number) => n.toLocaleString() }: CountUpProps) {
  const [v, setV] = useState(0);
  useEffect(() => {
    const start = performance.now();
    let raf: number;
    const tick = (t: number) => {
      const k = Math.min(1, (t - start) / duration);
      const ease = 1 - Math.pow(1 - k, 3);
      setV(Math.round(to * ease));
      if (k < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [to, duration]);
  return <>{format(v)}</>;
}

// 태그 칩
export type ChipTone = 'grass' | 'coral' | 'honey' | 'ink' | 'ghost' | 'sand';

export interface ChipProps {
  children: React.ReactNode;
  tone?: ChipTone;
  onClick?: () => void;
  active?: boolean;
  style?: React.CSSProperties;
}

export function Chip({ children, tone = "grass", onClick, active = false, style = {} }: ChipProps) {
  const tones: Record<ChipTone, { bg: string; color: string; border: string }> = {
    grass: { bg: "var(--grass-wash)", color: "var(--grass-1)", border: "var(--grass-soft)" },
    coral: { bg: "var(--coral-wash)", color: "var(--coral-ink-deep)", border: "var(--blush-wash)" },
    honey: { bg: "var(--honey-tint)", color: "var(--honey-ink)", border: "var(--honey-mid)" },
    ink: { bg: "var(--ink)", color: "var(--honey)", border: "var(--ink)" },
    ghost: { bg: "var(--paper)", color: "var(--ink-2)", border: "var(--line)" },
    sand: { bg: "var(--sand)", color: "var(--ink-2)", border: "var(--line)" },
  };
  const t = tones[tone];
  return (
    <button onClick={onClick} style={{
      display: "inline-flex", alignItems: "center", gap: 6, padding: "6px 10px", borderRadius: 999,
      background: active ? t.color : t.bg, color: active ? "var(--paper)" : t.color,
      border: `1px solid ${active ? t.color : t.border}`, fontSize: 12, fontWeight: 600, lineHeight: 1,
      transition: "all .2s", ...style,
    }}>{children}</button>
  );
}
