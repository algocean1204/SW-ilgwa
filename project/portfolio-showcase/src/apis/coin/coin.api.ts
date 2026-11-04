import { apiClient } from '../client';
import type { ApiResponse } from '../types';
import type {
  CoinBalanceResponse,
  CoinBundleResponse,
  CoinCheckoutResponse,
  CoinConfirmRequest,
  CoinConfirmResponse,
  CoinHistoryResponse,
  CoinPurchaseRequest,
  CoinUseExamRequest,
  CoinUseResponse,
  CoinUseSlideRequest,
  GetCoinHistoryParams,
} from './coin.type';

export function getCoinBundles(): Promise<ApiResponse<CoinBundleResponse[]>> {
  return apiClient.get('/coins/bundles');
}

export function getCoinBalance(): Promise<ApiResponse<CoinBalanceResponse>> {
  return apiClient.get('/coins/balance');
}

export function purchaseCoin(
  body: CoinPurchaseRequest,
): Promise<ApiResponse<CoinCheckoutResponse>> {
  return apiClient.post('/coins/purchase', body);
}

export function confirmCoinPayment(
  body: CoinConfirmRequest,
): Promise<ApiResponse<CoinConfirmResponse>> {
  return apiClient.post('/coins/confirm', body);
}

export function getCoinHistory(
  params?: GetCoinHistoryParams,
): Promise<ApiResponse<CoinHistoryResponse>> {
  return apiClient.get('/coins/history', params as Record<string, unknown>);
}

export function useCoinForSlide(
  body: CoinUseSlideRequest,
): Promise<ApiResponse<CoinUseResponse>> {
  return apiClient.post('/coins/use/slide', body);
}

export function useCoinForExam(
  body: CoinUseExamRequest,
): Promise<ApiResponse<CoinUseResponse>> {
  return apiClient.post('/coins/use/exam', body);
}
