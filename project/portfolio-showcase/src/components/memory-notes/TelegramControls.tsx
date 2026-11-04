// 텔레그램 패널의 Switch(토글)와 SettingRow(설정 행).
// MemoryTelegramPanel에서만 쓰지만 패널 파일이 길어져 따로 뺐다.
import { type ReactNode } from 'react';

export interface SwitchProps {
  on: boolean;
  onChange: (v: boolean) => void;
}

/** 텔레그램 패널 설정용 토글 스위치 */
export function Switch({ on, onChange }: SwitchProps) {
  return (
    <button
      onClick={() => onChange(!on)}
      style={{
        width: 36, height: 20, borderRadius: 10, padding: 2,
        // on: 연두 배경(telegram-connect-bg), off: 흰 반투명
        background: on ? 'var(--telegram-connect-bg)' : 'var(--telegram-overlay-soft)',
        display: 'flex', alignItems: 'center', transition: 'background .2s',
        border: on ? 'none' : '1px solid var(--telegram-overlay-soft)',
      }}
    >
      <div style={{
        width: 16, height: 16, borderRadius: 8,
        // on: 진한 그린 노브(grass-primary), off: 흰 노브
        background: on ? 'var(--grass-primary)' : 'var(--paper)',
        transform: `translateX(${on ? 16 : 0}px)`,
        transition: 'transform .2s',
      }} />
    </button>
  );
}

export interface SettingRowProps {
  label: string;
  right: ReactNode;
  border?: boolean;
}

/** 설정 항목 행. 왼쪽 레이블, 오른쪽 컨트롤. */
export function SettingRow({ label, right, border = true }: SettingRowProps) {
  return (
    <div style={{
      padding: '11px 14px', display: 'flex', alignItems: 'center',
      justifyContent: 'space-between',
      borderBottom: border ? '1px solid var(--line)' : 'none', minHeight: 42,
    }}>
      <span style={{ fontSize: 13, color: 'var(--ink-2)', fontWeight: 500 }}>{label}</span>
      <div style={{ display: 'flex', alignItems: 'center' }}>{right}</div>
    </div>
  );
}
