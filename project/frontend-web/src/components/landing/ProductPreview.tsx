// 학습 화면 미리보기. 교재, 수업, 과제, 복습이 한 화면에 이어지는 모습을 보여 준다.
// 색은 디자인 토큰만 쓰고 hex를 직접 쓰지 않는다.

interface ProductPreviewProps {
  primaryColor: string;
  washColor: string;
}

// 미리보기용 커리큘럼 챕터 목록
const CHAPTERS = [
  { n: "01", title: "세포의 구조와 기능",  mins: 18, done: true  },
  { n: "02", title: "광합성의 원리",        mins: 22, done: true  },
  { n: "03", title: "세포 호흡과 에너지",   mins: 20, done: false, active: true },
  { n: "04", title: "유전과 진화",          mins: 25, done: false },
];
const PROGRESS_PCT = Math.round(CHAPTERS.filter(c => c.done).length / CHAPTERS.length * 100);

function ProductPreview({ primaryColor, washColor }: ProductPreviewProps) {
  return (
    // 작업 화면 프레임
    <div style={{
      maxWidth: 780, margin: "0 auto",
      background: "var(--surface-card)", border: "1px solid var(--border-default)",
      borderRadius: 18, boxShadow: "var(--shadow-lg)",
      overflow: "hidden", userSelect: "none", pointerEvents: "none",
    }}>
      {/* macOS 스타일 상단 크롬 바 */}
      <div style={{
        display:"flex", alignItems:"center", gap:6, padding:"10px 14px",
        background:"var(--surface-subtle)", borderBottom:"1px solid var(--border-soft)",
      }}>
        <div style={{ width:10, height:10, borderRadius:5, background:"var(--chrome-dot-close)" }} />
        <div style={{ width:10, height:10, borderRadius:5, background:"var(--chrome-dot-minimise)" }} />
        <div style={{ width:10, height:10, borderRadius:5, background:"var(--chrome-dot-expand)" }} />
        {/* URL 바 */}
        <div style={{
          flex:1, marginLeft:10, padding:"3px 10px",
          background:"var(--surface-card)", borderRadius:6, border:"1px solid var(--border-soft)",
          fontSize:9, color:"var(--text-tertiary)", textAlign:"center",
        }}>
          ilkwa.study · 생물 심화 과정
        </div>
      </div>

      {/* 2단 레이아웃: 왼쪽(커리큘럼 목록) + 오른쪽(현재 수업) */}
      <div style={{ display:"flex" }}>
        {/* 왼쪽: 자동 구성 커리큘럼 패널 */}
        <div style={{ width:220, borderRight:"1px solid var(--border-soft)", padding:"14px 12px", flexShrink:0 }}>
          {/* 자동 구성 배지 */}
          <div style={{ display:"inline-flex", alignItems:"center", gap:4, padding:"2px 8px", borderRadius:20, background:washColor, marginBottom:10 }}>
            <div style={{ width:5, height:5, borderRadius:3, background:primaryColor }} />
            <span style={{ fontSize:9, fontWeight:800, color:primaryColor }}>자동 구성 커리큘럼</span>
          </div>
          <div style={{ fontSize:12, fontWeight:800, color:"var(--text-primary)", letterSpacing:0, marginBottom:4, lineHeight:1.3 }}>
            생물 심화<br/>4주 과정
          </div>
          {/* 진행률 바 */}
          <div style={{ marginBottom:12 }}>
            <div style={{ display:"flex", justifyContent:"space-between", fontSize:9, color:"var(--text-tertiary)", marginBottom:4 }}>
              <span>진행률</span>
              <span style={{ fontWeight:700, color:primaryColor }}>{PROGRESS_PCT}%</span>
            </div>
            <div style={{ height:4, background:"var(--border-soft)", borderRadius:2 }}>
              <div style={{ height:4, borderRadius:2, background:primaryColor, width:`${PROGRESS_PCT}%` }} />
            </div>
          </div>
          {/* 챕터 목록 */}
          <div style={{ display:"flex", flexDirection:"column", gap:3 }}>
            {CHAPTERS.map((ch) => (
              <div key={ch.n} style={{
                display:"flex", alignItems:"center", gap:7, padding:"6px 8px", borderRadius:7,
                background: ch.active ? washColor : "transparent",
                borderLeft: ch.active ? `2px solid ${primaryColor}` : "2px solid transparent",
              }}>
                {/* 완료/진행 상태 인디케이터 */}
                <div style={{
                  width:14, height:14, borderRadius:7, flexShrink:0,
                  background: ch.done ? primaryColor : ch.active ? washColor : "var(--border-soft)",
                  border: ch.active && !ch.done ? `1.5px solid ${primaryColor}` : "none",
                  display:"grid", placeItems:"center",
                }}>
                  {ch.done && <div style={{ width:5, height:5, borderRadius:1, background:"var(--paper)" }} />}
                </div>
                <div style={{ flex:1, minWidth:0 }}>
                  <div style={{
                    fontSize:10, fontWeight: ch.active ? 700 : 500,
                    color: ch.active ? primaryColor : ch.done ? "var(--text-tertiary)" : "var(--text-secondary)",
                    lineHeight:1.3, overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap",
                  }}>
                    {ch.n}강 · {ch.title}
                  </div>
                  <div style={{ fontSize:8, color:"var(--text-tertiary)" }}>{ch.mins}분</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* 오른쪽: 현재 강의 상세 패널 */}
        <div style={{ flex:1, padding:"14px 18px", minWidth:0 }}>
          {/* 챕터 헤더 */}
          <div style={{ display:"flex", alignItems:"center", justifyContent:"space-between", marginBottom:10 }}>
            <div>
              <div style={{ fontSize:9, fontWeight:700, color:primaryColor, letterSpacing:".1em" }}>3강 · 학습 중</div>
              <div style={{ fontSize:13, fontWeight:800, color:"var(--text-primary)", letterSpacing:0 }}>세포 호흡과 에너지</div>
            </div>
            {/* 음성 수업 진행 중 배지 */}
            <div style={{ display:"flex", alignItems:"center", gap:4, padding:"3px 9px", borderRadius:20, background:washColor }}>
              <div style={{ width:6, height:6, borderRadius:3, background:primaryColor }} />
              <span style={{ fontSize:9, fontWeight:700, color:primaryColor }}>음성 수업</span>
            </div>
          </div>
          {/* 현재 강의 슬라이드 블록 */}
          <div style={{ background:"var(--surface-subtle)", borderRadius:8, padding:"10px 12px", marginBottom:10 }}>
            <div style={{ height:7, width:"50%", borderRadius:3, background:primaryColor, opacity:0.3, marginBottom:7 }} />
            {[75, 60, 68, 50].map((w, i) => (
              <div key={i} style={{ height:5, width:`${w}%`, borderRadius:2, background:"var(--border-default)", marginBottom:4, opacity: i === 3 ? 0.4 : 0.7 }} />
            ))}
          </div>
          {/* 튜터 음성 응답 말풍선 */}
          <div style={{ display:"flex", gap:8, alignItems:"flex-start" }}>
            <div style={{ width:22, height:22, borderRadius:11, background:primaryColor, flexShrink:0, display:"grid", placeItems:"center" }}>
              <span style={{ fontSize:9, fontWeight:800, color:"var(--paper)" }}>쌤</span>
            </div>
            <div style={{ flex:1, fontSize:10, color:"var(--text-secondary)", lineHeight:1.55, background:"var(--surface-card)", borderRadius:"0 8px 8px 8px", padding:"6px 10px", border:"1px solid var(--border-soft)" }}>
              세포 호흡은 포도당을 분해해 <b style={{ color:"var(--text-primary)" }}>ATP</b>를 생성하는 과정입니다. 미토콘드리아 내막에서 진행되며…
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export { ProductPreview };
