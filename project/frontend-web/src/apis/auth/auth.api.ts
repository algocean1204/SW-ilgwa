import { apiClient, setAccessToken } from '../client';
import type {
  DevLoginResponse,
  GoogleLoginRequest,
  KakaoLoginRequest,
  LoginResponse,
  TokenRefreshResponse,
} from './auth.type';
import type { ApiResponse } from '../types';

const storeAccessToken = <T extends { accessToken: string }>(
  response: ApiResponse<T>,
): ApiResponse<T> => {
  if (response.data?.accessToken) {
    setAccessToken(response.data.accessToken);
  }
  return response;
};

export async function loginWithGoogle(
  body: GoogleLoginRequest,
): Promise<ApiResponse<LoginResponse>> {
  const response = await apiClient.post<LoginResponse>('/auth/login/google', body);
  return storeAccessToken(response);
}

export async function loginWithKakao(
  body: KakaoLoginRequest,
): Promise<ApiResponse<LoginResponse>> {
  const response = await apiClient.post<LoginResponse>('/auth/login/kakao', body);
  return storeAccessToken(response);
}

export async function devLogin(): Promise<ApiResponse<DevLoginResponse>> {
  const response = await apiClient.post<DevLoginResponse>('/auth/dev-login');
  return storeAccessToken(response);
}

export async function refresh(): Promise<ApiResponse<TokenRefreshResponse>> {
  const response = await apiClient.post<TokenRefreshResponse>('/auth/refresh');
  return storeAccessToken(response);
}

export async function logout(): Promise<void> {
  await apiClient.post<void>('/auth/logout');
}

/** 회원 탈퇴(소프트 삭제). 토큰이 필요한 보호 경로라서 PUBLIC_PATH_PREFIXES에 넣지 않는다. */
export async function withdraw(): Promise<void> {
  await apiClient.del('/auth/withdraw');
}
