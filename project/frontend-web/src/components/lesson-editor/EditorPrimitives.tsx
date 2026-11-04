// 레슨 설정 섹션에서 쓰는 작은 UI 블록(LE_Row, LE_Pill)
import type { LE_RowProps, LE_PillProps } from './types';

/** 레슨 설정 행. 레이블과 값을 가로로 놓는다. */
export function LE_Row({ label, children }: LE_RowProps){
  return (
    <div style={{ display:"flex", alignItems:"center", gap:8 }}>
      <div style={{ fontSize:12, color:"var(--ink-3)", fontWeight:600, width:70 }}>{label}</div>
      <div style={{ flex:1, display:"flex", gap:4, justifyContent:"flex-end", alignItems:"center" }}>{children}</div>
    </div>
  );
}

/** 레슨 난이도 등 선택 버튼. 활성 여부에 따라 색이 바뀐다. */
export function LE_Pill({ children, active, palette }: LE_PillProps){
  return (
    <span style={{
      padding:"3px 8px", borderRadius:999, fontSize:12, fontWeight:700,
      background: active ? palette.wash : "var(--sand)",
      color: active ? palette.primary : "var(--ink-3)",
      border: active ? `1px solid ${palette.soft}` : "1px solid transparent",
    }}>{children}</span>
  );
}
