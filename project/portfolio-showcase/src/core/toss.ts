// 토스페이먼츠 SDK 로드 + 결제 요청 유틸
// clientKey는 백엔드 checkout API 응답에서 받는다
import { isDemoMode } from '@/lib/demoMode';

const TOSS_SDK_URL = 'https://js.tosspayments.com/v2/standard';

interface TossPaymentInstance {
  requestPayment: (params: TossRequestParams) => Promise<TossPaymentResult>;
}

interface TossPayments {
  payment: (options: { customerKey: string }) => TossPaymentInstance;
}

interface TossRequestParams {
  method: 'CARD' | 'TRANSFER' | 'VIRTUAL_ACCOUNT' | 'MOBILE_PHONE' | 'TOSS_PAY';
  amount: { currency: string; value: number };
  orderId: string;
  orderName: string;
  customerEmail?: string;
  customerName?: string;
  successUrl: string;
  failUrl: string;
}

interface TossPaymentResult {
  paymentKey: string;
  orderId: string;
  amount: number;
}

type TossPaymentsFactory = ((clientKey: string) => TossPayments) & {
  ANONYMOUS?: string;
};

declare global {
  interface Window {
    TossPayments?: TossPaymentsFactory;
  }
}

let scriptLoaded: Promise<void> | null = null;

function loadTossScript(): Promise<void> {
  if (scriptLoaded) return scriptLoaded;

  scriptLoaded = new Promise((resolve, reject) => {
    if (window.TossPayments) { resolve(); return; }

    const script = document.createElement('script');
    script.src = TOSS_SDK_URL;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('토스페이먼츠 SDK 스크립트 로드 실패'));
    document.head.appendChild(script);
  });

  return scriptLoaded;
}

/** 토스 결제창을 연다. */
export async function requestTossPayment(params: {
  clientKey: string;
  orderId: string;
  orderName: string;
  amount: number;
  customerEmail?: string;
  customerName?: string;
  successUrl: string;
  failUrl: string;
}): Promise<TossPaymentResult> {
  // 데모 모드에서는 토스 SDK를 부르지 않고 성공 URL로 보낸다
  if (isDemoMode) {
    const url = new URL(params.successUrl, window.location.origin);
    url.searchParams.set('paymentKey', 'demo_payment_key');
    url.searchParams.set('orderId', params.orderId);
    url.searchParams.set('amount', String(params.amount));
    window.location.assign(url.toString());
    return new Promise(() => {});
  }

  await loadTossScript();
  if (!window.TossPayments) throw new Error('토스페이먼츠 SDK 로드 실패');

  const toss = window.TossPayments(params.clientKey);
  const customerKey = window.TossPayments.ANONYMOUS ?? '__ANONYMOUS__';
  const payment = toss.payment({ customerKey });
  return payment.requestPayment({
    method: 'CARD',
    amount: { currency: 'KRW', value: params.amount },
    orderId: params.orderId,
    orderName: params.orderName,
    customerEmail: params.customerEmail,
    customerName: params.customerName,
    successUrl: params.successUrl,
    failUrl: params.failUrl,
  });
}

export type { TossPaymentResult };
