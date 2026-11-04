export type SubscriptionStatus = 'FREE' | 'PRO' | 'PREMIUM' | 'EXPIRED';

export interface GoogleLoginRequest {
  idToken: string;
}

export interface KakaoLoginRequest {
  authorizationCode: string;
}

export interface LoginResponse {
  accessToken: string;
  userId: string;
  email: string;
  nickname: string;
  onboardingCompleted: boolean;
  subscriptionStatus: SubscriptionStatus;
}

export type DevLoginResponse = LoginResponse;

export interface TokenRefreshResponse {
  accessToken: string;
}
