import type { SubscriptionStatus } from '../auth/auth.type';

export interface UserProfileResponse {
  id: string;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  grade: string | null;
  major: string | null;
  education: string | null;
  interests: string[];
  learningGoal: string | null;
  preferredTone: string | null;
  subscriptionStatus: SubscriptionStatus;
  subscriptionExpiresAt: string | null;
  xp: number;
  level: number;
  points: number;
  telegramLinked: boolean;
  onboardingCompleted: boolean;
  createdAt: string;
  settings: SettingsResponse;
}

export interface UpdateProfileRequest {
  nickname?: string;
  profileImageUrl?: string;
  grade?: string;
  major?: string;
  education?: string;
  learningGoal?: string;
  interests?: string[];
  preferredTone?: string;
}

export interface OnboardingRequest {
  interests: string[];
  grade: string;
  learningGoal: string;
  preferredTone: string;
  dailyStudyGoalMinutes?: number;
}

export interface OnboardingResponse {
  onboardingCompleted: boolean;
  recommendedTutors: RecommendedTutorDto[];
}

export interface RecommendedTutorDto {
  id: string;
  name: string;
  tagline: string | null;
  profileImageUrl: string | null;
}

export interface SettingsResponse {
  highContrast: boolean;
  keyboardNavigation: boolean;
  dailyStudyGoalMinutes: number;
  notifications: NotificationsResponse;
}

export interface NotificationsResponse {
  lesson: boolean;
  assignment: boolean;
  community: boolean;
  marketing: boolean;
}

export interface UpdateSettingsRequest {
  highContrast?: boolean;
  keyboardNavigation?: boolean;
  dailyStudyGoalMinutes?: number;
  notifications?: NotificationsDto;
}

export interface NotificationsDto {
  lesson?: boolean;
  assignment?: boolean;
  community?: boolean;
  marketing?: boolean;
}

export interface UserStatsResponse {
  totalStudyMinutes: number;
  completedLessons: number;
  streakDays: number;
}

export interface TelegramLinkResponse {
  linkToken: string;
  telegramBotUrl: string;
  expiresAt: string;
}

export interface TelegramStatusResponse {
  linked: boolean;
  telegramChatId: number | null;
  telegramUsername: string | null;
  linkedAt: string | null;
  lastActiveAt: string | null;
}

// 프로필 이미지 업로드 응답. S3 업로드 후 CDN URL을 준다.
export interface UserProfileImageResponse {
  profileImageUrl: string;
}

// 텔레그램 자동 전송 옵션. user_settings.telegram_*_enabled 컬럼과 1:1로 맞는다.
export interface TelegramPreferencesResponse {
  noteEnabled: boolean;
  quizEnabled: boolean;
  friendEnabled: boolean;
}

// 텔레그램 자동 전송 옵션 수정 요청. 빠진 필드는 서버가 기존 값을 유지한다(부분 수정).
export interface UpdateTelegramPreferencesRequest {
  noteEnabled?: boolean;
  quizEnabled?: boolean;
  friendEnabled?: boolean;
}
