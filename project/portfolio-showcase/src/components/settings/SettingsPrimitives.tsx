// 설정 탭들이 같이 쓰는 UI 원시 컴포넌트.
// Card, Field, Input: 비즈니스 로직 없는 레이아웃 컨테이너
// SToggle: 팔레트 색과 외부 val로 표시 상태를 맞추는 스위치
// ToggleRow: SToggle과 텍스트를 구분선과 함께 놓는 행
import React, { useEffect, useState } from 'react';
import type { Palette } from '@/lib';

export interface CardProps {
  title?: string;
  subtitle?: string;
  children: React.ReactNode;
}

export interface FieldProps {
  label: string;
  hint?: string;
  children: React.ReactNode;
}

export interface InputProps {
  value?: string;
  placeholder?: string;
  type?: string;
  disabled?: boolean;
  readOnly?: boolean;
  onChange?: React.ChangeEventHandler<HTMLInputElement>;
}

export interface SToggleProps {
  val: boolean;
  palette: Palette;
  onClick?: (v: boolean) => void;
}

export interface ToggleRowProps {
  title: string;
  desc: string;
  val: boolean;
  palette: Palette;
  onToggle?: (next: boolean) => void;
}

export function Card({ title, subtitle, children }: CardProps) {
  return (
    <section style={{
      padding: "22px 24px", borderRadius: 16,
      background: "var(--paper)", border: "1px solid var(--line)", marginBottom: 14,
    }}>
      {title && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 14, fontWeight: 800, color: "var(--ink)", letterSpacing: "-.01em" }}>
            {title}
          </div>
          {subtitle && (
            <div style={{ fontSize: 11.5, color: "var(--ink-3)", marginTop: 3, lineHeight: 1.5 }}>
              {subtitle}
            </div>
          )}
        </div>
      )}
      {children}
    </section>
  );
}

export function Field({ label, hint, children }: FieldProps) {
  return (
    <div style={{
      display: "grid", gridTemplateColumns: "160px 1fr", gap: 16,
      padding: "14px 0", borderTop: "1px solid var(--line-2)",
    }}>
      <div>
        <div style={{ fontSize: 12, fontWeight: 700, color: "var(--ink)" }}>{label}</div>
        {hint && (
          <div style={{ fontSize: 10.5, color: "var(--ink-3)", marginTop: 3, lineHeight: 1.5 }}>
            {hint}
          </div>
        )}
      </div>
      <div>{children}</div>
    </div>
  );
}

export function Input({ value, placeholder, type, disabled, readOnly, onChange }: InputProps) {
  return (
    <input
      value={value}
      placeholder={placeholder}
      type={type ?? "text"}
      disabled={disabled}
      readOnly={readOnly ?? (!onChange && value !== undefined)}
      onChange={onChange}
      style={{
        width: "100%", padding: "10px 13px", borderRadius: 9,
        border: "1px solid var(--line)", fontSize: 13, color: "var(--ink)",
        background: disabled ? "var(--sand)" : "var(--paper)", fontFamily: "inherit",
        boxSizing: "border-box",
      }}
    />
  );
}

export function SToggle({ val, palette, onClick }: SToggleProps) {
  const [uncontrolledValue, setUncontrolledValue] = useState(!!val);
  const isControlled = !!onClick;
  const checked = isControlled ? !!val : uncontrolledValue;

  // 비제어 모드(onClick 없음)에서 외부 val이 바뀌면 내부 상태도 맞춘다
  // 예: 설정 데이터가 비동기로 로드된 뒤 val이 바뀌는 경우
  useEffect(() => {
    if (!isControlled) setUncontrolledValue(!!val);
  }, [val, isControlled]);

  return (
    <button
      onClick={() => {
        const next = !checked;
        if (onClick) {
          onClick(next);
        } else {
          setUncontrolledValue(next);
        }
      }}
      style={{
        width: 40, height: 23, borderRadius: 999,
        background: checked ? palette.primary : "var(--sand-2)",
        position: "relative", flexShrink: 0, transition: "background .2s",
      }}
    >
      <div style={{
        position: "absolute", top: 2, left: checked ? 19 : 2,
        width: 19, height: 19, borderRadius: "50%",
        // 노브는 카드 표면색 var(--paper)
        background: "var(--paper)",
        boxShadow: "var(--shadow-knob)", transition: "left .2s",
      }} />
    </button>
  );
}

export function ToggleRow({ title, desc, val, palette, onToggle }: ToggleRowProps) {
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 14,
      padding: "14px 0", borderTop: "1px solid var(--line-2)",
    }}>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 12.5, fontWeight: 700, color: "var(--ink)" }}>{title}</div>
        <div style={{ fontSize: 11, color: "var(--ink-3)", marginTop: 3, lineHeight: 1.5 }}>{desc}</div>
      </div>
      <SToggle val={val} palette={palette} onClick={onToggle} />
    </div>
  );
}
