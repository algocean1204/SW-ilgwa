// 생성권 탭 상단의 현재 잔액 배너.
// 비용 숫자가 모의고사 화면과 어긋나지 않게 examConstants의 상수를 가져다 쓴다.
import AICoinIcon from '@/components/AICoinIcon';
import { EXAM_COIN_COST } from '@/components/mock-exam/examConstants';

interface CoinBalanceBannerProps {
  // 현재 보유 생성권 수
  coins: number;
}

export function CoinBalanceBanner({ coins }: CoinBalanceBannerProps) {
  return (
    <div style={{
      padding: "18px 22px", borderRadius: 14, marginBottom: 18,
      // 골드 계열 var(--honey-tint) 배경
      background: "var(--honey-tint)", border: "1px solid var(--honey-mid)",
      display: "flex", alignItems: "center", gap: 14,
    }}>
      <AICoinIcon size={32} decorative />
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 14, fontWeight: 800, color: "var(--honey-ink)" }}>
          현재 잔액 · 생성권 {coins}개
        </div>
        <div style={{ fontSize: 12, color: "var(--honey-ink)", opacity: .85, marginTop: 3, lineHeight: 1.55 }}>
          생성권으로 <b>강의자료 1개 잠금 해제 (<AICoinIcon size={12} decorative />1)</b>,{" "}
          <b>모의고사 1회 생성 (<AICoinIcon size={12} decorative />{EXAM_COIN_COST})</b>을 할 수 있어요.
          구매한 생성권은 <b>유효기간 없음</b>.
        </div>
      </div>
      {/* 구독 안내 배지. 표면 배경이라 var(--paper) 토큰을 쓴다 */}
      <div style={{
        padding: "9px 14px", borderRadius: 10, background: "var(--paper)",
        color: "var(--honey-ink)", fontSize: 12, fontWeight: 700,
        border: "1px solid var(--honey-mid)",
      }}>
        구독 중엔 커리큘럼이 자동 해제돼요
      </div>
    </div>
  );
}
