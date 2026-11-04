// 사용자 프로필 관련 타입

/** 구독 플랜 */
export type SubscriptionPlan = 'free' | 'pro' | 'premium';

/** 사용자 프로필 */
export interface User {
  id: string;
  name: string;
  email: string;
  /** 레벨 */
  level: number;
  /** 학습 목표 */
  goal?: string;
  plan: SubscriptionPlan;
}
