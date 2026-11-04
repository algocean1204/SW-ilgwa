// 전역 앱 상태 타입

import type { SubscriptionPlan } from './user';

/** 앱 전역 상태 (Context로 공유) */
export interface AppState {
  /**
   * 인증 부트스트랩 완료 여부. false면 refresh 중이라 보호 API를 호출하지 않는다.
   * App.tsx 마운트 직후 /api/auth/refresh가 끝나면(성공·실패 무관) true가 된다.
   */
  authReady: boolean;
  /** 현재 활성 과외 id */
  activeCourseId: string;
  setActiveCourseId: (id: string) => void;
  /** AI 코인 잔고 */
  coins: number;
  setCoins: (coins: number) => void;
  /** 구독 플랜 */
  plan: SubscriptionPlan;
  setPlan: (plan: SubscriptionPlan) => void;
  /**
   * 내 과제 화면의 현재 과외 필터 값.
   * 'all'이면 전체 과제, 그 외에는 courseId로 거른다.
   * PageContextSidebar의 filter 항목과 같은 값을 쓴다.
   */
  activeHomeworkFilter: string;
  setActiveHomeworkFilter: (filter: string) => void;
  /** 내 과제 화면에서 선택한 과제 id. 사이드바 목록과 디테일 패널이 같이 쓴다. */
  selectedHomeworkId: string;
  setSelectedHomeworkId: (id: string) => void;
  /**
   * 현재 서브뷰 ID. 컨텍스트 사이드바의 'subview' 항목과 같은 값을 쓴다.
   * null이면 페이지 기본 뷰(overview)를 보인다.
   */
  pageSubView: string | null;
  setPageSubView: (v: string | null) => void;
  /** 열린 모달 키. null이면 모달 없음 */
  activeModal: string | null;
  setActiveModal: (key: string | null) => void;
  /** 수강 중인 과외가 있는지. null이면 아직 확인 전 */
  hasCourses: boolean | null;
  setHasCourses: (v: boolean) => void;
  /** 커리큘럼에서 고른 챕터 id. 학습 페이지에 들어가면 이 챕터로 간다. */
  selectedChapterId: string;
  setSelectedChapterId: (id: string) => void;
}

/** Tweaks 패널 설정 값 (TWEAKS_DEFAULTS 구조) */
export interface TweaksState {
  view: string;
  palette: string;
  motionIntensity: number;
  showAurora: boolean;
  showSpotlight: boolean;
  accent: string;
}
