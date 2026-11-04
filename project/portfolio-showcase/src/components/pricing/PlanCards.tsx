// 구독 플랜 3개 카드 그리드. Pro 카드를 가장 눈에 띄게 그린다.
// plans는 Pricing.tsx가 API 데이터와 합쳐 넘긴다.
import { useState } from 'react';
import type { Palette } from '@/lib';
import type { SubscriptionPlan } from '@/shared/types';
import StatusBadge from '@/components/ui/StatusBadge';
import Icon from '@/Icon';
// hover 시 플랜 카드의 테두리와 배경색을 바꾼다
import { SpotlightCard } from '@/components/animated';
import type { PlanDef } from './plansData';
// 베타 기간 결제 플래그. false면 구매 버튼을 "준비 중"으로 비활성화한다
import { ENABLE_PAYMENT } from '@/shared/constants/featureFlags';

// Pro 카드 혜택 목록. "왜 Pro인가"가 먼저 보이게 가격 위에 둔다
function PlanHighlights({ highlights }: { highlights: string[] }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 4, margin: "10px 0 4px" }}>
      {highlights.map((h, i) => (
        <div key={i} style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, fontWeight: 700, color: "var(--featured-fg-soft)" }}>
          <span style={{ width: 14, height: 14, borderRadius: "50%", background: "var(--white-overlay-md)", display: "grid", placeItems: "center", flexShrink: 0 }}>
            <Icon name="star" size={8} />
          </span>
          {h}
        </div>
      ))}
    </div>
  );
}

// 포함·미포함 기능 목록
function PlanBullets({ bullets, excluded, featured, palette }: { bullets: string[]; excluded?: string[]; featured: boolean; palette: Palette }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 9, marginTop: 16 }}>
      {bullets.map((b, i) => (
        <div key={i} style={{ display: "flex", alignItems: "flex-start", gap: 8, fontSize: 13, lineHeight: 1.5 }}>
          <span style={{ width: 16, height: 16, borderRadius: "50%", flexShrink: 0, marginTop: 1, background: featured ? "var(--telegram-overlay-soft)" : palette.wash, color: featured ? "var(--paper)" : palette.primary, display: "grid", placeItems: "center", fontSize: 9, fontWeight: 800 }}>
            <Icon name="check" size={9} />
          </span>
          <span style={{ color: featured ? "var(--featured-fg-soft)" : "var(--ink-2)" }}>{b}</span>
        </div>
      ))}
      {/* 미포함 기능은 취소선으로 표시한다 */}
      {excluded && excluded.map((b, i) => (
        <div key={"ex" + i} style={{ display: "flex", alignItems: "flex-start", gap: 8, fontSize: 13, lineHeight: 1.5, opacity: .55 }}>
          <span style={{ width: 16, height: 16, borderRadius: "50%", flexShrink: 0, marginTop: 1, background: "var(--sand)", color: "var(--muted)", display: "grid", placeItems: "center", fontSize: 12, fontWeight: 800 }}>—</span>
          <span style={{ color: "var(--muted)", textDecoration: "line-through" }}>{b}</span>
        </div>
      ))}
    </div>
  );
}

// 플랜별 한 줄 설명. 카드 이름 아래에 어떤 사람에게 맞는 플랜인지 적는다
const PLAN_DESCRIPTIONS: Record<string, string> = {
  free: "기능을 먼저 체험해보고 싶은 분",
  pro: "매일 꾸준히 학습하고 성과를 확인하고 싶은 분",
  premium: "집중적으로 빠르게 끝내고 싶은 분",
};

type BillingCycle = 'monthly' | 'yearly';

function BillingToggle({ value, onChange, featured, palette }: { value: BillingCycle; onChange: (v: BillingCycle) => void; featured: boolean; palette: Palette }) {
  return (
    <div style={{ display: 'inline-flex', gap: 3, padding: 3, borderRadius: 8, background: featured ? 'var(--telegram-overlay-soft)' : 'var(--surface-subtle)', border: featured ? '1px solid var(--telegram-overlay-soft)' : '1px solid var(--border-soft)' }}>
      {([{ v: 'monthly', l: '월간' }, { v: 'yearly', l: '연간' }] as { v: BillingCycle; l: string }[]).map(opt => (
        <button
          key={opt.v}
          onClick={(event) => { event.stopPropagation(); onChange(opt.v); }}
          style={{
            padding: '5px 10px', borderRadius: 6, fontSize: 11, fontWeight: 800,
            background: value === opt.v ? (featured ? 'var(--paper)' : palette.primary) : 'transparent',
            color: value === opt.v ? (featured ? palette.primary : 'var(--paper)') : (featured ? 'var(--featured-fg-soft)' : 'var(--text-tertiary)'),
          }}
        >
          {opt.l}
        </button>
      ))}
    </div>
  );
}

const PLAN_RANK: Record<SubscriptionPlan, number> = { free: 0, pro: 1, premium: 2 };

function getCtaLabel(planId: SubscriptionPlan, currentPlan: SubscriptionPlan): string {
  if (planId === currentPlan) return '현재 구독 중';
  if (planId === 'free') return '지금 사용 중';
  const rank = PLAN_RANK[planId];
  const currentRank = PLAN_RANK[currentPlan];
  if (rank > currentRank) return `${planId === 'pro' ? 'Pro' : 'Premium'} 시작하기 →`;
  return '다운그레이드';
}

interface PlanCardsProps {
  // Pricing.tsx가 API 데이터와 합쳐 넘기는 플랜 배열
  plans: PlanDef[];
  selected: SubscriptionPlan;
  onSelect: (id: SubscriptionPlan) => void;
  palette: Palette;
  currentPlan: SubscriptionPlan;
  onSubscribe?: (planCode: string) => void;
}

export function PlanCards({ plans, selected, onSelect, palette, currentPlan, onSubscribe }: PlanCardsProps) {
  const [cycles, setCycles] = useState<Record<'pro' | 'premium', BillingCycle>>({ pro: 'monthly', premium: 'monthly' });

  return (
    // 추천 배지가 카드 바깥으로 나가도 잘리지 않도록 위쪽 여유를 둔다
    <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16, marginBottom: 30, paddingTop: 12, alignItems: 'stretch' }}>
      {plans.map(p => {
        const active = selected === p.id;
        const featured = p.featured;
        const cycle = p.id === 'free' ? 'monthly' : cycles[p.id];
        // 카드 내부 토글 상태에 맞춰 월 환산 금액을 표시한다
        const price = cycle === "monthly" ? (p.price ?? 0) : Math.round((p.yprice ?? 0) / 12);
        return (
          <SpotlightCard
            key={p.id}
            radius={200}
            opacity={0.06}
            color={featured ? "var(--paper)" : palette.primary}
            // grid stretch와 height 100%로 같은 행의 카드 높이를 맞춘다
            style={{ borderRadius: 18, height: '100%' }}
          >
            <div
              role="button"
              tabIndex={0}
              onClick={() => onSelect(p.id)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onSelect(p.id);
                }
              }}
              style={{
                position: "relative", textAlign: "left",
                padding: "22px 20px", borderRadius: 18,
                background: featured ? palette.primary : "var(--paper)",
                color: featured ? "var(--paper)" : "var(--ink)",
                border: active ? (featured ? "2px solid var(--paper)" : `2px solid ${palette.primary}`) : "1px solid var(--line)",
                boxShadow: active ? (featured ? `0 24px 52px -18px ${palette.primary}` : "var(--shadow-lg)") : (featured ? `0 16px 40px -12px ${palette.primary}80` : "var(--shadow-sm)"),
                // minHeight 없이 CSS 그리드가 같은 행 카드 높이를 맞춘다
                transition: "all .2s", width: "100%", height: '100%', overflow: "visible",
                display: 'flex', flexDirection: 'column', cursor: 'pointer',
              }}
              className="lift"
            >
              {/* 추천 플랜 배지 */}
              {featured && <div style={{ position: "absolute", top: -10, right: 22 }}><StatusBadge tone="warning" label="추천 플랜" /></div>}

              {/* 플랜 태그라인 */}
              <div style={{ fontSize: 12, fontWeight: 700, letterSpacing: ".1em", color: featured ? "var(--featured-fg-dim)" : palette.primary, textTransform: "uppercase", marginBottom: 6 }}>
                {p.tagline}
              </div>

              {/* 플랜 이름과 한 줄 설명 */}
              <div style={{ fontSize: 24, fontWeight: 800, letterSpacing: 0 }}>{p.name}</div>
              <div style={{ fontSize: 12, marginTop: 5, marginBottom: 2, lineHeight: 1.5, color: featured ? "var(--featured-fg-soft)" : "var(--ink-3)", fontWeight: featured ? 600 : 400 }}>
                {PLAN_DESCRIPTIONS[p.id]}
              </div>

              {/* Pro 카드 혜택 */}
              {featured && p.highlights && <PlanHighlights highlights={p.highlights} />}

              {/* Pro/Premium 카드 내부 월간·연간 토글 */}
              {p.price > 0 && (
                <div style={{ marginTop: 12 }}>
                  <BillingToggle
                    value={cycle}
                    onChange={(next) => setCycles(prev => ({ ...prev, [p.id]: next }))}
                    featured={featured}
                    palette={palette}
                  />
                </div>
              )}

              {/* 가격 영역 */}
              <div style={{ marginTop: 14, paddingBottom: 14, borderBottom: featured ? "1px solid var(--telegram-overlay-soft)" : "1px solid var(--line-2)" }}>
                <div style={{ display: "flex", alignItems: "baseline", gap: 4 }}>
                  <span style={{ fontSize: 28, fontWeight: 800, letterSpacing: 0 }}>
                    ₩{(price ?? 0).toLocaleString()}
                  </span>
                  <span style={{ fontSize: 12, opacity: .75 }}>/ 월</span>
                </div>
                {cycle === "yearly" && p.price > 0 && <div style={{ fontSize: 12, opacity: .8, marginTop: 4 }}>연 ₩{(p.yprice ?? 0).toLocaleString()} 일시불</div>}
                {p.price === 0 && <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 4 }}>무료</div>}
              </div>

              {/* 포함·미포함 기능 목록 */}
              <PlanBullets bullets={p.bullets} excluded={p.excluded} featured={featured} palette={palette} />

              {/* CTA 버튼 영역. marginTop auto로 하단에 붙이고 paddingTop으로 최소 간격을 둔다 */}
              <div style={{ marginTop: 'auto', paddingTop: 22 }}>
                {/* ENABLE_PAYMENT가 false면 유료 플랜 버튼을 "준비 중"으로 비활성화한다 */}
                {!ENABLE_PAYMENT && p.price > 0 ? (
                  <div style={{
                    width: '100%', padding: featured ? "13px 14px" : "11px 14px",
                    borderRadius: 10, textAlign: "center", fontSize: featured ? 14 : 13, fontWeight: 800,
                    background: featured ? 'rgba(255,255,255,0.15)' : 'var(--surface-subtle)',
                    color: featured ? 'rgba(255,255,255,0.55)' : 'var(--text-tertiary)',
                    border: featured ? '1px solid rgba(255,255,255,0.2)' : '1px solid var(--border-soft)',
                  }}>준비 중</div>
                ) : (
                  <button
                    onClick={(e) => { e.stopPropagation(); if (p.id !== currentPlan && p.price > 0 && onSubscribe) onSubscribe(p.id); }}
                    disabled={p.id === currentPlan || p.price === 0}
                    style={{
                      width: '100%', padding: featured ? "13px 14px" : "11px 14px",
                      borderRadius: 10, textAlign: "center", fontSize: featured ? 14 : 13, fontWeight: 800,
                      background: p.id === currentPlan ? 'var(--surface-subtle)' : featured ? "var(--paper)" : "var(--surface-subtle)",
                      color: p.id === currentPlan ? 'var(--text-tertiary)' : featured ? palette.primary : "var(--text-secondary)",
                      border: featured ? "none" : "1px solid var(--border-soft)",
                      cursor: p.id !== currentPlan && p.price > 0 ? 'pointer' : 'default',
                      opacity: p.id === currentPlan ? 0.7 : 1,
                    }}
                  >{getCtaLabel(p.id, currentPlan)}</button>
                )}
              </div>
            </div>
          </SpotlightCard>
        );
      })}
    </div>
  );
}
