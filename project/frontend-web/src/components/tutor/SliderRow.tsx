// 레이블이 있는 슬라이더 행.
// 좌우 라벨과 현재 값 힌트를 보여 주고 팔레트로 활성 색을 바꾼다.
import type { Palette } from '@/lib';

interface SliderRowProps {
  label: string;
  left: string;
  right: string;
  val: number;
  set: (v: number) => void;
  palette: Palette;
  hint: string;
  // 잠금 상태면 키보드 포커스와 입력을 막는다
  disabled?: boolean;
}

export function SliderRow({ label, left, right, val, set, palette, hint, disabled }: SliderRowProps) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', marginBottom: 6 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink)', flex: 1 }}>{label}</div>
        <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{hint}</div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span style={{ fontSize: 12, color: 'var(--muted)', width: 50 }}>{left}</span>
        <div style={{ flex: 1, position: 'relative', height: 28 }}>
          {/* 트랙 배경 */}
          <div style={{
            position: 'absolute', top: '50%', left: 0, right: 0,
            height: 4, marginTop: -2, borderRadius: 2, background: 'var(--sand-2)',
          }} />
          {/* 채워진 구간은 단색 primary */}
          <div style={{
            position: 'absolute', top: '50%', left: 0, width: `${val}%`,
            height: 4, marginTop: -2, borderRadius: 2, background: palette.primary,
          }} />
          {/* aria-label로 스크린리더에 슬라이더 목적을 알린다 */}
          {/* pointerEvents만으로는 키보드·스크린리더 접근을 못 막아 disabled를 준다 */}
          <input
            type="range"
            aria-label={label}
            min="0"
            max="100"
            value={val}
            disabled={disabled}
            onChange={e => set(+e.target.value)}
            style={{ position: 'absolute', inset: 0, width: '100%', opacity: 0, cursor: disabled ? 'not-allowed' : 'pointer' }}
          />
          {/* 슬라이더 핸들 */}
          <div style={{
            position: 'absolute', top: '50%', left: `${val}%`,
            transform: 'translate(-50%,-50%)',
            width: 18, height: 18, borderRadius: '50%', background: 'var(--paper)',
            border: `3px solid ${palette.primary}`,
            boxShadow: 'var(--shadow-sm)', pointerEvents: 'none',
          }} />
        </div>
        <span style={{ fontSize: 12, color: 'var(--muted)', width: 70, textAlign: 'right' }}>{right}</span>
      </div>
    </div>
  );
}
