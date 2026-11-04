// PDF·주제 탭을 전환하는 컨테이너. 패널(PdfEntry, TopicEntry)은 children으로 받는다.
// hero보다 눈에 덜 띄게 그림자와 배경을 약하게 둔다.
import type { Palette } from '@/lib';

interface DualEntryCardProps {
  palette: Palette;
  // PdfEntry와 TopicEntry가 형제라서 탭 상태는 부모가 가진다
  mode: "pdf" | "topic";
  setMode: (m: "pdf" | "topic") => void;
  children: React.ReactNode;
}

function DualEntryCard({ palette, mode, setMode, children }: DualEntryCardProps) {
  return (
    <div style={{
      position:"relative", maxWidth:720, margin:"40px auto 0",
      padding:6, background:"var(--paper)", borderRadius:20,
      border:"1px solid var(--line)",
      boxShadow:"var(--shadow-sm)",
    }}>
      {/* 탭 토글 행 */}
      <div style={{
        display:"grid", gridTemplateColumns:"1fr 1fr", gap:4, padding:4,
        background:"var(--bg)", borderRadius:14,
      }}>
        {/* PDF 탭 버튼 */}
        <button onClick={() => setMode("pdf")} style={{
          padding:"12px 16px", borderRadius:11, fontSize:13, fontWeight:800,
          display:"flex", alignItems:"center", justifyContent:"center", gap:8,
          background: mode === "pdf" ? "var(--paper)" : "transparent",
          color: mode === "pdf" ? palette.primary : "var(--ink-3)",
          boxShadow: mode === "pdf" ? "0 4px 12px -6px rgba(34,50,46,.12)" : "none",
          transition:"all .2s",
        }}>
          <span>PDF 업로드로 시작</span>
        </button>

        {/* 주제 입력 탭 버튼 */}
        <button onClick={() => setMode("topic")} style={{
          padding:"12px 16px", borderRadius:11, fontSize:13, fontWeight:800,
          display:"flex", alignItems:"center", justifyContent:"center", gap:8,
          background: mode === "topic" ? "var(--paper)" : "transparent",
          color: mode === "topic" ? palette.primary : "var(--ink-3)",
          boxShadow: mode === "topic" ? "0 4px 12px -6px rgba(34,50,46,.12)" : "none",
          transition:"all .2s",
        }}>
          <span>주제만 입력해서 시작</span>
        </button>
      </div>

      {/* 활성 패널 콘텐츠 슬롯 */}
      <div style={{ padding:"22px 20px 18px", textAlign:"left" }}>
        {children}
      </div>
    </div>
  );
}

export { DualEntryCard };
