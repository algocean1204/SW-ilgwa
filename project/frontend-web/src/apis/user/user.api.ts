import { apiClient } from '../client';
import type { ApiResponse } from '../types';
import type {
  OnboardingRequest,
  OnboardingResponse,
  SettingsResponse,
  TelegramLinkResponse,
  TelegramPreferencesResponse,
  TelegramStatusResponse,
  UpdateProfileRequest,
  UpdateSettingsRequest,
  UserProfileImageResponse,
  UpdateTelegramPreferencesRequest,
  UserProfileResponse,
  UserStatsResponse,
} from './user.type';

export function getProfile(): Promise<ApiResponse<UserProfileResponse>> {
  return apiClient.get<UserProfileResponse>('/users/me');
}

export function updateProfile(
  body: UpdateProfileRequest,
): Promise<ApiResponse<UserProfileResponse>> {
  return apiClient.put<UserProfileResponse>('/users/me', body);
}

export function completeOnboarding(
  body: OnboardingRequest,
): Promise<ApiResponse<OnboardingResponse>> {
  return apiClient.post<OnboardingResponse>('/users/onboarding', body);
}

export function linkTelegram(): Promise<ApiResponse<TelegramLinkResponse>> {
  return apiClient.post<TelegramLinkResponse>('/users/telegram/link');
}

export function getTelegramStatus(): Promise<ApiResponse<TelegramStatusResponse>> {
  return apiClient.get<TelegramStatusResponse>('/users/telegram/status');
}

export function getTelegramPreferences(): Promise<ApiResponse<TelegramPreferencesResponse>> {
  return apiClient.get<TelegramPreferencesResponse>('/users/telegram/preferences');
}

export function updateTelegramPreferences(
  body: UpdateTelegramPreferencesRequest,
): Promise<ApiResponse<TelegramPreferencesResponse>> {
  return apiClient.put<TelegramPreferencesResponse>('/users/telegram/preferences', body);
}

export function updateSettings(
  body: UpdateSettingsRequest,
): Promise<ApiResponse<SettingsResponse>> {
  return apiClient.put<SettingsResponse>('/users/settings', body);
}

export function getMyStats(): Promise<ApiResponse<UserStatsResponse>> {
  return apiClient.get<UserStatsResponse>('/users/me/stats');
}

// 사용자 프로필 이미지 업로드. multipart/form-data, 필드명 "file", jpg/png/webp 5MB 이하
export function uploadUserProfileImage(
  file: FormData,
): Promise<ApiResponse<UserProfileImageResponse>> {
  return apiClient.upload('/users/me/profile-image', file);
}

// 사용자 프로필 이미지 삭제. S3에서 지우고 응답 본문은 없다.
export function deleteUserProfileImage(): Promise<void> {
  return apiClient.del('/users/me/profile-image');
}
