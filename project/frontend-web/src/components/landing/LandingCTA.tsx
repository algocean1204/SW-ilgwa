// 랜딩 마지막 "오늘 시작" CTA 배너와 저작권 푸터
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import { ShimmerButton } from '@/components/animated';

interface LandingCTAProps {
  palette: Palette;
  setRoute: RouteSetter;
}

function LandingCTA({ palette, setRoute }: LandingCTAProps) {
  return (
    <>
      {/* CTA 배너 */}
      <section style={{ padding:"20px 40px 80px" }}>
        <div style={{
          maxWidth:960, margin:"0 auto",
          padding:"40px 48px", borderRadius:18,
          background:palette.primary,
          color:"var(--paper)", position:"relative", overflow:"hidden",
          display:"flex", alignItems:"center", gap:30,
        }}>
          {/* 카피 영역 */}
          <div style={{ position:"relative", flex:1 }}>
            <div style={{ fontSize:12, fontWeight:800, letterSpacing:".1em", opacity:.8 }}>START TODAY</div>
            <div style={{ fontSize:28, fontWeight:800, marginTop:6, letterSpacing:0, lineHeight:1.2 }}>
              교재가 있으면 더 빠르게,<br/>없어도 오늘 바로 시작할 수 있습니다.
            </div>
          </div>

          {/* 진한 배경 위라서 버튼은 var(--paper) 배경을 쓴다 */}
          <ShimmerButton onClick={() => setRoute("auth")} className="lift" style={{
            padding:"16px 28px", borderRadius:12, fontSize:14, fontWeight:800,
            background:"var(--paper)", color:palette.primary,
            boxShadow:"0 20px 40px -20px rgba(22,59,54,.5)",
          }}>학습 시작하기</ShimmerButton>
        </div>
      </section>

      {/* 사이트 저작권 푸터 */}
      <footer style={{
        padding:"24px 40px", textAlign:"center",
        fontSize:12, color:"var(--ink-3)",
        borderTop:"1px solid var(--line-2)",
      }}>
        © 2026 일과 ilkwa · 교재와 주제를 연결하는 개인 학습 플랫폼
      </footer>
    </>
  );
}

export { LandingCTA };
