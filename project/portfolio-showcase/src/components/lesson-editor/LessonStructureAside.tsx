// 드래그로 순서를 바꾸는 챕터·레슨 목록. 드래그 상태(draggedIdx, items)는 부모가 props로 넘긴다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';
import type { LessonItem } from './types';

interface LessonStructureAsideProps {
  palette: Palette;
  items: LessonItem[];
  selected: string;
  draggedIdx: number | null;
  setSelected: (id: string) => void;
  onDragStart: (i: number) => void;
  onDragOver: (e: React.DragEvent, i: number) => void;
  onDragEnd: () => void;
}

export function LessonStructureAside({
  palette, items, selected, draggedIdx,
  setSelected, onDragStart, onDragOver, onDragEnd,
}: LessonStructureAsideProps){
  return (
    <aside style={{ background:"var(--paper)", borderRight:"1px solid var(--line)", padding:"16px", position:"relative" }}>
      <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:12 }}>
        <button style={{ padding:"7px 12px", borderRadius:8, background:"var(--coral-wash)", color:"var(--coral-ink-deep)", fontWeight:800, fontSize:11 }}>■ 멈추기</button>
        <div style={{ flex:1 }} />
        {/* + 챕터/수업 버튼 */}
        <button style={{ padding:"7px 10px", borderRadius:6, background:"var(--paper)", border:"1px solid var(--line)", fontSize:12, fontWeight:600, color:"var(--ink-3)" }}>+ 챕터</button>
        <button style={{ padding:"7px 10px", borderRadius:6, background:"var(--paper)", border:"1px solid var(--line)", fontSize:12, fontWeight:600, color:"var(--ink-3)" }}>+ 수업</button>
      </div>

      <div style={{ fontSize:12, fontWeight:800, color:"var(--muted)", letterSpacing:".08em", textTransform:"uppercase", marginBottom:8 }}>강의 구조 · 드래그로 정렬</div>

      {items.map((it, i)=>{
        if(it.kind==="chapter") return (
          <div key={it.id}
            draggable onDragStart={()=>onDragStart(i)} onDragOver={e=>onDragOver(e,i)} onDragEnd={onDragEnd}
            style={{
              padding:"8px 4px", marginTop: i>0 ? 14 : 0,
              fontSize:12, fontWeight:800, color:"var(--ink)", cursor:"grab",
              display:"flex", gap:8, alignItems:"center",
              opacity: draggedIdx===i ? 0.4 : 1,
            }}>
            <span style={{ color:"var(--muted)", fontSize:11 }}>⋮⋮</span>
            <Icon name="file" size={14} color="var(--muted)"/>
            <span style={{ overflow:"hidden", whiteSpace:"nowrap", textOverflow:"ellipsis" }}>{it.label}</span>
          </div>
        );

        const active = selected===it.id;
        const stateKey = it.state ?? "wait";
        const stateBadge = ({
          done: { bg:palette.soft, fg:palette.primary, tx:<Icon name="check" size={10} /> },
          gen: { bg:"var(--honey)", fg:"var(--honey-ink)", tx:"⟳ 생성중" },
          wait: { bg:"var(--sand)", fg:"var(--ink-3)", tx:"▸ 대기" },
        } as Record<string, { bg: string; fg: string; tx: React.ReactNode }>)[stateKey] ?? { bg:"var(--sand)", fg:"var(--ink-3)", tx:"▸ 대기" };

        return (
          <div key={it.id}
            draggable onDragStart={()=>onDragStart(i)} onDragOver={e=>onDragOver(e,i)} onDragEnd={onDragEnd}
            onClick={()=>setSelected(it.id)} className="lift"
            style={{
              padding:"10px 12px", marginLeft:16, marginTop:4, borderRadius:8,
              background: active ? "var(--honey-wash)" : "transparent",
              border: active ? `1px solid var(--honey-soft)` : "1px solid transparent",
              display:"flex", gap:8, alignItems:"center", cursor:"grab",
              opacity: draggedIdx===i ? 0.4 : 1,
            }}>
            <span style={{ color:"var(--muted)", fontSize:10 }}>⋮⋮</span>
            <span style={{ flex:1, fontSize:12, fontWeight: active ? 700 : 500, overflow:"hidden", whiteSpace:"nowrap", textOverflow:"ellipsis" }}>{it.label}</span>
            <span style={{ padding:"2px 6px", borderRadius:6, background:stateBadge.bg, color:stateBadge.fg, fontSize:12, fontWeight:800 }}>{stateBadge.tx}</span>
          </div>
        );
      })}
    </aside>
  );
}
