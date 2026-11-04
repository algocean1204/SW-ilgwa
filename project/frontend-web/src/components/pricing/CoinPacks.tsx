// 생성권 팩 3개 카드 그리드. API 번들 목록을 그리고,
// API에 없는 tagline·featured는 bundleId로 매핑한다.
import { useState, useEffect } from 'react';
import AICoinIcon from '@/components/AICoinIcon';
import { getCoinBundles } from '@/apis';
import type { CoinBundleResponse } from '@/apis';
// 베타 기간에는 결제 버튼을 막는다
import { ENABLE_PAYMENT } from '@/shared/constants/featureFlags';

// API에 없는 프론트 전용 표시 레이블
const BUNDLE_UI: Record<string, { tagline: string; featured: boolean }> = {
  COIN_5:  { tagline: '체험팩',   featured: false },
  COIN_20: { tagline: '가장 인기', featured: true },
  COIN_60: { tagline: '합격팩',   featured: false },
};

interface CoinPacksProps {
  onBuy: (bundleId: string) => void;
}

export function CoinPacks({ onBuy }: CoinPacksProps) {
  const [bundles, setBundles] = useState<CoinBundleResponse[]>([]);

  useEffect(() => {
    void getCoinBundles().then((res) => {
      // 응답이 배열이 아니면(null, 객체 등) .map에서 throw하므로 빈 배열로 둔다
      if (Array.isArray(res.data)) {
        setBundles(res.data);
      } else {
        setBundles([]);
      }
    }).catch(() => { setBundles([]); });
  }, []);

  // 로딩 중이거나 API 오류면 빈 상태 UI를 그린다.
  // null을 반환하면 상위 레이아웃이 무너질 수 있다
  if (bundles.length === 0) {
    return (
      <div style={{
        padding: '32px 0',
        textAlign: 'center',
        color: 'var(--ink-3)',
        fontSize: 13,
      }}>
        코인 팩 정보를 불러오는 중...
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16, marginBottom: 22 }}>
        {bundles.map(b => {
          const ui = BUNDLE_UI[b.bundleId] ?? { tagline: '', featured: false };
          const perCoin = b.displayAmount > 0 ? Math.round(b.priceKrw / b.displayAmount) : 0;
          return (
            <div
              key={b.bundleId}
              className="lift"
              style={{
                padding: "20px 18px", borderRadius: 18,
                background: ui.featured ? "var(--honey)" : "var(--paper)",
                border: ui.featured ? "2px solid var(--honey-mid)" : "1px solid var(--line)",
                boxShadow: ui.featured ? "0 18px 40px -18px var(--honey-2)" : "var(--shadow-sm)",
                position: "relative",
              }}
            >
              {ui.featured && (
                <div style={{
                  position: "absolute", top: -10, right: 22,
                  padding: "4px 10px", borderRadius: 999,
                  background: "var(--ink)", color: "var(--honey)",
                  fontSize: 12, fontWeight: 800, letterSpacing: ".08em",
                }}>
                  {ui.tagline.toUpperCase()}
                </div>
              )}
              <div style={{
                fontSize: 12, fontWeight: 800, letterSpacing: ".1em",
                color: ui.featured ? "var(--honey-ink)" : "var(--ink-3)",
                textTransform: "uppercase", marginBottom: 4,
              }}>
                {!ui.featured && ui.tagline}
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 8 }}>
                <AICoinIcon size={32} decorative />
                <span style={{
                  fontSize: 36, fontWeight: 800, letterSpacing: 0,
                  color: ui.featured ? "var(--honey-ink)" : "var(--ink)",
                }}>
                  {b.displayAmount}
                </span>
                {b.bonusAmount > 0 && (
                  <span style={{
                    fontSize: 14, fontWeight: 800,
                    color: "var(--coral-ink)", background: "var(--coral-wash)",
                    padding: "2px 8px", borderRadius: 6,
                  }}>
                    +{b.bonusAmount}
                  </span>
                )}
              </div>
              <div style={{
                fontSize: 22, fontWeight: 800, letterSpacing: 0,
                color: ui.featured ? "var(--honey-ink)" : "var(--ink)",
              }}>
                ₩{b.priceKrw.toLocaleString()}
              </div>
              <div style={{ fontSize: 12, color: ui.featured ? "var(--honey-ink)" : "var(--ink-3)", marginTop: 3, opacity: .8 }}>
                개당 ₩{perCoin.toLocaleString()}
              </div>
              <div style={{
                marginTop: 18, paddingTop: 14,
                borderTop: ui.featured ? "1px solid var(--honey-border-soft)" : "1px solid var(--line-2)",
                display: "flex", flexDirection: "column", gap: 6, fontSize: 12,
                color: ui.featured ? "var(--honey-ink)" : "var(--ink-2)",
              }}>
                <div>· 강의자료 해제 약 {b.totalAmount}개</div>
                <div>· 또는 모의고사 {Math.floor(b.totalAmount / 5)}회 생성</div>
                <div>· <b>유효기간 없음</b></div>
              </div>
              {/* ENABLE_PAYMENT가 false면 구매 버튼을 "준비 중"으로 비활성화한다 */}
              {ENABLE_PAYMENT ? (
                <button
                  onClick={() => onBuy(b.bundleId)}
                  className="lift"
                  style={{
                    marginTop: 18, width: "100%", padding: "12px", borderRadius: 10,
                    background: ui.featured ? "var(--honey-ink)" : "var(--ink)", color: "var(--paper)",
                    fontSize: 13, fontWeight: 800,
                  }}
                >
                  {b.displayAmount}개 구매하기
                </button>
              ) : (
                <div
                  style={{
                    marginTop: 18, width: "100%", padding: "12px", borderRadius: 10,
                    background: 'var(--surface-subtle)', color: 'var(--text-tertiary)',
                    fontSize: 13, fontWeight: 800,
                    textAlign: 'center',
                    border: '1px solid var(--border-soft)',
                    boxSizing: 'border-box',
                  }}
                >
                  준비 중
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
