export type SubscriptionPlanCode = 'free' | 'pro' | 'premium';

export type SubscriptionStatusType = 'active' | 'cancelled' | 'expired';

export type PaymentStatus = 'paid' | 'failed' | 'refunded';

export interface PlanListResponse {
  code: string;
  priceKrw: number | null;
  periodDays: number | null;
  maxCourses: number | null;
  maxSlidesPerCourse: number | null;
  canUseTelegram: boolean | null;
  canCustomizeTutor: boolean | null;
  features: string[];
}

export interface CurrentSubscriptionResponse {
  subscriptionId: string;
  planCode: string;
  status: SubscriptionStatusType;
  startedAt: string;
  endsAt: string;
  autoRenew: boolean | null;
  nextBillingDate: string | null;
  cancelledAt: string | null;
}

export interface CheckoutResponse {
  orderId: string;
  tossPaymentsClientKey: string;
  amount: number | null;
  orderName: string;
  customerEmail: string;
  customerName: string;
}

export interface ConfirmResponse {
  subscriptionId: string;
  planCode: string;
  status: SubscriptionStatusType;
  startedAt: string;
  endsAt: string;
  receiptUrl: string;
}

export interface CancelResponse {
  subscriptionId: string;
  planCode: string;
  status: SubscriptionStatusType;
  endsAt: string;
  cancelledAt: string;
}

export interface UpgradeResponse {
  orderId: string;
  amount: number | null;
  proratedDays: number | null;
  tossPaymentsClientKey: string;
}

export interface DowngradeResponse {
  scheduledPlanCode: string;
  effectiveFrom: string;
  currentPlanActiveUntil: string;
}

export interface HistoryResponse {
  paymentId: string;
  planCode: string | null;
  amount: number | null;
  status: PaymentStatus;
  paidAt: string;
  tossOrderId: string;
}

export interface CheckoutRequest {
  planCode: string;
  successUrl: string;
  failUrl: string;
}

export interface ConfirmRequest {
  paymentKey: string;
  orderId: string;
  amount: number;
}

export interface UpgradeRequest {
  planCode: string;
  successUrl: string;
  failUrl: string;
}

export interface DowngradeRequest {
  planCode: string;
}

export interface GetSubscriptionHistoryParams {
  page?: number;
  size?: number;
}
