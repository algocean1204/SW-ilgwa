// 코인 안내 가로 바. 4개 항목을 한 줄에 보여준다.
import type { Palette } from '@/lib';

interface CoinInfoCardProps {
  // 제목 강조색 계산에 사용하는 팔레트
  palette: Palette;
}

// 코인 안내 4가지
const INFO_ITEMS = [
  { label: "해제 시간", value: "슬라이드당 ~30초" },
  { label: "환불",      value: "7일 이내 전액" },
  { label: "구독 시",   value: "자동 해제 적용" },
  { label: "이월",      value: "유효기간 없음" },
];

export function CoinInfoCard({ palette }: CoinInfoCardProps) {
  return (
    // 참고 정보라 surface-subtle 배경으로 콘텐츠 영역과만 구분한다
    <div style={{
      display: "flex", alignItems: "stretch",
      padding: "12px 18px", borderRadius: 12,
      background: "var(--surface-subtle)", border: "1px solid var(--border-soft)",
      gap: 0, marginTop: 14,
    }}>
      {INFO_ITEMS.map((item, i) => (
        <div
          key={item.label}
          style={{
            flex: 1,
            // 첫 항목을 뺀 나머지는 왼쪽 구분선으로 경계를 그린다
            borderLeft: i > 0 ? "1px solid var(--border-soft)" : "none",
            paddingLeft: i > 0 ? 16 : 0,
            paddingRight: i < INFO_ITEMS.length - 1 ? 16 : 0,
          }}
        >
          {/* 항목 레이블. palette.primary 색을 쓴다 */}
          <div style={{
            fontSize: 11, fontWeight: 700, color: palette.primary,
            letterSpacing: ".06em", marginBottom: 2,
          }}>
            {item.label}
          </div>
          {/* 항목 값 */}
          <div style={{ fontSize: 12, color: "var(--text-secondary)", fontWeight: 500 }}>
            {item.value}
          </div>
        </div>
      ))}
    </div>
  );
}
