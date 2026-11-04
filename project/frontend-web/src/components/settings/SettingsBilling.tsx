// 결제·구독 탭.
// getCurrentSubscription, getPlans, getSubscriptionHistory 결과를 보여준다.
import { useCallback, useEffect, useState } from 'react';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import {
  ApiError,
  getCurrentSubscription,
  getPlans,
  getSubscriptionHistory,
} from '@/apis';
import type {
  CurrentSubscriptionResponse,
  HistoryResponse,
  PlanListResponse,
} from '@/apis';
import { Card } from './SettingsPrimitives';
import StatusBadge from '@/components/ui/StatusBadge';

interface SettingsBillingProps {
  palette: Palette;
  setRoute: RouteSetter;
}

const PLAN_LABELS: Record<string, string> = {
  free: 'FREE',
  pro: 'PRO',
  premium: 'PREMIUM',
};

const STATUS_TONE: Record<string, 'success' | 'warning' | 'danger'> = {
  active: 'success',
  cancelled: 'warning',
  expired: 'danger',
  paid: 'success',
  failed: 'danger',
  refunded: 'warning',
};

const STATUS_LABEL: Record<string, string> = {
  active: '구독 중',
  cancelled: '해지 예정',
  expired: '만료',
  paid: '완료',
  failed: '실패',
  refunded: '환불',
};

const formatDate = (v: string | null | undefined): string => {
  if (!v) return '-';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일`;
};

const formatPrice = (v: number | null | undefined): string => {
  if (v == null) return '-';
  return `₩${v.toLocaleString('ko-KR')}`;
};

const formatError = (e: unknown): string => {
  if (e instanceof ApiError) {
    if (e.status === 401) return '로그인 세션이 만료됐어요.';
    return e.errorResponse?.message ?? `요청에 실패했습니다. (${e.status})`;
  }
  if (e instanceof Error) return e.message;
  return '요청에 실패했습니다.';
};

export function SettingsBilling({ palette, setRoute }: SettingsBillingProps) {
  const [subscription, setSubscription] = useState<CurrentSubscriptionResponse | null>(null);
  const [plan, setPlan] = useState<PlanListResponse | null>(null);
  const [history, setHistory] = useState<HistoryResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [subRes, plansRes, histRes] = await Promise.all([
        getCurrentSubscription(),
        getPlans(),
        getSubscriptionHistory({ page: 0, size: 10 }),
      ]);

      const sub = subRes.data;
      setSubscription(sub ?? null);

      // 비배열 응답(null, 객체 등)에 .find를 부르면 TypeError로 화면이 깨져 배열인지 먼저 본다.
      if (sub && Array.isArray(plansRes.data)) {
        const matched = plansRes.data.find((p) => p.code === sub.planCode);
        setPlan(matched ?? null);
      }

      setHistory(histRes.data?.content ?? []);
    } catch (e) {
      setError(formatError(e));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const planLabel = PLAN_LABELS[subscription?.planCode ?? ''] ?? subscription?.planCode ?? '-';
  const priceLabel = formatPrice(plan?.priceKrw);
  const periodLabel = plan?.periodDays ? `${plan.periodDays}일` : '월';

  if (isLoading) {
    return (
      <div style={{ padding: 40, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
        구독 정보를 불러오는 중...
      </div>
    );
  }

  return (
    <>
      {error && (
        <div
          role="alert"
          style={{
            marginBottom: 14, padding: '12px 14px', borderRadius: 'var(--radius-md)',
            background: 'var(--state-danger-bg)', border: '1px solid var(--coral)',
            color: 'var(--danger)', fontSize: 12.5, fontWeight: 700,
            display: 'flex', alignItems: 'center', gap: 10,
          }}
        >
          <span style={{ flex: 1 }}>{error}</span>
          <button
            onClick={() => void load()}
            style={{ fontSize: 11.5, fontWeight: 800, color: 'var(--danger)' }}
          >
            다시 시도
          </button>
        </div>
      )}

      {/* 구독 현황 요약 */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 14,
        padding: '12px 16px', marginBottom: 14, borderRadius: 'var(--radius-lg)',
        background: 'var(--surface-subtle)', border: '1px solid var(--border-soft)',
      }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
            <StatusBadge
              tone={STATUS_TONE[subscription?.status ?? ''] ?? 'warning'}
              label={`${planLabel} PLAN`}
              size="sm"
            />
            <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-primary)' }}>
              {priceLabel} / {periodLabel}
            </span>
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
            {subscription?.status === 'active' && subscription.nextBillingDate
              ? `다음 결제일: ${formatDate(subscription.nextBillingDate)}`
              : subscription?.status === 'cancelled'
                ? `${formatDate(subscription.endsAt)}까지 이용 가능`
                : STATUS_LABEL[subscription?.status ?? ''] ?? '구독 정보 없음'}
          </div>
        </div>
        <div style={{
          width: 8, height: 8, borderRadius: '50%', flexShrink: 0,
          background: subscription?.status === 'active' ? 'var(--grass-primary)' : 'var(--text-muted)',
        }} />
      </div>

      {/* 현재 플랜 카드 */}
      <Card title="현재 플랜">
        <div style={{
          padding: '18px 20px', borderRadius: 'var(--radius-lg)',
          background: `linear-gradient(135deg, ${palette.primary}, ${palette.primary2})`,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div>
              <div style={{ fontSize: 11, fontWeight: 800, letterSpacing: '.1em', opacity: .9, color: 'var(--paper)' }}>
                {planLabel} PLAN
              </div>
              <div style={{ fontSize: 22, fontWeight: 800, marginTop: 4, letterSpacing: '-.02em', color: 'var(--paper)' }}>
                {priceLabel === '-' ? 'Free' : `${priceLabel} / ${periodLabel}`}
              </div>
              <div style={{ fontSize: 11.5, marginTop: 6, opacity: .9, color: 'var(--paper)' }}>
                {subscription?.nextBillingDate
                  ? `다음 결제일: ${formatDate(subscription.nextBillingDate)}`
                  : `시작일: ${formatDate(subscription?.startedAt)}`}
              </div>
            </div>
            <div style={{ flex: 1 }} />
            <button
              onClick={() => setRoute('pricing')}
              style={{
                padding: '10px 16px', borderRadius: 'var(--radius-md)', fontSize: 12, fontWeight: 800,
                background: 'var(--paper)', color: palette.primary,
              }}
              className="lift"
            >
              플랜 변경
            </button>
          </div>
        </div>
      </Card>

      {/* 결제 내역 */}
      <Card title="결제 내역">
        {history.length === 0 ? (
          <div style={{ padding: '20px 0', textAlign: 'center', color: 'var(--ink-3)', fontSize: 12.5 }}>
            결제 내역이 없습니다
          </div>
        ) : (
          history.map((h, i) => (
            <div key={h.paymentId} style={{
              display: 'flex', alignItems: 'center', gap: 12, padding: '12px 0',
              borderTop: i > 0 ? '1px solid var(--line-2)' : 'none',
            }}>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', width: 100 }}>
                {formatDate(h.paidAt)}
              </div>
              <div style={{ flex: 1, fontSize: 12.5, color: 'var(--ink)', fontWeight: 600 }}>
                {PLAN_LABELS[h.planCode ?? ''] ?? h.planCode ?? '-'}
              </div>
              <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink)', width: 80, textAlign: 'right' }}>
                {formatPrice(h.amount)}
              </div>
              <StatusBadge
                tone={STATUS_TONE[h.status] ?? 'warning'}
                label={STATUS_LABEL[h.status] ?? h.status}
                size="sm"
              />
            </div>
          ))
        )}
      </Card>
    </>
  );
}
