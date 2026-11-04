// 랜딩의 주제 입력 진입 패널. textarea, 예시 칩, 커리큘럼 생성 CTA로 구성된다.
import { useState } from 'react';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';

interface EntryProps {
  palette: Palette;
  setRoute: RouteSetter;
}

// 입력 예시로 보여 줄 주제 칩
const EXAMPLES = [
  "중학교 3학년 이차함수 기초부터",
  "중학교 1학년 정수와 유리수",
  "고등학교 1학년 일차방정식",
  "중학교 2학년 연립방정식",
];

function TopicEntry({ palette, setRoute }: EntryProps) {
  const [v, setV] = useState("");

  return (
    <>
      {/* 주제 입력 텍스트에어리어 */}
      <textarea
        value={v}
        onChange={e => setV(e.target.value)}
        placeholder="예) 중학교 3학년 이차함수, 기초부터 심화까지 30일 과정으로"
        style={{
          width:"100%", minHeight:96, resize:"none",
          padding:"14px 16px", borderRadius:12, border:"1.5px solid var(--line)",
          fontSize:14, color:"var(--ink)", background:"var(--paper)",
          fontFamily:"inherit", lineHeight:1.6, outline:"none",
        }}
        onFocus={e => e.target.style.borderColor = palette.primary}
        onBlur={e => e.target.style.borderColor = "var(--line)"}
      />

      {/* 예시 주제 칩 버튼 행 */}
      <div style={{ display:"flex", alignItems:"center", gap:8, marginTop:10, flexWrap:"wrap" }}>
        <span style={{ fontSize:12, color:"var(--ink-3)", fontWeight:600 }}>예시:</span>
        {EXAMPLES.map(ex => (
          <button key={ex} onClick={() => setV(ex)} style={{
            fontSize:12, padding:"4px 9px", borderRadius:999,
            background:"var(--bg)", border:"1px solid var(--line)",
            color:"var(--ink-2)", fontWeight:600,
          }} className="lift">{ex}</button>
        ))}
      </div>

      {/* 커리큘럼 생성 CTA. 누르면 로그인 화면으로 보낸다 */}
      <button onClick={() => setRoute("auth")} className="lift" style={{
        width:"100%", marginTop:14, padding:"13px 18px", borderRadius:11,
        background:palette.primary,
        color:"var(--paper)", fontSize:13, fontWeight:800,
        boxShadow:`0 10px 24px -10px ${palette.primary}`,
      }}>커리큘럼 만들기</button>

      {/* 낮은 진입 장벽 안내 */}
      <div style={{ fontSize:12, color:"var(--ink-3)", marginTop:8, textAlign:"center" }}>
        한 줄만 써도 단계와 분량을 맞춰 구체화합니다
      </div>
    </>
  );
}

export { TopicEntry };
