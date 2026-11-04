// "HOW IT WORKS" 4단계 섹션. LandingFeatures 카드 그리드와 구분되게 가로 타임라인으로 그린다.
// 단계 사이는 점선으로 잇고, 각 단계는 번호, 아이콘, 제목, 설명 순으로 쌓는다.
import Icon from '@/Icon';
import type { Palette } from '@/lib';

interface HowItWorksProps {
  palette: Palette;
}

// 단계 순서는 이 배열 순서를 따른다
const STEPS = [
  { n: "01", i: "upload",   t: "교재 또는 주제",       d: "PDF를 올리거나 배우고 싶은 주제만 입력" },
  { n: "02", i: "map",      t: "커리큘럼 구성",          d: "챕터·슬라이드·음성 수업 흐름 자동 정리" },
  { n: "03", i: "mic",      t: "1:1 음성 강의",         d: "슬라이드+TTS 동기화 · 소크라테스 Q&A" },
  { n: "04", i: "telegram", t: "과제·채점 기록",        d: "DM으로 과제 수신 · 제출과 피드백 저장" },
] as const;

// 타임라인 한 단계
interface TimelineStepProps {
  step: typeof STEPS[number];
  palette: Palette;
  isLast: boolean;
}

function TimelineStep({ step, palette, isLast }: TimelineStepProps) {
  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", textAlign: "center" }}>
      {/* 상단 행: 아이콘 원 + 연결 점선 */}
      <div style={{ display: "flex", alignItems: "center", width: "100%", marginBottom: 20 }}>
        {/* 스텝 번호 + 아이콘 원 */}
        <div style={{ flexShrink: 0, display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
          {/* 번호 레이블 */}
          <div style={{
            fontSize: 11, fontWeight: 800, color: palette.primary,
            letterSpacing: ".15em",
          }}>
            {step.n}
          </div>
          {/* 아이콘 원 */}
          <div style={{
            width: 52, height: 52, borderRadius: "50%",
            background: palette.wash,
            border: `2px solid ${palette.soft}`,
            display: "grid", placeItems: "center",
          }}>
            <Icon name={step.i} size={22} color={palette.primary} strokeWidth={1.7} />
          </div>
        </div>

        {/* 연결 점선. 마지막 단계에는 그리지 않는다 */}
        {!isLast && (
          <div style={{
            flex: 1,
            height: 1,
            borderTop: `2px dashed ${palette.soft}`,
            marginTop: 28,
            opacity: 0.6,
          }} />
        )}
        {/* 마지막 단계는 여백으로 대체 */}
        {isLast && <div style={{ flex: 1 }} />}
      </div>

      {/* 텍스트 영역 */}
      <div style={{ paddingRight: isLast ? 0 : 24, width: "100%" }}>
        <div style={{
          fontSize: 14, fontWeight: 800, color: "var(--text-primary)",
          letterSpacing: 0, marginBottom: 6, lineHeight: 1.3,
        }}>
          {step.t}
        </div>
        <div style={{ fontSize: 12, color: "var(--text-tertiary)", lineHeight: 1.6 }}>
          {step.d}
        </div>
      </div>
    </div>
  );
}

function HowItWorks({ palette }: HowItWorksProps) {
  return (
    // 앞뒤 섹션(sand, surface-subtle)과 구분되게 배경색을 두지 않는다
    <section style={{ padding: "64px 40px", maxWidth: 1100, margin: "0 auto" }}>
      {/* 섹션 헤더 */}
      <div style={{ textAlign: "center", marginBottom: 48 }}>
        <div style={{
          fontSize: 11, fontWeight: 700, color: "var(--text-tertiary)",
          letterSpacing: ".15em", textTransform: "uppercase", marginBottom: 10,
        }}>
          HOW IT WORKS
        </div>
        <h2 style={{
          margin: 0, fontSize: 30, fontWeight: 850,
          color: "var(--text-primary)", letterSpacing: 0,
        }}>
          한 번 만든 흐름이 매일 이어집니다
        </h2>
      </div>

      {/* 가로 타임라인 */}
      <div className="landing-step-grid">
        {STEPS.map((step, idx) => (
          <TimelineStep
            key={step.n}
            step={step}
            palette={palette}
            isLast={idx === STEPS.length - 1}
          />
        ))}
      </div>
    </section>
  );
}

export { HowItWorks };
