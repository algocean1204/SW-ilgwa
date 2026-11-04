import { apiClient } from '../client';
import type { ApiResponse, OffsetPageResponse } from '../types';
import type {
  CancelResponse,
  CheckoutRequest,
  CheckoutResponse,
  ConfirmRequest,
  ConfirmResponse,
  CurrentSubscriptionResponse,
  DowngradeRequest,
  DowngradeResponse,
  GetSubscriptionHistoryParams,
  HistoryResponse,
  PlanListResponse,
  UpgradeRequest,
  UpgradeResponse,
} from './subscription.type';

export function getPlans(): Promise<ApiResponse<PlanListResponse[]>> {
  return apiClient.get('/subscription/plans');
}

export function getCurrentSubscription(): Promise<ApiResponse<CurrentSubscriptionResponse>> {
  return apiClient.get('/subscription/current');
}

export function getSubscriptionHistory(
  params?: GetSubscriptionHistoryParams,
): Promise<ApiResponse<OffsetPageResponse<HistoryResponse>>> {
  return apiClient.get('/subscription/history', params as Record<string, unknown>);
}

export function checkout(
  body: CheckoutRequest,
): Promise<ApiResponse<CheckoutResponse>> {
  return apiClient.post('/subscription/checkout', body);
}

export function confirmSubscription(
  body: ConfirmRequest,
): Promise<ApiResponse<ConfirmResponse>> {
  return apiClient.post('/subscription/confirm', body);
}

export function cancelSubscription(): Promise<ApiResponse<CancelResponse>> {
  return apiClient.delete('/subscription/cancel');
}

export function upgradeSubscription(
  body: UpgradeRequest,
): Promise<ApiResponse<UpgradeResponse>> {
  return apiClient.post('/subscription/upgrade', body);
}

export function downgradeSubscription(
  body: DowngradeRequest,
): Promise<ApiResponse<DowngradeResponse>> {
  return apiClient.post('/subscription/downgrade', body);
}
