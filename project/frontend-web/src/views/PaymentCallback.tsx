// 토스페이먼츠 결제 성공·실패 콜백 화면
import { useState, useEffect } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import { confirmSubscription, confirmCoinPayment, getCoinBalance } from '@/apis';
import Icon from '@/Icon';
import type { AppState, RouteSetter } from '@/shared/types';

interface PaymentCallbackProps {
  setRoute: RouteSetter;
  appState: AppState;
}

type CallbackStatus = 'processing' | 'success' | 'fail';

function PaymentCallback({ setRoute, appState }: PaymentCallbackProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;
  const [status, setStatus] = useState<CallbackStatus>('processing');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const paymentKey = params.get('paymentKey');
    const orderId = params.get('orderId');
    const amount = params.get('amount');
    const type = params.get('type');
    const errorCode = params.get('code');
    const errorMessage = params.get('message');

    if (errorCode || errorMessage) {
      void Promise.resolve().then(() => {
        setStatus('fail');
        setMessage(errorMessage ?? '결제가 취소되었거나 오류가 발생했습니다.');
      });
      return;
    }

    if (!paymentKey || !orderId || !amount) {
      void Promise.resolve().then(() => {
        setStatus('fail');
        setMessage('결제 정보가 올바르지 않습니다.');
      });
      return;
    }

    const body = { paymentKey, orderId, amount: Number(amount) };

    if (type === 'coin') {
      void confirmCoinPayment(body)
        .then((res) => {
          setStatus('success');
          setMessage(`생성권 ${res.data?.coinAmount ?? 0}개가 충전되었습니다.`);
          if (res.data?.balance != null) appState.setCoins(res.data.balance);
        })
        .catch(() => {
          setStatus('fail');
          setMessage('생성권 결제 확인에 실패했습니다. 고객센터에 문의해 주세요.');
        });
    } else {
      void confirmSubscription(body)
        .then((res) => {
          setStatus('success');
          const plan = res.data?.planCode;
          setMessage(`${plan?.toUpperCase() ?? 'Pro'} 구독이 시작되었습니다!`);
          if (plan === 'pro' || plan === 'premium') {
            appState.setPlan(plan);
          }
          void getCoinBalance().then((r) => {
            if (r.data) appState.setCoins(r.data.balance);
          }).catch(() => {});
        })
        .catch(() => {
          setStatus('fail');
          setMessage('구독 결제 확인에 실패했습니다. 고객센터에 문의해 주세요.');
        });
    }
  }, [appState]);

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--bg)',
    }}>
      <div style={{
        maxWidth: 420, width: '100%', padding: '48px 40px', borderRadius: 20,
        background: 'var(--paper)', border: '1px solid var(--line)',
        textAlign: 'center', boxShadow: 'var(--shadow-lg)',
      }}>
        {status === 'processing' && (
          <>
            <div className="pulse" style={{
              width: 56, height: 56, borderRadius: '50%', margin: '0 auto 20px',
              background: palette.wash, border: `2px solid ${palette.soft}`,
              display: 'grid', placeItems: 'center',
            }}>
              <Icon name="sparkle" size={24} color={palette.primary} />
            </div>
            <h2 style={{ fontSize: 20, fontWeight: 800, color: 'var(--ink)', margin: '0 0 8px' }}>
              결제 처리 중
            </h2>
            <p style={{ fontSize: 13, color: 'var(--ink-3)', lineHeight: 1.6 }}>
              잠시만 기다려 주세요...
            </p>
          </>
        )}

        {status === 'success' && (
          <>
            <div style={{
              width: 56, height: 56, borderRadius: '50%', margin: '0 auto 20px',
              background: 'var(--grass-wash)', border: '2px solid var(--grass-soft)',
              display: 'grid', placeItems: 'center',
            }}>
              <Icon name="check" size={24} color="var(--grass-ink)" />
            </div>
            <h2 style={{ fontSize: 20, fontWeight: 800, color: 'var(--ink)', margin: '0 0 8px' }}>
              결제 완료
            </h2>
            <p style={{ fontSize: 13, color: 'var(--ink-3)', lineHeight: 1.6 }}>
              {message}
            </p>
            <button
              onClick={() => setRoute('home')}
              className="lift"
              style={{
                marginTop: 24, padding: '12px 28px', borderRadius: 10,
                fontSize: 14, fontWeight: 800,
                background: palette.primary, color: 'var(--paper)',
              }}
            >
              홈으로 돌아가기
            </button>
          </>
        )}

        {status === 'fail' && (
          <>
            <div style={{
              width: 56, height: 56, borderRadius: '50%', margin: '0 auto 20px',
              background: 'var(--coral-wash)', border: '2px solid var(--coral-soft)',
              display: 'grid', placeItems: 'center',
            }}>
              <Icon name="x" size={24} color="var(--coral-ink)" />
            </div>
            <h2 style={{ fontSize: 20, fontWeight: 800, color: 'var(--ink)', margin: '0 0 8px' }}>
              결제 실패
            </h2>
            <p style={{ fontSize: 13, color: 'var(--ink-3)', lineHeight: 1.6 }}>
              {message}
            </p>
            <button
              onClick={() => setRoute('pricing')}
              className="lift"
              style={{
                marginTop: 24, padding: '12px 28px', borderRadius: 10,
                fontSize: 14, fontWeight: 800,
                background: palette.primary, color: 'var(--paper)',
              }}
            >
              다시 시도하기
            </button>
          </>
        )}
      </div>
    </div>
  );
}

export default PaymentCallback;
