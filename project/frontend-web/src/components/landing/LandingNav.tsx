// 랜딩 상단 내비게이션. 로고, 요금 링크, 로그인, 무료 시작 CTA만 둔다.
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';

interface LandingNavProps {
  palette: Palette;
  setRoute: RouteSetter;
}

function LandingNav({ palette, setRoute }: LandingNavProps) {
  return (
    <nav style={{
      position:"sticky", top:0, zIndex:10,
      display:"flex", alignItems:"center",
      padding:"0 48px",
      height: 56,
      // 반투명 배경과 블러로 첫 화면 위에 고정한다
      background:"var(--nav-blur-bg)", backdropFilter:"blur(16px)",
      borderBottom:"1px solid var(--line-2)",
    }}>
      {/* 브랜드 로고 영역 */}
      <div style={{ display:"flex", alignItems:"center", gap:10 }}>
        <div style={{
          width:30, height:30, borderRadius:8,
          background:palette.primary,
          color:"var(--paper)", fontSize:14, fontWeight:800, display:"grid", placeItems:"center",
        }}>일</div>
        <span style={{ fontSize:15, fontWeight:850, color:"var(--ink)", letterSpacing:0 }}>일과</span>
        {/* 영문 표기는 보조 식별자 수준으로 낮춘다 */}
        <span style={{ fontSize:11, color:"var(--ink-3)", fontWeight:600, marginLeft:2 }}>ilkwa</span>
      </div>

      <div style={{ flex:1 }} />

      {/* 요금 링크 */}
      <button
        onClick={() => setRoute("pricing")}
        style={{
          fontSize:13, fontWeight:600, color:"var(--ink-2)",
          background:"none", border:"none", cursor:"pointer",
          padding:"8px 16px", borderRadius:8,
        }}
      >
        요금
      </button>

      {/* 로그인. 텍스트 버튼으로 약하게 둔다 */}
      <button
        onClick={() => setRoute("auth")}
        style={{
          fontSize:13, fontWeight:600, color:"var(--ink-3)",
          background:"none", border:"none", cursor:"pointer",
          padding:"8px 16px", borderRadius:8,
        }}
      >
        로그인
      </button>

      {/* 무료 시작 CTA */}
      <button
        onClick={() => setRoute("auth")}
        className="lift"
        style={{
          padding:"8px 18px", borderRadius:9, fontSize:13, fontWeight:800,
          background:palette.primary,
          color:"var(--paper)",
          border:"none", cursor:"pointer",
          marginLeft:8,
        }}
      >
        학습 시작
      </button>
    </nav>
  );
}

export { LandingNav };
