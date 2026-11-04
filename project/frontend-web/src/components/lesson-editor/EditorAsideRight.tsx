// 에디터 우측 보조 패널. 생성 상태, 아웃라인, 레슨 설정, 암기노트, 강사 정보를 보여준다.
import type { Palette } from '@/lib';
import { LE_Row, LE_Pill } from './EditorPrimitives';
import Icon from '@/Icon';

interface EditorAsideRightProps {
  palette: Palette;
}

const OUTLINE_ITEMS: [string, string, boolean][] = [
  ["1.", "소프트웨어와 소프트웨어 공학", true],
  ["2.", "SDLC의 여섯 단계", true],
  ["3.", "프로세스 모델 비교: 폭포수 vs 애자일", false],
  ["4.", "핵심 용어 요약표", false],
  ["5.", "학습 확인 문제", false],
];

const MEMO_ITEMS: [string, string][] = [
  ["핵심", "SDLC 6단계"],
  ["산출물", "SRS의 정의와 구성"],
  ["비교", "폭포수 vs 애자일"],
];

/** 우측 패널 섹션 소제목 */
function SectionLabel({ children }: { children: React.ReactNode }){
  return (
    <div style={{ fontSize:12, fontWeight:800, color:"var(--muted)", letterSpacing:".08em", textTransform:"uppercase", margin:"20px 0 8px" }}>
      {children}
    </div>
  );
}

export function EditorAsideRight({ palette }: EditorAsideRightProps){
  return (
    <aside style={{ padding:"20px", background:"var(--bg)", borderLeft:"1px solid var(--line)" }}>

      {/* 생성 상태 섹션 */}
      <div style={{ fontSize:12, fontWeight:800, color:"var(--muted)", letterSpacing:".08em", textTransform:"uppercase", marginBottom:8 }}>생성 상태</div>
      <div style={{ background:"var(--paper)", borderRadius:12, border:"1px solid var(--line)", padding:"14px 14px" }}>
        <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:10 }}>
          <div style={{ width:28, height:28, borderRadius:"50%", background:palette.wash, display:"grid", placeItems:"center" }}><Icon name="owl" size={14} color={palette.primary} /></div>
          <div style={{ flex:1 }}>
            <div style={{ fontSize:12, fontWeight:700 }}>올빼미 플래너</div>
            <div style={{ fontSize:12, color:palette.primary, display:"flex", alignItems:"center", gap:4 }}>
              <span style={{ width:5, height:5, borderRadius:3, background:palette.primary2 }} /> 레슨 2개 생성 중
            </div>
          </div>
          <button style={{ padding:"4px 9px", borderRadius:6, fontSize:12, fontWeight:700, background:"var(--coral-wash)", color:"var(--coral-ink-deep)" }}>■ 멈춤</button>
        </div>
        <div style={{ height:5, background:"var(--sand)", borderRadius:3, overflow:"hidden" }}>
          <div style={{ width:"62%", height:"100%", background:palette.primary, borderRadius:3 }} />
        </div>
        <div style={{ display:"flex", justifyContent:"space-between", fontSize:12, color:"var(--ink-3)", marginTop:5 }}>
          <span>6 / 10 완료</span>
          <span className="mono">약 2분 남음</span>
        </div>
      </div>

      {/* 현재 레슨 아웃라인 섹션 */}
      <SectionLabel>현재 레슨 아웃라인</SectionLabel>
      <div style={{ background:"var(--paper)", borderRadius:12, border:"1px solid var(--line)", padding:"10px 4px" }}>
        {OUTLINE_ITEMS.map(([num, lbl, done], i)=>(
          <div key={i} style={{ display:"flex", gap:8, alignItems:"center", padding:"7px 12px", fontSize:12 }}>
            <span className="mono" style={{ fontSize:12, color:"var(--muted)", width:14 }}>{num}</span>
            <span style={{ flex:1, color: done ? "var(--ink)" : "var(--ink-3)", fontWeight: done ? 600 : 500 }}>{lbl}</span>
            {done && <Icon name="check" size={11} color={palette.primary} />}
          </div>
        ))}
      </div>

      {/* 레슨 설정 섹션 */}
      <SectionLabel>레슨 설정</SectionLabel>
      <div style={{ background:"var(--paper)", borderRadius:12, border:"1px solid var(--line)", padding:"12px 14px", display:"flex", flexDirection:"column", gap:10 }}>
        <LE_Row label="난이도"><LE_Pill active palette={palette}>초급</LE_Pill><LE_Pill palette={palette}>중급</LE_Pill><LE_Pill palette={palette}>고급</LE_Pill></LE_Row>
        <LE_Row label="예상 시간"><span style={{ fontSize:12, fontWeight:700 }}>12분</span></LE_Row>
        <LE_Row label="공개 범위"><span style={{ fontSize:12, fontWeight:600, color:palette.primary }}>● 수강생만</span></LE_Row>
        <LE_Row label="튜터"><span style={{ fontSize:12, fontWeight:600 }}>허용</span></LE_Row>
      </div>

      {/* 학생 암기노트 미리보기 섹션 */}
      <SectionLabel>학생 암기노트 미리보기</SectionLabel>
      <div style={{ background:"var(--paper)", borderRadius:12, border:"1px solid var(--line)", padding:"4px 0" }}>
        {MEMO_ITEMS.map(([tag, body], i)=>(
          <div key={i} style={{
            padding:"10px 12px", fontSize:12, color:"var(--ink-2)",
            borderTop: i ? "1px solid var(--line)" : "none",
            display:"flex", alignItems:"flex-start", gap:8,
          }}>
            <span style={{
              fontSize:12, fontWeight:700, padding:"2px 6px", borderRadius:4,
              background:palette.wash, color:palette.primary, border:`1px solid ${palette.soft}`,
              textTransform:"uppercase", letterSpacing:".04em", flexShrink:0, marginTop:1,
            }}>{tag}</span>
            <span style={{ lineHeight:1.4 }}>{body}</span>
          </div>
        ))}
        <div style={{ padding:"9px 12px", borderTop:"1px solid var(--line)", display:"flex", alignItems:"center", gap:8 }}>
          <span style={{ fontSize:12, color:"var(--ink-3)", flex:1 }}>이 레슨에서 <b style={{ color:palette.primary }}>8개</b> 자동 생성 예상</span>
          <span style={{ fontSize:12, color:"var(--telegram-blue)", fontWeight:600 }}>텔레그램</span>
        </div>
      </div>

      {/* 강사 정보 */}
      <div style={{ marginTop:22, padding:"12px 14px", borderRadius:12, background:"var(--paper)", border:"1px solid var(--line)", display:"flex", gap:10, alignItems:"center" }}>
        <div style={{ width:28, height:28, borderRadius:"50%", background:"var(--coral)", color:"var(--paper)", display:"grid", placeItems:"center", fontWeight:800, fontSize:11 }}>n</div>
        <div style={{ flex:1 }}>
          <div style={{ fontSize:12, fontWeight:700 }}>naooungdev</div>
          <div style={{ fontSize:12, color:"var(--ink-3)" }}>강사 · 정보처리기사 과정</div>
        </div>
        <button style={{ color:"var(--ink-3)" }}><Icon name="settings" size={14} /></button>
      </div>
    </aside>
  );
}
