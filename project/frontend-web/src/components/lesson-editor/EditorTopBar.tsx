// 에디터 상단 헤더 바. 로고, 강의 제목, 액션 Chip 버튼을 보여주며 상태는 없다.
import { Chip } from '@/lib';
import type { Palette } from '@/lib';
import Icon from '@/Icon';

// lib가 Chip tone 타입을 내보내지 않아 여기서 선언한다
type ChipTone = 'grass' | 'coral' | 'honey' | 'ink' | 'ghost' | 'sand';

interface EditorTopBarProps {
  palette: Palette;
}

// 헤더 우측 액션 버튼. 이 배열 순서대로 그린다.
const ACTION_CHIPS: [string, string, ChipTone][] = [
  ["user",     "수강생",    "ghost"],
  ["eye",      "미리보기",  "ghost"],
  ["download", "다운로드 ▾","honey"],
  ["bolt",     "공유",      "grass"],
];

export function EditorTopBar({ palette }: EditorTopBarProps){
  return (
    <div style={{
      height:60, background:"var(--paper)", borderBottom:"1px solid var(--line)",
      display:"flex", alignItems:"center", padding:"0 24px", gap:14, fontSize:13,
    }}>
      {/* 로고: 올빼미 아이콘과 브랜드 스크립트 */}
      <div style={{ display:"flex", alignItems:"center", gap:6 }}>
        <div style={{ width:28, height:28, borderRadius:"50%", background:palette.soft, display:"grid", placeItems:"center" }}><Icon name="owl" size={16} color={palette.primary} /></div>
        <span className="script" style={{ fontSize:28, color:palette.primary, lineHeight:1, letterSpacing:"-.01em", transform:"translateY(-2px)", display:"inline-block" }}>일과</span>
      </div>

      <button style={{ width:30, height:30, borderRadius:8, background:"var(--sand)", display:"grid", placeItems:"center" }}><Icon name="arrowLeft" size={14} /></button>

      <div style={{ fontWeight:800, color:"var(--ink)" }}>정보처리기사 실기 대비 — 소프트웨어 개발 생명주기 집중 강의</div>

      <div style={{ flex:1 }} />

      {/* 우측 액션 Chip 버튼 그룹 */}
      {ACTION_CHIPS.map(([ic, lb, tn])=>(
        <Chip key={lb} tone={tn} style={{ padding:"8px 12px", fontSize:12, display:"flex", alignItems:"center", gap:4 }}>
          <Icon name={ic} size={13}/> {lb}
        </Chip>
      ))}
    </div>
  );
}
