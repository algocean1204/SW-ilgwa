// 레이블이 있는 토글 스위치 행.
// on/off 상태와 설명을 보여 주고 팔레트로 활성 색을 바꾼다.
import type { Palette } from '@/lib';

interface ToggleRowProps {
  label: string;
  desc: string;
  val: boolean;
  set: (v: boolean) => void;
  palette: Palette;
  // 잠금 상태면 버튼을 비활성화한다
  disabled?: boolean;
}

export function ToggleRow({ label, desc, val, set, palette, disabled }: ToggleRowProps) {
  return (
    <button
      role="switch"
      aria-checked={val}
      // disabled면 onClick을 막고 aria-disabled로 스크린리더에 알린다
      disabled={disabled}
      aria-disabled={disabled}
      onClick={() => set(!val)}
      className="lift"
      style={{
        display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px',
        borderRadius: 12,
        background: val ? palette.wash : 'var(--paper)',
        border: val ? `1.5px solid ${palette.soft}` : '1px solid var(--line)',
        textAlign: 'left',
      }}
    >
      {/* 스위치 트랙과 핸들 */}
      <div style={{
        width: 38, height: 22, borderRadius: 999,
        background: val ? palette.primary : 'var(--sand-2)',
        position: 'relative', flexShrink: 0, transition: 'background .2s',
      }}>
        {/* 핸들 그림자는 var(--shadow-knob) 토큰을 쓴다 */}
        <div style={{
          position: 'absolute', top: 2, left: val ? 18 : 2,
          width: 18, height: 18, borderRadius: '50%', background: 'var(--paper)',
          boxShadow: 'var(--shadow-knob)', transition: 'left .2s',
        }} />
      </div>
      <div>
        <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink)' }}>{label}</div>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>{desc}</div>
      </div>
    </button>
  );
}
