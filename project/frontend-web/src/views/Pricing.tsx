// 구독 요금제와 생성권 패키지 페이지. 구독과 코인 탭으로 나뉜다.
// 마운트 시 getPlans, getCurrentSubscription, getCoinBalance를 병렬로 부른다.
import { useState, useEffect } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import type { AppState, SubscriptionPlan, RouteSetter } from '@/shared/types';
import {
  checkout,
  purchaseCoin,
  getPlans,
  getCurrentSubscription,
  getCoinBalance,
} from '@/apis';
import type { PlanListResponse } from '@/apis';
import { requestTossPayment } from '@/core/toss';
import { PricingHeader }      from '@/components/pricing/PricingHeader';
import { PlanCards }          from '@/components/pricing/PlanCards';
import { ProBenefits }        from '@/components/pricing/ProBenefits';
import { CoinBalanceBanner }  from '@/components/pricing/CoinBalanceBanner';
import { CoinPacks }          from '@/components/pricing/CoinPacks';
import { CoinInfoCard }       from '@/components/pricing/CoinInfoCard';
import AICoinIcon             from '@/components/AICoinIcon';
import { plans as fallbackPlans } from '@/components/pricing/plansData';
import type { PlanDef } from '@/components/pricing/plansData';
import type { Palette } from '@/lib';

interface PricingProps {
  setRoute: RouteSetter;
  appState: AppState;
}

// 최상위 탭: 구독, 코인
type MainTab = 'subscription' | 'coin';

function MainTabBar({ tab, setTab, palette }: { tab: MainTab; setTab: (t: MainTab) => void; palette: Palette }) {
  const active = (t: MainTab) => tab === t;
  return (
    <div style={{
      display: 'flex', gap: 4, padding: 4, background: 'var(--paper)',
      borderRadius: 12, border: '1px solid var(--line)',
      width: 'fit-content', marginBottom: 22,
    }}>
      {(['subscription', 'coin'] as MainTab[]).map(t => (
        <button
          key={t}
          onClick={() => setTab(t)}
          style={{
            padding: '8px 22px', borderRadius: 9, fontSize: 13, fontWeight: 700,
            background: active(t) ? palette.primary : 'transparent',
            color: active(t) ? 'var(--paper)' : 'var(--ink-2)',
            transition: 'all .15s',
          }}
        >
          {t === 'subscription' ? '구독' : '코인'}
        </button>
      ))}
    </div>
  );
}

// API 플랜 데이터를 프론트엔드 표시 설정과 합친다
// API에 없는 yprice, tagline, featured, excluded, cta, highlights는 하드코딩 값을 쓴다
function mergePlans(apiPlans: PlanListResponse[]): PlanDef[] {
  return fallbackPlans.map(frontendPlan => {
    const apiPlan = apiPlans.find(ap => ap.code === frontendPlan.id);
    if (!apiPlan) {
      // API에 플랜 코드가 없으면 프론트엔드 기본값을 쓴다
      return frontendPlan;
    }
    return {
      ...frontendPlan,
      // API 가격이 null이면 하드코딩 값을 둔다
      price: apiPlan.priceKrw !== null ? apiPlan.priceKrw : frontendPlan.price,
      // API features가 비어 있지 않을 때만 bullets를 바꾼다
      bullets: apiPlan.features.length > 0 ? apiPlan.features : frontendPlan.bullets,
    };
  });
}

// API planCode 문자열을 SubscriptionPlan으로 바꾼다
function toPlanCode(code: string): SubscriptionPlan {
  if (code === 'pro' || code === 'premium') return code;
  return 'free';
}

function Pricing({ appState }: PricingProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;

  // 헤더의 생성권(코인) 버튼으로 들어오면 localStorage 플래그를 읽어 코인 탭으로 시작한다.
  // setRoute가 pageSubView를 매번 초기화해서 localStorage를 쓴다.
  // 초기화 함수는 읽기만 해서 StrictMode 이중 렌더에서도 같은 값이 나온다.
  const [mainTab, setMainTab] = useState<MainTab>(
    () => localStorage.getItem('pricing_init_tab') === 'coin' ? 'coin' : 'subscription',
  );

  // 플래그 삭제는 부수효과라 useEffect에서 한다.
  // 초기화 함수 안에서 지우면 StrictMode 두 번째 렌더가 'subscription'으로 덮어쓴다.
  useEffect(() => {
    if (localStorage.getItem('pricing_init_tab')) {
      localStorage.removeItem('pricing_init_tab');
    }
  }, []);
  const [selected, setSelected] = useState<SubscriptionPlan>(appState?.plan ?? 'pro');

  // API와 합친 플랜 목록. 로딩 전에는 하드코딩 폴백을 쓴다.
  const [mergedPlans, setMergedPlans] = useState<PlanDef[]>(fallbackPlans);
  // 현재 구독 플랜 코드. 조회에 실패하면 'free'다.
  const [currentPlan, setCurrentPlan] = useState<SubscriptionPlan>(appState?.plan ?? 'free');
  // 생성권 잔액. appState.coins로 시작하고 API 응답 뒤 전역 값도 함께 바꾼다.
  // 그래야 결제 직후 이 화면과 헤더 코인 칩이 같은 값을 보인다.
  const [coinBalance, setCoinBalance] = useState<number>(appState?.coins ?? 0);
  // 세 API 호출이 모두 끝날 때까지 로딩 상태다
  const [loading, setLoading] = useState(true);

  const baseUrl = window.location.origin;

  // 플랜 목록, 현재 구독, 코인 잔액을 병렬로 조회한다
  useEffect(() => {
    let cancelled = false;

    async function fetchPricingData() {
      try {
        const [plansRes, subRes, coinRes] = await Promise.all([
          getPlans(),
          getCurrentSubscription(),
          getCoinBalance(),
        ]);

        if (cancelled) return;

        if (plansRes.data) {
          setMergedPlans(mergePlans(plansRes.data));
        }

        // active 구독만 유효한 플랜으로 본다. 로컬 currentPlan과 전역 appState.plan을
        // 함께 바꿔 헤더, 사이드바도 같은 플랜을 보이게 한다.
        if (subRes.data && subRes.data.status === 'active') {
          const plan = toPlanCode(subRes.data.planCode);
          setCurrentPlan(plan);
          appState.setPlan(plan);
        }

        // 코인 잔액도 로컬과 전역 appState를 함께 바꿔 헤더 코인 칩과 맞춘다
        if (coinRes.data) {
          setCoinBalance(coinRes.data.balance);
          appState.setCoins(coinRes.data.balance);
        }
      } catch {
        // API 오류면 하드코딩 폴백을 그대로 둔다
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void fetchPricingData();

    return () => { cancelled = true; };
  }, []);

  function handleSubscribe(planCode: string) {
    const successUrl = `${baseUrl}?route=paymentCallback&type=subscription`;
    const failUrl = `${baseUrl}?route=paymentCallback&type=subscription`;
    void checkout({ planCode, successUrl, failUrl })
      .then((res) => {
        if (!res.data) return;
        return requestTossPayment({
          clientKey: res.data.tossPaymentsClientKey,
          orderId: res.data.orderId,
          orderName: res.data.orderName,
          amount: res.data.amount ?? 0,
          customerEmail: res.data.customerEmail,
          customerName: res.data.customerName,
          successUrl,
          failUrl,
        });
      })
      .catch(console.error);
  }

  function handleCoinBuy(bundleId: string) {
    const successUrl = `${baseUrl}?route=paymentCallback&type=coin`;
    const failUrl = `${baseUrl}?route=paymentCallback&type=coin`;
    void purchaseCoin({ bundleId, successUrl, failUrl })
      .then((res) => {
        if (!res.data) return;
        return requestTossPayment({
          clientKey: res.data.tossPaymentsClientKey,
          orderId: res.data.orderId,
          orderName: res.data.orderName,
          amount: res.data.amount,
          successUrl,
          failUrl,
        });
      })
      .catch(console.error);
  }

  return (
    <div style={{ maxWidth: 1180, margin: '0 auto', padding: 'var(--page-py) var(--page-px) 56px' }}>

      {/* 페이지 헤더 */}
      <PricingHeader palette={palette} />

      {/* 최상위 탭: 구독, 코인 */}
      <MainTabBar tab={mainTab} setTab={setMainTab} palette={palette} />

      {/* 구독 탭 */}
      {mainTab === 'subscription' && (
        <>
          {/* 로딩 중에는 플랜 카드 자리에 인디케이터를 보인다 */}
          {loading ? (
            <div style={{
              display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16,
              marginBottom: 30, paddingTop: 12,
            }}>
              {[0, 1, 2].map(i => (
                <div
                  key={i}
                  style={{
                    height: 420, borderRadius: 18,
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--line)',
                    animation: 'pulse 1.5s ease-in-out infinite',
                  }}
                />
              ))}
            </div>
          ) : (
            /* Free, Pro, Premium 플랜 카드 */
            <PlanCards
              plans={mergedPlans}
              selected={selected}
              onSelect={setSelected}
              palette={palette}
              currentPlan={currentPlan}
              onSubscribe={handleSubscribe}
            />
          )}

          {/* Pro 혜택 안내 */}
          <ProBenefits palette={palette} />
        </>
      )}

      {/* 코인 탭 */}
      {mainTab === 'coin' && (
        <div>
          <div style={{ marginBottom: 14 }}>
            <div style={{
              fontSize: 11, fontWeight: 700, letterSpacing: '.14em',
              textTransform: 'uppercase', color: 'var(--text-tertiary)', marginBottom: 6,
            }}>
              생성권 패키지
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <AICoinIcon size={20} decorative />
              <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, letterSpacing: 0, color: 'var(--text-primary)' }}>
                추가 기능 해제
              </h2>
            </div>
            <p style={{ margin: '6px 0 0', fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
              구독 없이도 생성권으로 강의자료 · 모의고사를 낱개로 바로 해제할 수 있어요.
              생성권은 <b style={{ color: 'var(--text-primary)' }}>유효기간 없음</b>이라 여유 있게 사용 가능해요.
            </p>
          </div>

          {/* 코인 잔액 배너 */}
          <CoinBalanceBanner coins={coinBalance} />

          {/* 코인 팩 3개 그리드 */}
          <CoinPacks onBuy={handleCoinBuy} />

          {/* 코인 안내 컴팩트 바 */}
          <CoinInfoCard palette={palette} />
        </div>
      )}

    </div>
  );
}

export default Pricing;
