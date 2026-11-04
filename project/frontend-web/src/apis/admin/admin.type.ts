// 관리자 API 요청/응답 타입. 백엔드 admin 도메인 DTO와 맞춘다.

export type AdminSubscriptionStatus = 'FREE' | 'PRO' | 'PREMIUM' | 'EXPIRED';
export type AdminUserStatus = 'ACTIVE' | 'SUSPENDED';
export type AdminUserRole = 'USER' | 'ADMIN' | 'SUPER_ADMIN';
export type TelegramChatIdAction = 'SET' | 'UNLINK' | 'RELINK';

export interface AdminUserListResponse {
  id: string;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  subscriptionStatus: AdminSubscriptionStatus;
  role: AdminUserRole;
  status: AdminUserStatus;
  telegramLinked: boolean;
  telegramChatId: number | null;
  createdAt: string;
}

export interface AdminUserSuspendResponse {
  id: string;
  status: AdminUserStatus;
  reason: string | null;
  suspendedAt: string | null;
}

export interface SuspendRequest {
  reason?: string;
}

export interface AdminTelegramResponse {
  linked: boolean;
  telegramChatId: number | null;
  linkedAt: string | null;
}

export interface TelegramMessageItem {
  id: string;
  direction: string;
  messageType: string;
  text: string;
  imageUrl: string | null;
  referenceType: string | null;
  referenceId: string | null;
  telegramMsgId: string | null;
  sentAt: string;
}

export interface TelegramMessageListResponse {
  messages: TelegramMessageItem[];
}

export interface TelegramSessionItem {
  id: string;
  sessionType: string;
  startedAt: string;
  status: string;
  summary: string | null;
}

export interface TelegramSessionListResponse {
  sessions: TelegramSessionItem[];
}

export interface TelegramCredentialResponse {
  botTokenFingerprint: string;
  adminChatId: number | null;
  lastRotatedAt: string | null;
  lastHealthCheckAt: string | null;
}

export interface TelegramCredentialUpdateResponse {
  botTokenFingerprint: string;
  lastRotatedAt: string | null;
  healthCheckPassed: boolean;
}

export interface GetAdminUsersParams {
  keyword?: string;
  subscriptionStatus?: AdminSubscriptionStatus;
  status?: AdminUserStatus;
  page?: number;
  size?: number;
}

export interface TelegramChatIdRequest {
  chatId: number | null;
  action: TelegramChatIdAction;
}

export interface TelegramCredentialUpdateRequest {
  botToken?: string;
  adminChatId?: number;
}

export interface GetTelegramMessagesParams {
  chatId: number;
  from: string;
  to: string;
}

export interface AdminDashboardResponse {
  totalUsers: number;
  weeklySubmissionRate: number;
  averageProgress: number;
  unansweredCount: number;
}

export interface BroadcastRequest {
  message: string;
  targetUserIds?: string[] | null;
}

export interface BroadcastResponse {
  sentCount: number;
  failedCount: number;
  failedUserIds: string[];
}

// 텔레그램 수신자
export interface RecipientResponse {
  id: string;
  nickname: string;
  chatId: number;
  createdAt: string;
}

export interface RecipientListResponse {
  recipients: RecipientResponse[];
}

export interface AddRecipientRequest {
  nickname: string;
  chatId: number;
}

// 알림 규칙
export type AlertEventType =
  | 'PAYMENT_FAILED'
  | 'QUERY_ERROR'
  | 'AUTH_FAILURE'
  | 'SYSTEM_ERROR'
  | 'SUBMISSION_TIMEOUT'
  | 'SUBSCRIPTION_EXPIRED'
  | 'CURRICULUM_GENERATION_FAILED';

export type AlertSeverity = 'INFO' | 'WARN' | 'CRITICAL';

export interface AlertRuleResponse {
  id: string;
  eventType: AlertEventType;
  displayName: string;
  enabled: boolean;
  severity: AlertSeverity;
  updatedAt: string;
}

export interface AlertRuleListResponse {
  rules: AlertRuleResponse[];
}

export interface UpdateAlertRuleRequest {
  enabled?: boolean;
  severity?: AlertSeverity;
}
