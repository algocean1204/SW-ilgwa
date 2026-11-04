// 라우트 매핑. 라우트 타입·상수·헬퍼를 App.tsx에서 분리했다.
import React, { useEffect } from 'react';
import type { RouteKey, RouteSetter, AppState } from '@/shared/types';
import { ENABLE_ADMIN, ENABLE_EDITOR } from '@/shared/constants/featureFlags';

import Landing from '@/views/Landing';
import Auth from '@/views/Auth';
import KakaoCallback from '@/views/KakaoCallback';
import Onboarding from '@/views/Onboarding';
import StudentHome from '@/views/StudentHome';
import CourseExplore from '@/views/CourseExplore';
import CourseDetail from '@/views/CourseDetail';
import MyCourses from '@/views/MyCourses';
import StudentLearning from '@/views/StudentLearning';
import MemoryNotes from '@/views/MemoryNotes';
import Report from '@/views/Report';
import MyHomework from '@/views/MyHomework';
import MockExam from '@/views/MockExam';
import AIMockExam from '@/views/AIMockExam';
import TutorCustomize from '@/views/TutorCustomize';
import Pricing from '@/views/Pricing';
import Settings from '@/views/Settings';
// ApiTest는 항상 임포트하고 라우트 연결만 DEV 여부로 나눈다.
// import.meta.env.DEV === false면 Vite tree-shake가 이 모듈을 번들에서 뺀다.
import ApiTest from '@/views/ApiTest';
import LessonEditor from '@/views/LessonEditor';
import StudyCalendar from '@/views/StudyCalendar';
import AdminConsole from '@/views/AdminConsole';
import PaymentCallback from '@/views/PaymentCallback';

/** 공통 뷰 props (라우팅, 앱 상태) */
export interface ViewProps {
  setRoute: RouteSetter;
  appState: AppState;
}

/**
 * 프로덕션용 apiTest 차단 컴포넌트. apiTest에 직접 들어오면 landing으로 보낸다.
 * DEV에서는 ROUTES에 실제 ApiTest가 연결돼 이 컴포넌트를 쓰지 않는다.
 */
function ApiTestProductionGuard({ setRoute }: ViewProps) {
  useEffect(() => {
    setRoute('landing');
  }, [setRoute]);
  // 리다이렉트가 끝날 때까지 빈 화면을 둔다
  return null;
}

/**
 * admin 라우트 게이트. ENABLE_ADMIN이 false면 landing으로 보낸다.
 */
function AdminGate({ setRoute }: ViewProps) {
  useEffect(() => {
    setRoute('landing');
  }, [setRoute]);
  return null;
}

/**
 * editor 라우트 게이트. ENABLE_EDITOR가 false면 landing으로 보낸다.
 * 강사 레슨 에디터는 하드코딩 목업이라 출시 버전에 보이지 않는다.
 */
function EditorGate({ setRoute }: ViewProps) {
  useEffect(() => {
    setRoute('landing');
  }, [setRoute]);
  return null;
}

// 상단 크롬 유형: none(크롬 없음) | sidebar(워크스페이스 셸)
export type ChromeType = 'none' | 'sidebar';

// 라우트 메타데이터
export interface RouteConfig {
  comp: React.ComponentType<ViewProps>;
  chrome: ChromeType;
  label: string;
  full?: boolean;
}

// 라우트 → 컴포넌트 매핑. App 렌더 밖에서도 쓰도록 모듈 상수로 둔다.
export const ROUTES: Record<RouteKey, RouteConfig> = {
  // 마케팅 / 인증
  landing:    { comp: Landing,         chrome: "none",    label: "랜딩" },
  auth:       { comp: Auth,            chrome: "none",    label: "로그인" },
  kakaoCallback: { comp: KakaoCallback, chrome: "none",    label: "카카오 로그인" },
  onboarding: { comp: Onboarding,      chrome: "none",    label: "온보딩" },
  // 학생
  home:       { comp: StudentHome,     chrome: "sidebar", label: "홈" },
  explore:    { comp: CourseExplore,   chrome: "sidebar", label: "새 과외" },
  courseDetail: { comp: CourseDetail,  chrome: "sidebar", label: "커리큘럼 확인" },
  myCourses:  { comp: MyCourses,       chrome: "sidebar", label: "내 과외" },
  learning:   { comp: StudentLearning, chrome: "sidebar", label: "과외 중", full: true },
  notes:      { comp: MemoryNotes,     chrome: "sidebar", label: "암기노트" },
  calendar:   { comp: StudyCalendar,   chrome: "sidebar", label: "학습 캘린더" },
  report:     { comp: Report,          chrome: "sidebar", label: "학습 리포트" },
  myHomework: { comp: MyHomework,      chrome: "sidebar", label: "내 과제" },
  mockExam:   { comp: MockExam,        chrome: "sidebar", label: "모의고사" },
  aiMockExam: { comp: AIMockExam,      chrome: "sidebar", label: "실전 모의고사" },
  // 튜터 + 결제 + 설정
  tutor:      { comp: TutorCustomize,  chrome: "sidebar", label: "튜터 설정" },
  pricing:    { comp: Pricing,         chrome: "sidebar", label: "구독 · 코인" },
  settings:   { comp: Settings,        chrome: "sidebar", label: "설정" },
  // apiTest는 DEV에서 실제 화면, 프로덕션에서 landing 리다이렉트를 연결한다
  apiTest:    { comp: import.meta.env.DEV ? ApiTest : ApiTestProductionGuard, chrome: "none", label: "API 테스트", full: true },
  // 강사. ENABLE_EDITOR가 false면 landing 리다이렉트를 연결한다.
  editor:     { comp: ENABLE_EDITOR ? LessonEditor : EditorGate,    chrome: "none",    label: "강사 에디터", full: true },
  // 운영. ENABLE_ADMIN이 false면 landing 리다이렉트를 연결한다.
  admin:      { comp: ENABLE_ADMIN ? AdminConsole : AdminGate,    chrome: "none",    label: "관리자" },
  paymentCallback: { comp: PaymentCallback, chrome: "none", label: "결제 처리" },
};

// 화면 인덱스를 2자리 문자열로 바꾼다 (data-screen-label 속성용)
export function pad(n: number): string { return String(n).padStart(2, "0"); }

// 라우트 키의 순서 인덱스. 개발 도구에서 화면을 구분하는 데 쓴다.
export function idxOf(r: RouteKey): number {
  const order: RouteKey[] = [
    "landing", "auth", "kakaoCallback", "onboarding", "home", "explore", "courseDetail", "myCourses",
    "learning", "notes", "calendar",
    "report", "myHomework", "mockExam", "aiMockExam", "tutor", "pricing", "settings", "apiTest", "editor", "admin", "paymentCallback",
  ];
  const i = order.indexOf(r);
  return i < 0 ? 99 : i + 1;
}
