// 관리자 API. SUPER_ADMIN 전용 엔드포인트다.
import { apiClient } from '../client';
import type { ApiResponse, OffsetPageResponse } from '../types';
import type {
  AdminTelegramResponse,
  AdminUserListResponse,
  AdminUserSuspendResponse,
  AdminDashboardResponse,
  AddRecipientRequest,
  AlertRuleListResponse,
  AlertRuleResponse,
  BroadcastRequest,
  BroadcastResponse,
  GetAdminUsersParams,
  GetTelegramMessagesParams,
  RecipientListResponse,
  SuspendRequest,
  TelegramChatIdRequest,
  TelegramCredentialResponse,
  TelegramCredentialUpdateRequest,
  TelegramCredentialUpdateResponse,
  TelegramMessageListResponse,
  TelegramSessionListResponse,
  UpdateAlertRuleRequest,
} from './admin.type';

export function getAdminUsers(
  params?: GetAdminUsersParams,
): Promise<ApiResponse<OffsetPageResponse<AdminUserListResponse>>> {
  const query = new URLSearchParams();
  if (params?.keyword) query.set('keyword', params.keyword);
  if (params?.subscriptionStatus) query.set('subscriptionStatus', params.subscriptionStatus);
  if (params?.status) query.set('status', params.status);
  if (params?.page != null) query.set('page', String(params.page));
  if (params?.size != null) query.set('size', String(params.size));
  const qs = query.toString();
  return apiClient.get(`/admin/users${qs ? `?${qs}` : ''}`);
}

export function suspendUser(
  userId: string,
  body?: SuspendRequest,
): Promise<ApiResponse<AdminUserSuspendResponse>> {
  return apiClient.put(`/admin/users/${userId}/suspend`, body);
}

export function setUserTelegramChatId(
  userId: string,
  body: TelegramChatIdRequest,
): Promise<ApiResponse<AdminTelegramResponse>> {
  return apiClient.post(`/admin/users/${userId}/telegram/chat-id`, body);
}

export function getTelegramMessages(
  params: GetTelegramMessagesParams,
): Promise<ApiResponse<TelegramMessageListResponse>> {
  const query = new URLSearchParams({
    chatId: String(params.chatId),
    from: params.from,
    to: params.to,
  });
  return apiClient.get(`/admin/telegram/messages?${query.toString()}`);
}

export function getTelegramSessions(
  userId: string,
): Promise<ApiResponse<TelegramSessionListResponse>> {
  return apiClient.get(`/admin/telegram/sessions?userId=${userId}`);
}

export function getTelegramCredential(): Promise<ApiResponse<TelegramCredentialResponse>> {
  return apiClient.get('/admin/telegram-credential');
}

export function updateTelegramCredential(
  body: TelegramCredentialUpdateRequest,
): Promise<ApiResponse<TelegramCredentialUpdateResponse>> {
  return apiClient.patch('/admin/telegram-credential', body);
}

export function getAdminDashboard(): Promise<ApiResponse<AdminDashboardResponse>> {
  return apiClient.get('/admin/dashboard');
}

export function sendBroadcast(body: BroadcastRequest): Promise<ApiResponse<BroadcastResponse>> {
  return apiClient.post('/admin/broadcast', body);
}

// 텔레그램 수신자 목록
export function getRecipients(): Promise<ApiResponse<RecipientListResponse>> {
  return apiClient.get('/admin/telegram/recipients');
}

export function addRecipient(body: AddRecipientRequest): Promise<ApiResponse<RecipientListResponse>> {
  return apiClient.post('/admin/telegram/recipients', body);
}

export function deleteRecipient(id: string): Promise<ApiResponse<void>> {
  return apiClient.delete<void>('/admin/telegram/recipients/' + id);
}

// 알림 규칙
export function getAlertRules(): Promise<ApiResponse<AlertRuleListResponse>> {
  return apiClient.get('/admin/alert-rules');
}

export function updateAlertRule(id: string, body: UpdateAlertRuleRequest): Promise<ApiResponse<AlertRuleResponse>> {
  return apiClient.patch('/admin/alert-rules/' + id, body);
}
