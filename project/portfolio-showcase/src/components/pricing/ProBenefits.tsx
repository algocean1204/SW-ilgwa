// "Pro에서 바로 쓸 수 있는 것" 혜택 카드 목록.
// 플랜 카드 옆에서 기능별 이점을 문장으로 보여준다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';

// 서비스 기능별 혜택. 기능명보다 무엇이 달라지는지를 적는다.
// icon은 Icon.tsx의 아이콘 키
const BENEFITS = [
  {
    icon: "phone",
    t: "텔레그램 과제·채점 포함",
    d: "매일 과제를 받고, 루브릭 채점 후 총평을 확인해요",
  },
  {
    icon: "compass",
    t: "튜터 커스터마이징",
    d: "학습 스타일·목표에 맞게 나만의 튜터를 설정할 수 있어요",
  },
  {
    icon: "chart",
    t: "학습 리포트·모의고사",
    d: "진도와 약점을 리포트로 확인하고, 완강 후 모의고사 1회 무료",
  },
  {
    icon: "refresh",
    t: "언제든 업·다운그레이드",
    d: "사용하다 맞지 않으면 7일 안에 전액 환불, 위약금 없음",
  },
];

interface ProBenefitsProps {
  // wash/soft 토큰과 primary 색에 쓰는 테마 팔레트
  palette: Palette;
}

export function ProBenefits({ palette }: ProBenefitsProps) {
  return (
    <div style={{
      padding: "22px 24px", borderRadius: 18,
      background: palette.wash, border: `1px solid ${palette.soft}`,
    }}>
      {/* 섹션 레이블 */}
      <div style={{
        fontSize: 12, fontWeight: 700, color: palette.primary,
        letterSpacing: ".1em", textTransform: "uppercase", marginBottom: 14,
      }}>
        Pro에서 바로 쓸 수 있는 것
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        {BENEFITS.map(f => (
          // 혜택 카드. 기능명과 구체적 설명을 함께 보여준다
          <div
            key={f.t}
            style={{
              display: "flex", alignItems: "flex-start", gap: 12,
              padding: "12px 14px", borderRadius: 12,
              background: "var(--paper)", border: "1px solid var(--line)",
            }}
          >
            {/* 아이콘은 텍스트 블록과 균형을 맞추려 위쪽에 정렬한다 */}
            <span style={{ flexShrink: 0, lineHeight: 1.4, color: palette.primary }}>
              <Icon name={f.icon} size={16} />
            </span>
            <div>
              <div style={{ fontSize: 13, fontWeight: 700 }}>{f.t}</div>
              <div style={{ fontSize: 12, color: "var(--ink-3)", marginTop: 2, lineHeight: 1.5 }}>{f.d}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
