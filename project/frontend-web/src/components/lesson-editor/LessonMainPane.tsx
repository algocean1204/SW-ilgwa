// 선택된 레슨의 메인 콘텐츠 패널. SDLC 다이어그램과 레슨 개요 표를 보여주며 상태는 없다.
import { Chip } from '@/lib';
import type { Palette } from '@/lib';
import Icon from '@/Icon';

interface LessonMainPaneProps {
  palette: Palette;
}

// SDLC 다이어그램 노드의 위치와 레이블
const SDLC_NODES = [
  { x:"24%", y:"24%", label:"계획" },
  { x:"50%", y:"10%", label:"분석" },
  { x:"76%", y:"24%", label:"설계" },
  { x:"20%", y:"72%", label:"구현" },
  { x:"50%", y:"84%", label:"테스트" },
  { x:"80%", y:"72%", label:"배포" },
];

// 각 노드에서 중심으로 잇는 선 좌표
const SDLC_LINES: [number, number][] = [
  [120, 60], [250, 40], [380, 60],
  [100, 170], [250, 200], [400, 170],
];

const TABLE_ROWS: [string, string][] = [
  ["소프트웨어란 무엇인가", "컴퓨터 시스템에서 프로그램, 데이터, 처리된 정보 전체를 의미"],
  ["컴퓨터의 구성 요소는", "하드웨어 + 소프트웨어 + 휴먼웨어"],
  ["공학(Engineering)의 핵심은", "체계적·정량적 접근으로 품질·비용·일정 관리"],
];

export function LessonMainPane({ palette }: LessonMainPaneProps){
  return (
    <main style={{ padding:"20px 24px" }}>
      <div style={{ display:"flex", gap:8, marginBottom:14 }}>
        <Chip tone="grass" active>SDLC 개요와 소프트웨어 공학의 핵심 개념</Chip>
        <div style={{ flex:1 }} />
        <Chip tone="ghost">다시 구성</Chip>
        <Chip tone="ghost"><><Icon name="settings" size={12} /> 옵션</></Chip>
      </div>

      <div style={{ background:"var(--paper)", borderRadius:16, border:"1px solid var(--line)", padding:"24px 28px", boxShadow:"var(--shadow-sm)" }}>
        <div style={{ fontSize:12, fontWeight:800, color:"var(--muted)", letterSpacing:".08em", textTransform:"uppercase" }}>플래너 노드 · SDLC</div>

        {/* SDLC 다이어그램. 중심 원과 6개 노드를 점선으로 잇는다 */}
        <div style={{ position:"relative", height:240, marginTop:10 }} className="dots">
          <div style={{ position:"absolute", inset:0 }}>
            <svg width="100%" height="100%" viewBox="0 0 500 240" style={{ position:"absolute", inset:0 }}>
              {SDLC_LINES.map(([x,y],i)=>(
                <line key={i} x1={x} y1={y} x2={250} y2={120} stroke={palette.primary2} strokeWidth="1.5" strokeDasharray="3 5" opacity=".4" />
              ))}
            </svg>
          </div>

          {/* 중심 SDLC 원 */}
          <div style={{
            position:"absolute", left:"50%", top:"50%", transform:"translate(-50%,-50%)",
            width:80, height:80, borderRadius:"50%", background:palette.soft,
            display:"grid", placeItems:"center", boxShadow:"0 8px 22px -4px rgba(32,123,76,.35)",
          }}>
            <div style={{ fontWeight:900, fontSize:14, color:palette.primary }}>SDLC</div>
          </div>

          {/* 6개 SDLC 단계 노드 */}
          {SDLC_NODES.map((n, i)=>(
            <div key={i} className="float" style={{
              position:"absolute", left:n.x, top:n.y, transform:"translate(-50%,-50%)",
              width:46, height:46, borderRadius:"50%", background:"var(--paper)",
              border:`2px solid ${palette.primary2}`, display:"grid", placeItems:"center",
              fontSize:12, fontWeight:700, color:palette.primary,
              animationDelay:(i*.3)+"s",
            }}>{n.label}</div>
          ))}
        </div>

        <h2 style={{ margin:"18px 0 6px", fontSize:22, fontWeight:800, letterSpacing:"-.02em" }}>SDLC 개요와 소프트웨어 공학의 핵심 개념</h2>
        <p style={{ margin:0, fontSize:13, color:"var(--ink-3)", lineHeight:1.6 }}>
          이 레슨은 핵심 개념을 질문-답변 구조, 표, 연결 고리 중심으로 구성하였다.
          비전공자 및 초학습자가 용어 간 관계를 한눈에 파악하는 것을 목표로 한다.
        </p>

        <h3 style={{ fontSize:14, fontWeight:800, marginTop:18, marginBottom:10 }}>1. 소프트웨어와 소프트웨어 공학은 무엇인가</h3>
        <div style={{ border:"1px solid var(--line)", borderRadius:10, overflow:"hidden" }}>
          <div style={{ display:"grid", gridTemplateColumns:"1fr 1.3fr", background:"var(--sand)", padding:"10px 14px", fontSize:12, fontWeight:800, color:"var(--ink-3)" }}>
            <div>질문</div><div>답변</div>
          </div>
          {TABLE_ROWS.map(([q, a], i)=>(
            // 행마다 var(--paper)와 var(--bg)를 번갈아 쓴다
            <div key={i} style={{ display:"grid", gridTemplateColumns:"1fr 1.3fr", padding:"12px 14px", fontSize:12, borderTop: i ? "1px solid var(--line)" : "none", background: i%2 ? "var(--bg)" : "var(--paper)" }}>
              <div style={{ fontWeight:600 }}>{q}</div>
              <div style={{ color:"var(--ink-2)" }}>{a}</div>
            </div>
          ))}
        </div>
      </div>
    </main>
  );
}
