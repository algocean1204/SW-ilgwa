// 랜딩 기능 소개 섹션. 사용자가 매일 보는 학습 흐름 위주로 보여 준다.
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import { SpotlightCard } from '@/components/animated';

interface LandingFeaturesProps {
  palette: Palette;
}

// 학습 기억 기능을 강조하는 카드. 진한 면과 얇은 선만 쓴다.
interface FeaturedCardProps {
  palette: Palette;
}

function FeaturedCard({ palette }: FeaturedCardProps) {
  return (
    <div style={{
      padding: "32px 36px",
      borderRadius: 18,
      background: palette.primary,
      color: "var(--paper)",
      position: "relative",
      overflow: "hidden",
      display: "flex",
      alignItems: "center",
      gap: 32,
      marginBottom: 16,
    }}>
      {/* 우측 선 장식. 노트 기준선처럼 보이게 한다 */}
      <div style={{
        position: "absolute", right: 36, top: 0, bottom: 0,
        width: 1,
        background: "var(--white-overlay-md)",
        pointerEvents: "none",
      }} />
      <div style={{
        position: "absolute", right: 64, top: 0, bottom: 0,
        width: 1,
        background: "var(--white-overlay-sm)",
        pointerEvents: "none",
      }} />

      {/* 아이콘 영역 */}
      <div style={{
        width: 56, height: 56, borderRadius: 16, flexShrink: 0,
        background: "var(--white-overlay-md)",
        display: "grid", placeItems: "center",
      }}>
        <Icon name="book" size={26} color="var(--paper)" strokeWidth={1.6} />
      </div>

      {/* 텍스트 영역 */}
      <div style={{ position: "relative", flex: 1 }}>
        {/* eyebrow 레이블 */}
        <div style={{
          fontSize: 11, fontWeight: 700, letterSpacing: ".14em",
          textTransform: "uppercase", opacity: 0.7, marginBottom: 8,
        }}>
          SIGNATURE FEATURE
        </div>
        <div style={{
          fontSize: 22, fontWeight: 800,
          letterSpacing: 0, lineHeight: 1.25, marginBottom: 8,
        }}>
          SHM · 나를 기억하는 개인 수업
        </div>
        <div style={{ fontSize: 13, lineHeight: 1.7, opacity: 0.85, maxWidth: 520 }}>
          내가 틀린 개념, 자주 묻는 질문, 학습 속도가 개인 기억에 쌓입니다.
          관찰당하는 느낌 없이, 단 둘이 공부하는 환경이에요.
        </div>
      </div>
    </div>
  );
}

// 보조 기능 카드. SpotlightCard라 hover 시 테두리와 배경색이 바뀐다.
interface SubFeatureProps {
  title: string;
  desc: string;
  icon: string;
  palette: Palette;
  // 3열 격자가 단조롭지 않게 일부 카드를 더 높게 한다
  tall?: boolean;
}

function SubFeature({ title, desc, icon, palette, tall = false }: SubFeatureProps) {
  return (
    <SpotlightCard
      style={{
        padding: "20px 22px",
        borderRadius: 16,
        background: "var(--paper)",
        border: "1px solid var(--border-default)",
        minHeight: tall ? 150 : 120,
        display: "flex",
        flexDirection: "column",
        gap: 10,
      }}
      opacity={0.05}
      radius={180}
    >
      {/* 아이콘 */}
      <div style={{
        width: 38, height: 38, borderRadius: 10,
        background: palette.wash, flexShrink: 0,
        display: "grid", placeItems: "center",
      }}>
        <Icon name={icon} size={18} color={palette.primary} strokeWidth={1.8} />
      </div>

      <div>
        <div style={{ fontSize: 14, fontWeight: 800, color: "var(--text-primary)", letterSpacing: 0 }}>
          {title}
        </div>
        <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 4, lineHeight: 1.6 }}>
          {desc}
        </div>
      </div>
    </SpotlightCard>
  );
}

function LandingFeatures({ palette }: LandingFeaturesProps) {
  return (
    // var(--sand) 배경과 위아래 border로 앞뒤 섹션과 구분한다
    <section style={{
      background: "var(--sand)",
      padding: "56px 40px",
      borderTop: "1px solid var(--border-soft)",
      borderBottom: "1px solid var(--border-soft)",
    }}>
      <div style={{ maxWidth: 1100, margin: "0 auto" }}>
        {/* 섹션 헤더 */}
        <div style={{ marginBottom: 28 }}>
          <div style={{
            fontSize: 11, fontWeight: 700, color: "var(--text-tertiary)",
            letterSpacing: ".14em", textTransform: "uppercase", marginBottom: 6,
          }}>
            FEATURES
          </div>
          <h2 style={{
            margin: 0, fontSize: 28, fontWeight: 800,
        color: "var(--text-primary)", letterSpacing: 0,
      }}>
            매일 이어지는 수업 흐름
          </h2>
        </div>

        {/* SHM 강조 카드. 섹션 상단에 전폭으로 둔다 */}
        <FeaturedCard palette={palette} />

        {/* 보조 기능 3개. 3열로 두고 tall로 높이를 달리한다 */}
        <div className="landing-feature-grid">
          <SubFeature
            palette={palette}
            icon="mic"
            title="음성으로 대화하는 수업"
            desc="마이크로 질문하고, 수업 맥락 안에서 바로 이어서 설명을 듣습니다"
            tall
          />
          <SubFeature
            palette={palette}
            icon="note"
            title="노트와 암기카드 정리"
            desc="강의 핵심을 노트로 저장하고 암기카드로 변환합니다"
          />
          <SubFeature
            palette={palette}
            icon="flag"
            title="소크라테스 Q&A"
            desc="정답을 바로 주지 않고 스스로 찾아가도록 이끌어줍니다"
            tall
          />
        </div>
      </div>
    </section>
  );
}

export { LandingFeatures };
