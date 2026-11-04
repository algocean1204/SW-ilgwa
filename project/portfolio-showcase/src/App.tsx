// 전체 앱 라우터. 상태 초기화, 컨텍스트 주입, 레이아웃 결정을 한다.
// 뷰 임포트·라우트 맵·워크스페이스 셸·헬퍼는 각 파일에 있다.
import React, { useState, useEffect, useRef } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { TweaksCtx, AppStateCtx, TWEAKS_DEFAULTS } from '@/lib';
import { TweaksPanel } from '@/Tweaks';
import DevRouteJumper from '@/components/DevRouteJumper';
import { ROUTES, pad, idxOf } from '@/routes/config';
import type { ViewProps } from '@/routes/config';
import WorkspaceShell from '@/components/layout/WorkspaceShell';
import type {
  AppState,
  TweaksState,
  SubscriptionPlan,
  RouteKey,
  RouteSetter,
} from '@/shared/types';
import { ROUTE_KEYS } from '@/shared/types/route';
import { slideUp } from '@/shared/motion/variants';
import { TRANSITION_SLOW } from '@/shared/motion/transitions';
import { AUTH_EXPIRED_EVENT, clearAccessToken, getAccessToken, markBootstrapComplete, setAccessToken, getCoinBalance, getCurrentSubscription, getMyCourses, getProfile } from '@/apis';
import { isDemoMode } from '@/lib/demoMode';

// 인증이 필요 없는 라우트. 여기서는 보호 API 호출을 건너뛴다.
// 비로그인 첫 진입에서 401 → refresh 실패 → AUTH_EXPIRED 루프가 생기지 않게 한다.
const PUBLIC_ROUTES: ReadonlySet<RouteKey> = new Set([
  "landing", "auth", "kakaoCallback", "paymentCallback",
]);

// 문자열이 유효한 SubscriptionPlan인지 확인한다
function isSubscriptionPlan(v: string | null | undefined): v is SubscriptionPlan {
  return v === 'free' || v === 'pro' || v === 'premium';
}

function isRouteKey(v: string | null | undefined): v is RouteKey {
  return !!v && ROUTE_KEYS.includes(v as RouteKey);
}

function isPersistableRoute(route: RouteKey): boolean {
  return route !== "kakaoCallback" && route !== "paymentCallback";
}

const COURSE_REQUIRED_ROUTES: ReadonlySet<RouteKey> = new Set([
  "home", "learning", "courseDetail", "myCourses", "myHomework",
  "calendar", "notes", "report", "aiMockExam", "mockExam",
]);

// refresh API를 직접 호출한다. client.ts의 refreshToken과 별개로 앱 시작 시 1회만 돈다.
// 성공하면 accessToken을 메모리에 두고, 실패(401 포함)는 조용히 anonymous로 둔다.
async function bootstrapAuth(): Promise<void> {
  try {
    const BASE_URL = import.meta.env.DEV ? '/api' : '/api';
    const res = await fetch(`${BASE_URL}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    });
    if (res.ok) {
      const body = (await res.json()) as { data?: { accessToken?: string } };
      if (body.data?.accessToken) {
        setAccessToken(body.data.accessToken);
      }
    }
    // 401 등 실패는 오류를 던지지 않고 anonymous로 둔다
  } catch {
    // 네트워크 오류도 사용자에게 보이지 않고 anonymous로 둔다
  }
}

function App() {
  // 인증 부트스트랩 완료 플래그. false면 /api/auth/refresh 중이라
  // 보호 API(getMyCourses, getCoinBalance 등)를 호출하지 않는다.
  // true가 된 뒤에야 로그인/anonymous 상태가 정해진다.
  const [authReady, setAuthReady] = useState(false);

  // 첫 마운트에서 세션을 복구한다. 쿠키에 refresh token이 있으면 accessToken을 되살린다.
  // 랜딩에서도 세션 복구가 필요할 수 있어 PUBLIC_ROUTES와 관계없이 실행한다.
  // 끝나면(성공·실패 무관) authReady를 true로 바꿔 보호 API 게이트를 연다.
  useEffect(() => {
    // bootstrapAuth는 컴포넌트 밖 함수라서 deps가 필요 없다.
    // markBootstrapComplete는 client.ts의 bootstrapReadyPromise를 resolve해 게이트를 연다.
    // React 상태 갱신보다 게이트가 먼저 열리도록 setAuthReady보다 앞에서 호출한다.
    void bootstrapAuth().finally(() => {
      markBootstrapComplete();
      setAuthReady(true);
    });
  }, []);

  const [tweaks, setTweaks] = useState<TweaksState>(() => {
    try {
      const s = localStorage.getItem("tweaks_state");
      if (s) return { ...TWEAKS_DEFAULTS, ...(JSON.parse(s) as Partial<TweaksState>) };
    } catch (_e) { /* 파싱 실패 시 기본값 사용 */ }
    return TWEAKS_DEFAULTS;
  });
  useEffect(() => {
    try { localStorage.setItem("tweaks_state", JSON.stringify(tweaks)); } catch (_e) { /* 저장 실패 무시 */ }
  }, [tweaks]);

  const [route, setRoute] = useState<RouteKey>(() => {
    try {
      if (window.location.pathname === "/oauth/kakao") return "kakaoCallback";
      const queryRoute = new URLSearchParams(window.location.search).get("route");
      if (isRouteKey(queryRoute)) return queryRoute;
      const stored = localStorage.getItem("route");
      // 데모는 첫 화면 없이 메인으로 연다. 예전에 저장된 landing·auth 경로도 무시한다.
      if (isDemoMode) return isRouteKey(stored) && stored !== "landing" && stored !== "auth" ? stored : "home";
      if (isRouteKey(stored)) return stored;
      return isRouteKey(tweaks.view) ? tweaks.view : "landing";
    } catch (_e) { return "landing"; }
  });
  useEffect(() => {
    try {
      if (isPersistableRoute(route)) localStorage.setItem("route", route);
    } catch (_e) { /* 저장 실패 무시 */ }
  }, [route]);

  // 지금 학습하거나 보고 있는 과외 (sidebar / MyCourses → Learning)
  const [activeCourseId, setActiveCourseId] = useState<string>(() => {
    try { return localStorage.getItem("activeCourseId") ?? ""; }
    catch (_e) { return ""; }
  });
  // effect가 오래된 클로저 값을 읽지 않도록 activeCourseId 최신값을 ref에 둔다
  const activeCourseIdRef = useRef(activeCourseId);
  useEffect(() => { activeCourseIdRef.current = activeCourseId; }, [activeCourseId]);
  useEffect(() => {
    try {
      if (activeCourseId) localStorage.setItem("activeCourseId", activeCourseId);
      else localStorage.removeItem("activeCourseId");
    } catch (_e) { /* 저장 실패 무시 */ }
  }, [activeCourseId]);

  // 세션 복구 후 온보딩을 끝내지 않은 사용자는 온보딩으로 보낸다
  useEffect(() => {
    if (!authReady || !getAccessToken() || PUBLIC_ROUTES.has(route)) return;
    if (route === 'onboarding') return;
    void getProfile().then((res) => {
      if (res.data && !res.data.onboardingCompleted) setRoute('onboarding');
    }).catch(() => {});
  }, [authReady, route]);

  // authReady 이후 과외 목록을 불러, activeCourseId가 비었거나 없는 ID면 첫 과외로 정한다.
  // deps를 [authReady, route]로만 둬서 setActiveCourseId가 이 effect를 다시 돌리지 않게 한다.
  // activeCourseId 최신값은 ref로 읽는다.
  useEffect(() => {
    // 부트스트랩 전, 토큰 없음, public 라우트에서는 보호 API를 호출하지 않는다
    if (!authReady || !getAccessToken() || PUBLIC_ROUTES.has(route)) return;
    void getMyCourses({ page: 0, size: 50 }).then((res) => {
      const courses = res.data?.content ?? [];
      if (courses.length === 0) return; // 과외가 없으면 그대로 둔다
      const current = activeCourseIdRef.current;
      const isValid = !!current && courses.some((c) => c.id === current);
      // 이미 유효하면 사용자가 고른 과외를 유지한다
      if (isValid) return;
      setActiveCourseId(courses[0]!.id);
    }).catch((err: unknown) => {
      console.warn('[App] activeCourseId 초기 세팅 중 과외 목록 조회 실패:', err);
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authReady, route]);

  // AI 코인 잔고. 결제 전 사용자가 유료 잔액으로 오해하지 않게 0에서 시작해 API 값으로 바꾼다.
  // authReady가 false면 부트스트랩 전이라 호출하지 않는다.
  const [coins, setCoins] = useState<number>(0);
  useEffect(() => {
    if (!authReady || !getAccessToken() || PUBLIC_ROUTES.has(route)) return;
    void getCoinBalance().then((res) => {
      if (typeof res.data?.balance === 'number') setCoins(res.data.balance);
    }).catch(() => { setCoins(0); });
  }, [route, authReady]);
  useEffect(() => {
    try { localStorage.setItem("coins", String(coins)); } catch (_e) { /* 저장 실패 무시 */ }
  }, [coins]);

  const [plan, setPlan] = useState<SubscriptionPlan>('free');
  useEffect(() => {
    // authReady가 false면 구독 조회를 미룬다. 비로그인 사용자는 free다.
    if (!authReady || !getAccessToken() || PUBLIC_ROUTES.has(route)) return;
    void getCurrentSubscription().then((res) => {
      const subscription = res.data;
      if (subscription?.status === 'active' && isSubscriptionPlan(subscription.planCode)) {
        setPlan(subscription.planCode);
        return;
      }
      setPlan('free');
    }).catch(() => { setPlan('free'); });
  }, [route, authReady]);
  useEffect(() => {
    try { localStorage.setItem("plan", plan); } catch (_e) { /* 저장 실패 무시 */ }
  }, [plan]);

  // 내 과제 화면 과외 필터 (기본 'all', 전체 과제)
  const [activeHomeworkFilter, setActiveHomeworkFilter] = useState<string>('all');

  // 내 과제 화면에서 선택한 과제 id. 사이드바 목록과 디테일 패널이 같이 쓴다.
  const [selectedHomeworkId, setSelectedHomeworkId] = useState<string>('hw-1');

  // 컨텍스트 사이드바 서브뷰. null이면 전체 보기, 문자열이면 그 섹션만 보인다.
  const [pageSubView, setPageSubView] = useState<string | null>(null);

  // 열린 모달 키. null이면 모달 없음
  const [activeModal, setActiveModal] = useState<string | null>(null);

  // 수강 중인 과외가 있는지. null이면 아직 API 확인 전
  const [hasCourses, setHasCourses] = useState<boolean | null>(null);

  // 커리큘럼에서 고른 챕터 id. 학습 페이지에 들어가면 이 챕터로 간다.
  const [selectedChapterId, setSelectedChapterId] = useState('');

  const appState: AppState = {
    authReady,
    activeCourseId, setActiveCourseId,
    coins, setCoins,
    plan, setPlan,
    activeHomeworkFilter, setActiveHomeworkFilter,
    selectedHomeworkId, setSelectedHomeworkId,
    pageSubView, setPageSubView,
    activeModal, setActiveModal,
    hasCourses, setHasCourses,
    selectedChapterId, setSelectedChapterId,
  };

  const renderRoute: RouteKey =
    hasCourses === false && COURSE_REQUIRED_ROUTES.has(route) ? "explore" : route;

  const R = ROUTES[renderRoute] ?? ROUTES.landing!;
  const View = R.comp as React.ComponentType<ViewProps>;

  // 라우트가 바뀌면 맨 위로 스크롤한다
  useEffect(() => { window.scrollTo({ top: 0, behavior: "instant" }); }, [renderRoute]);

  useEffect(() => {
    const handleAuthExpired = () => {
      clearAccessToken();
      setPageSubView(null);
      setActiveModal(null);
      setRoute("auth");
    };

    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
  }, []);

  // 브라우저 뒤로/앞으로 버튼을 누르면 URL에서 route를 읽어 상태를 되돌린다.
  // pushState는 popstate를 일으키지 않아 루프를 막는 플래그가 필요 없다.
  useEffect(() => {
    const handlePopState = (e: PopStateEvent) => {
      // history state의 route를 먼저 쓰고, 없으면 URL 쿼리를 다시 읽는다
      const stateRoute = (e.state as { route?: string } | null)?.route;
      const queryRoute = new URLSearchParams(window.location.search).get("route");
      const next = stateRoute ?? queryRoute;
      if (isRouteKey(next)) {
        setRoute(hasCourses === false && COURSE_REQUIRED_ROUTES.has(next) ? "explore" : next);
        setPageSubView(null);
        setActiveModal(null);
      }
    };
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  // 렌더 시점의 hasCourses를 캡처해야 해서 deps에 넣는다
  }, [hasCourses]);

  // 라우트가 바뀌면 서브뷰와 모달을 비워 새 페이지를 전체 뷰로 연다.
  // 주소창·뒤로가기·새로고침이 같은 화면을 보이도록 URL도 pushState로 바꾼다.
  const setRouteTyped: RouteSetter = (r) => {
    const effective = hasCourses === false && COURSE_REQUIRED_ROUTES.has(r) ? "explore" : r;
    // URL이 같으면 history 항목을 또 쌓지 않는다
    const currentQuery = new URLSearchParams(window.location.search).get("route");
    if (currentQuery !== effective) {
      window.history.pushState({ route: effective }, "", `?route=${effective}`);
    }
    setRoute(effective);
    setPageSubView(null);
    setActiveModal(null);
  };

  return (
    <TweaksCtx.Provider value={tweaks}>
      <AppStateCtx.Provider value={appState}>
        <div data-screen-label={`${pad(idxOf(renderRoute))} ${R.label}`} style={{ minHeight: "100vh", background: "var(--bg)" }}>
          {R.chrome === "sidebar" ? (
            <WorkspaceShell route={renderRoute} setRoute={setRouteTyped} appState={appState}>
              <AnimatePresence mode="wait">
                <motion.div
                  key={renderRoute}
                  variants={slideUp}
                  initial="hidden"
                  animate="visible"
                  exit="exit"
                  transition={TRANSITION_SLOW}
                >
                  <View setRoute={setRouteTyped} appState={appState} />
                </motion.div>
              </AnimatePresence>
            </WorkspaceShell>
          ) : (
            <AnimatePresence mode="wait">
              <motion.div
                key={renderRoute}
                variants={slideUp}
                initial="hidden"
                animate="visible"
                exit="exit"
                transition={TRANSITION_SLOW}
              >
                <View setRoute={setRouteTyped} appState={appState} />
              </motion.div>
            </AnimatePresence>
          )}
        </div>

        {/* 개발용 화면 이동 도구 (DEV에서만) */}
        {import.meta.env.DEV && (
          <DevRouteJumper route={renderRoute} setRoute={setRouteTyped} />
        )}
        {import.meta.env.DEV && <TweaksPanel tweaks={tweaks} setTweaks={setTweaks} />}
      </AppStateCtx.Provider>
    </TweaksCtx.Provider>
  );
}

export default App;
