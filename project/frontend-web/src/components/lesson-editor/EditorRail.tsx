// 에디터 좌측 세로 아이콘 레일
import type { Palette } from '@/lib';
import Icon from '@/Icon';

interface EditorRailProps {
  palette: Palette;
}

// 레일 아이콘 목록. 두 번째(index 1)가 현재 활성 탭이다.
const RAIL_ICONS = ["home","book","message","file","note","trophy","user","file"] as const;

export function EditorRail({ palette }: EditorRailProps){
  return (
    <div style={{
      background:"var(--paper)", borderRight:"1px solid var(--line)",
      display:"flex", flexDirection:"column", alignItems:"center", gap:6, padding:"18px 0",
    }}>
      {RAIL_ICONS.map((ic, i)=>(
        <button key={i} className="lift" style={{
          width:40, height:40, borderRadius:10,
          background: i===1 ? palette.wash : "transparent",
          color: i===1 ? palette.primary : "var(--ink-3)",
          display:"grid", placeItems:"center",
        }}>
          <Icon name={ic} size={18} color={i===1 ? palette.primary : "var(--ink-3)"}/>
        </button>
      ))}
    </div>
  );
}
