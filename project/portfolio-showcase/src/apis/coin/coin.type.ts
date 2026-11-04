export type CoinTransactionType = 'purchase' | 'use_slide' | 'use_exam' | 'refund';

export interface CoinBalanceResponse {
  balance: number;
  totalPurchased: number;
  totalUsed: number;
}

export interface CoinCheckoutResponse {
  orderId: string;
  amount: number;
  coinAmount: number;
  tossPaymentsClientKey: string;
  orderName: string;
}

export interface CoinConfirmResponse {
  coinAmount: number;
  balance: number;
  paymentKey: string;
}

export interface CoinHistoryItem {
  id: string;
  type: string;
  amount: number;
  balanceAfter: number;
  refId: string;
  note: string;
  createdAt: string;
}

export interface CoinHistoryResponse {
  content: CoinHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface CoinUseResponse {
  coinUsed: number;
  coinBalance: number;
  refId: string;
}

export interface CoinPurchaseRequest {
  bundleId: string;
  successUrl: string;
  failUrl: string;
}

export interface CoinConfirmRequest {
  paymentKey: string;
  orderId: string;
  amount: number;
}

export interface CoinUseSlideRequest {
  slideId: string;
}

export interface CoinUseExamRequest {
  courseId: string;
}

export interface GetCoinHistoryParams {
  type?: string;
  page?: number;
  size?: number;
}

export interface CoinBundleResponse {
  bundleId: string;
  displayAmount: number;
  bonusAmount: number;
  totalAmount: number;
  priceKrw: number;
}
