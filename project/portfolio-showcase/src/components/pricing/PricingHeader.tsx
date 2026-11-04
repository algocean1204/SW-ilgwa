// 요금제 페이지 상단 헤더. 플랜을 고르는 기준을 제목과 설명으로 보여준다.
import type { Palette } from '@/lib';
import PageIntro from '@/components/ui/PageIntro';

interface PricingHeaderProps {
  // script 폰트 색에 쓰는 현재 팔레트
  palette: Palette;
}

export function PricingHeader({ palette }: PricingHeaderProps) {
  return (
    <div style={{ textAlign: "left", marginBottom: 18 }}>
      <PageIntro
        eyebrow="PRICING"
        title={
          <>
            <span className="script" style={{ fontSize: 42, color: palette.primary }}>꾸준히</span>{" "}
            공부할 수 있는 요금제
          </>
        }
        description={
          <>
            {/* 플랜 선택 기준 한 문장 */}
            대부분의 학습자에게는{" "}
            <b style={{ color: "var(--text-primary)" }}>Pro</b>가 맞아요.
            텔레그램 과제·채점, 튜터 커스터마이징, 학습 리포트가 한 번에 열려요.
          </>
        }
      />
    </div>
  );
}
