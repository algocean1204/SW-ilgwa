// 소셜 로그인과 데모 개발자 로그인 화면. OAuth 설정이 빠져도 앱 마운트는 유지한다.
import { useCallback, useEffect, useRef, useState } from 'react';
import Icon from '@/Icon';
import { env } from '@/core/env';
import { useTweaks, PALETTES } from '@/lib';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import OAuthBrandIcon from '@/components/brand/OAuthBrandIcon';
import { ApiError, devLogin, loginWithGoogle } from '@/apis';
import { isDemoMode } from '@/lib/demoMode';

interface AuthProps {
  setRoute: RouteSetter;
}

interface GoogleCredentialResponse {
  credential?: string;
}

interface GoogleAccountsId {
  initialize: (config: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
  }) => void;
  renderButton: (
    parent: HTMLElement,
    options: {
      theme: 'outline' | 'filled_blue' | 'filled_black';
      size: 'large' | 'medium' | 'small';
      text: 'continue_with' | 'signin_with' | 'signup_with';
      shape: 'rectangular' | 'pill' | 'circle' | 'square';
      logo_alignment: 'left' | 'center';
      width: number;
      locale: string;
    },
  ) => void;
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: GoogleAccountsId;
      };
    };
  }
}

const GOOGLE_IDENTITY_SCRIPT = 'https://accounts.google.com/gsi/client';
const DEV_LOGIN_ENABLED = import.meta.env.DEV || import.meta.env.VITE_DEV_LOGIN === 'true';

let googleScriptPromise: Promise<void> | null = null;

function hasEnvValue(value: string): boolean {
  return value.trim().length > 0;
}

function buildKakaoLoginUrl(): string {
  const params = new URLSearchParams({
    client_id: env.kakaoClientId,
    redirect_uri: env.kakaoRedirectUri,
    response_type: 'code',
    scope: 'account_email',
  });

  return `https://kauth.kakao.com/oauth/authorize?${params.toString()}`;
}

function loadGoogleIdentityScript(): Promise<void> {
  if (window.google?.accounts.id) return Promise.resolve();
  if (googleScriptPromise) return googleScriptPromise;

  googleScriptPromise = new Promise((resolve, reject) => {
    const existingScript = document.querySelector<HTMLScriptElement>(
      `script[src="${GOOGLE_IDENTITY_SCRIPT}"]`,
    );

    if (existingScript) {
      existingScript.addEventListener('load', () => resolve(), { once: true });
      existingScript.addEventListener('error', () => reject(new Error('Google 로그인 스크립트 로드 실패')), {
        once: true,
      });
      return;
    }

    const script = document.createElement('script');
    script.src = GOOGLE_IDENTITY_SCRIPT;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Google 로그인 스크립트 로드 실패'));
    document.head.appendChild(script);
  });

  return googleScriptPromise;
}

// 상태 코드별로 사용자용 한국어 메시지를 고른다. 원문 오류나 스택은 보여 주지 않는다.
function toAuthMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        // 네트워크 오류: 서버 미응답 또는 CORS 차단
        return '서버에 연결할 수 없습니다. 네트워크 상태를 확인해주세요.';
      case 400:
        return '로그인 정보가 올바르지 않습니다. 다시 시도해주세요.';
      case 401:
      case 403:
        // 데모 로그인 403은 backend-spring CORS나 미인증 문제다
        return '로그인 권한이 없거나 인증에 실패했습니다. 다른 계정으로 시도해주세요.';
      case 404:
        return '로그인 서비스를 찾을 수 없습니다. 잠시 후 다시 시도해주세요.';
      case 409:
        return '이미 다른 방법으로 가입된 계정입니다. 기존 로그인 방식을 이용해주세요.';
      case 429:
        return '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.';
      case 500:
      case 502:
      case 503:
        return '서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.';
      default:
        // 알 수 없는 상태 코드는 원문 대신 일반 안내를 보여 준다
        return '로그인 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
    }
  }
  if (error instanceof Error) {
    // Google 스크립트 로드 실패 같은 클라이언트 오류. 원문은 화면에 내지 않는다.
    return 'Google 로그인을 불러오는 데 실패했습니다. 페이지를 새로고침하고 다시 시도해주세요.';
  }
  return '로그인 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
}

function rememberApiTestToken(accessToken: string | undefined): void {
  if (import.meta.env.DEV && accessToken) {
    localStorage.setItem('api_test_access_token', accessToken);
  }
}

function Auth({ setRoute }: AuthProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;
  const [mode, setMode] = useState<"login" | "agree">("login");
  const [googleReady, setGoogleReady] = useState(false);
  const [googleBusy, setGoogleBusy] = useState(false);
  const [devBusy, setDevBusy] = useState(false);
  const [authMessage, setAuthMessage] = useState<string | null>(null);
  const googleButtonRef = useRef<HTMLDivElement | null>(null);
  const hasGoogleOAuth = hasEnvValue(env.googleClientId);
  const hasKakaoOAuth = hasEnvValue(env.kakaoClientId) && hasEnvValue(env.kakaoRedirectUri);
  const kakaoLoginUrl = hasKakaoOAuth ? buildKakaoLoginUrl() : '';

  const completeLogin = useCallback((onboardingCompleted: boolean | undefined) => {
    const postLoginRoute = localStorage.getItem('post_login_route');
    localStorage.removeItem('post_login_route');
    if (postLoginRoute === 'admin') { setRoute('admin'); return; }
    setRoute(onboardingCompleted ? 'home' : 'onboarding');
  }, [setRoute]);

  const handleGoogleCredential = useCallback(
    (response: GoogleCredentialResponse) => {
      if (!response.credential) {
        setAuthMessage('Google ID Token을 받지 못했습니다.');
        return;
      }

      setGoogleBusy(true);
      setAuthMessage('Google 로그인 처리 중입니다.');

      void loginWithGoogle({ idToken: response.credential })
        .then((loginResponse) => {
          rememberApiTestToken(loginResponse.data?.accessToken);
          completeLogin(loginResponse.data?.onboardingCompleted);
        })
        .catch((error: unknown) => {
          setAuthMessage(toAuthMessage(error));
        })
        .finally(() => {
          setGoogleBusy(false);
        });
    },
    [completeLogin],
  );

  const handleDevLogin = useCallback(() => {
    setDevBusy(true);
    setAuthMessage('개발자 로그인 처리 중입니다.');

    void devLogin()
      .then((loginResponse) => {
        rememberApiTestToken(loginResponse.data?.accessToken);
        completeLogin(loginResponse.data?.onboardingCompleted);
      })
      .catch((error: unknown) => {
        setAuthMessage(toAuthMessage(error));
      })
      .finally(() => {
        setDevBusy(false);
      });
  }, [completeLogin]);

  useEffect(() => {
    if (!hasGoogleOAuth) {
      setGoogleReady(false);
      if (googleButtonRef.current) googleButtonRef.current.innerHTML = '';
      return;
    }

    let active = true;
    let renderedButtonElement: HTMLDivElement | null = null;

    void loadGoogleIdentityScript()
      .then(() => {
        if (!active || !window.google?.accounts.id) return;

        window.google.accounts.id.initialize({
          client_id: env.googleClientId,
          callback: handleGoogleCredential,
        });

        if (googleButtonRef.current) {
          renderedButtonElement = googleButtonRef.current;
          renderedButtonElement.innerHTML = '';
          window.google.accounts.id.renderButton(renderedButtonElement, {
            theme: 'outline',
            size: 'large',
            text: 'continue_with',
            shape: 'rectangular',
            logo_alignment: 'left',
            width: 400,
            locale: 'ko',
          });
        }

        setGoogleReady(true);
      })
      .catch((error: unknown) => {
        if (!active) return;
        setAuthMessage(toAuthMessage(error));
      });

    return () => {
      active = false;
      if (renderedButtonElement) renderedButtonElement.innerHTML = '';
    };
  }, [handleGoogleCredential, hasGoogleOAuth, mode]);

  return (
    <div className="auth-shell">
      {/* 왼쪽 브랜드 패널. 패딩은 .auth-hero(반응형)가 맡고 배경색만 인라인으로 둔다 */}
      <div className="auth-hero" style={{
        position:"relative", overflow:"hidden",
        display:"flex", flexDirection:"column", justifyContent:"space-between",
        background:palette.primary,
        color:"var(--surface-strong)",
      }}>
        <div className="dots" style={{ position:"absolute", inset:0, opacity:.15 }} />
        <div style={{ position:"relative" }}>
          <div style={{ display:"flex", alignItems:"center", gap:10 }}>
            <div style={{
              width:36, height:36, borderRadius:10, background:"var(--telegram-overlay-soft)",
              display:"grid", placeItems:"center", fontSize:18, fontWeight:800, backdropFilter:"blur(8px)",
            }}>일</div>
            <div>
              <div style={{ fontSize:14, fontWeight:800, letterSpacing:0 }}>일과 · ilkwa</div>
              <div style={{ fontSize:12, opacity:.8 }}>개인 학습 데스크</div>
            </div>
          </div>
        </div>

        <div style={{ position:"relative", maxWidth:540 }}>
          {/* 폰트 크기는 .auth-hero__title(반응형)에서 정한다 */}
          <h1 className="auth-hero__title" style={{ margin:0, fontWeight:850, letterSpacing:0, lineHeight:1.15 }}>
            교재와 일정이<br/>
            <span className="script">하나의 흐름으로</span><br/>
            정리됩니다
          </h1>
          <p style={{ margin:"18px 0 0", fontSize:14, lineHeight:1.7, opacity:.92, maxWidth:440 }}>
            PDF가 있으면 교재를 분석해서,<br/>
            없어도 주제만 말하면 커리큘럼부터 슬라이드·음성 강의·과제까지 이어서 구성합니다.
          </p>
          <div style={{ display:"flex", gap:10, marginTop:24, flexWrap:"wrap" }}>
            {["PDF 업로드","주제 입력","음성 강의","텔레그램 과제"].map(label=>(
              <span key={label} style={{
                fontSize:12, fontWeight:700, padding:"6px 11px", borderRadius:999,
                background:"var(--white-overlay-md)", backdropFilter:"blur(8px)",
                border:"1px solid var(--telegram-overlay-soft)",
              }}>{label}</span>
            ))}
          </div>
        </div>

        <div style={{ position:"relative", fontSize:12, opacity:.7 }}>
          © 2026 ilkwa. 한국어 학습에 최적화된 개인 과외 플랫폼.
        </div>
      </div>

      {/* 오른쪽 로그인 패널. 패딩은 .auth-panel(반응형)에서 정한다 */}
      <div className="auth-panel" style={{
        display:"flex", flexDirection:"column", justifyContent:"center",
        background:"var(--paper)",
      }}>
        {mode === "login" ? (
          <>
            <div style={{ fontSize:12, fontWeight:700, color:"var(--muted)", letterSpacing:".15em", textTransform:"uppercase", marginBottom:12 }}>
              SIGN IN
            </div>
            <h2 style={{ margin:"0 0 8px", fontSize:28, fontWeight:850, color:"var(--ink)", letterSpacing:0 }}>
              {isDemoMode ? '데모 체험' : '시작할까요?'}
            </h2>
            <p style={{ margin:"0 0 28px", fontSize:13, color:"var(--ink-3)", lineHeight:1.6 }}>
              {isDemoMode ? (
                <>
                  <b style={{ color:"var(--ink-2)" }}>포트폴리오 정적 데모입니다.</b><br/>
                  아래 버튼으로 바로 데모 계정에 입장해 3과목 강의를 체험할 수 있어요.
                </>
              ) : (
                <>
                  <b style={{ color:"var(--ink-2)" }}>소셜 계정 하나면 충분해요.</b><br/>
                  처음 오시는 분은 자동으로 계정이 만들어지고, 간단한 온보딩으로 안내해드려요.
                </>
              )}
            </p>

            {isDemoMode && DEV_LOGIN_ENABLED && (
              <button
                type="button"
                onClick={handleDevLogin}
                disabled={devBusy}
                className="lift"
                style={{
                  width:"100%", padding:"16px 20px", borderRadius:12,
                  display:"flex", alignItems:"center", gap:12,
                  background: palette.primary, border:`1.5px solid ${palette.primary}`,
                  fontSize:15, fontWeight:800, color:"var(--paper)", marginBottom:20,
                  opacity: devBusy ? .65 : 1, cursor: devBusy ? "wait" : "pointer",
                }}
              >
                <Icon name="play" size={18} color="var(--paper)" />
                <span style={{ flex:1, textAlign:"left" }}>
                  {devBusy ? '데모 입장 중...' : '데모 체험하기'}
                </span>
              </button>
            )}

            {!isDemoMode && hasGoogleOAuth && (
              <div style={{
                position:"relative", width:"100%", height:52, marginBottom:10,
                borderRadius:12, overflow:"hidden",
                opacity: googleBusy ? .6 : 1, pointerEvents: googleBusy ? "none" : "auto",
              }}>
                <div style={{
                  display:"flex", alignItems:"center", gap:12,
                  padding:"14px 18px",
                  background:"var(--paper)", border:"1.5px solid var(--line)",
                  borderRadius:12,
                  fontSize:14, fontWeight:700, color:"var(--ink)",
                  cursor:"pointer", height:"100%", boxSizing:"border-box",
                }}>
                  <OAuthBrandIcon provider="google" size={20} />
                  <span style={{ flex:1, textAlign:"left" }}>
                    {googleReady ? 'Google 계정으로 계속하기' : 'Google 로그인 준비 중'}
                  </span>
                </div>
                <div ref={googleButtonRef} style={{
                  position:"absolute", top:0, left:0,
                  width:"100%", height:"100%",
                  opacity:0.01, zIndex:2,
                  display:"flex", alignItems:"center",
                }} />
              </div>
            )}

            {!isDemoMode && hasKakaoOAuth && (
              <a href={kakaoLoginUrl} className="lift" style={{
                width:"100%", padding:"14px 18px", borderRadius:12,
                display:"flex", alignItems:"center", gap:12,
                background:"var(--kakao-bg)", border:"1.5px solid var(--kakao-border)",
                fontSize:14, fontWeight:800, color:"var(--kakao-ink)", marginBottom:10,
                textDecoration:"none",
              }}>
                <OAuthBrandIcon provider="kakao" size={20} />
                <span style={{ flex:1, textAlign:"left" }}>카카오로 3초만에 시작</span>
              </a>
            )}

            {!isDemoMode && DEV_LOGIN_ENABLED && (
              <button
                type="button"
                onClick={handleDevLogin}
                disabled={devBusy}
                className="lift"
                style={{
                  width:"100%", padding:"14px 18px", borderRadius:12,
                  display:"flex", alignItems:"center", gap:12,
                  background:"var(--ink)", border:"1.5px solid var(--ink)",
                  fontSize:14, fontWeight:800, color:"var(--paper)", marginBottom:20,
                  opacity: devBusy ? .65 : 1, cursor: devBusy ? "wait" : "pointer",
                }}
              >
                <span aria-hidden="true">🔧</span>
                <span style={{ flex:1, textAlign:"left" }}>
                  {devBusy ? '개발자 로그인 처리 중' : '개발자 로그인(데모)'}
                </span>
              </button>
            )}

            {!hasGoogleOAuth && !hasKakaoOAuth && !DEV_LOGIN_ENABLED && (
              <div style={{
                padding:"10px 12px", borderRadius:10,
                background:"var(--sand)", border:"1px solid var(--line-2)",
                color:"var(--ink-2)", fontSize:12, lineHeight:1.5, marginBottom:20,
              }}>
                로그인 환경 설정이 필요합니다.
              </div>
            )}

            {authMessage && (
              <div style={{
                padding:"10px 12px", borderRadius:10,
                background:"var(--sand)", border:"1px solid var(--line-2)",
                color:"var(--ink-2)", fontSize:12, lineHeight:1.5, marginTop:-4,
              }}>
                {authMessage}
              </div>
            )}

            <div style={{ fontSize:12, color:"var(--ink-3)", marginTop:22, lineHeight:1.6, textAlign:"center" }}>
              계속을 누르면{" "}
              <button onClick={()=>setMode("agree")} style={{ color:palette.primary, fontWeight:700, textDecoration:"underline" }}>이용약관</button>과{" "}
              <button onClick={()=>setMode("agree")} style={{ color:palette.primary, fontWeight:700, textDecoration:"underline" }}>개인정보 처리방침</button>에 동의하는 것으로 간주됩니다.
            </div>
          </>
        ) : (
          <Terms palette={palette} onBack={()=>setMode("login")} />
        )}
      </div>
    </div>
  );
}

interface TermsProps {
  palette: Palette;
  onBack: () => void;
}
function Terms({ palette, onBack }: TermsProps) {
  return (
    <>
      <button onClick={onBack} style={{ fontSize:12, color:"var(--ink-3)", marginBottom:12, textAlign:"left", display:"flex", alignItems:"center", gap:4 }}><Icon name="chevron-left" size={11} /> 돌아가기</button>
      <h2 style={{ margin:"0 0 16px", fontSize:22, fontWeight:800, color:"var(--ink)", letterSpacing:0 }}>
        이용약관 · 개인정보
      </h2>
      <div style={{
        padding:"18px 20px", borderRadius:12, background:"var(--bg)",
        border:"1px solid var(--line-2)", fontSize:12, color:"var(--ink-2)", lineHeight:1.7, maxHeight:340, overflow:"auto",
      }} className="scroll">
        <p><b>제1조 (서비스)</b> 일과(ilkwa)는 PDF 분석 또는 주제 입력을 통해 맞춤 커리큘럼·슬라이드·음성 강의·과제·채점을 제공합니다.</p>
        <p><b>제2조 (계정)</b> 계정은 구글 또는 카카오 소셜 로그인을 통해서만 생성·인증됩니다. 자체 이메일 비밀번호 가입은 제공하지 않습니다.</p>
        <p><b>제3조 (학습 데이터)</b> 업로드한 PDF와 학습 기록은 개인 SHM에 암호화 저장되며, 동의 없이 제3자에게 제공되지 않습니다.</p>
        <p><b>제4조 (튜터 설정)</b> 사용자가 설정한 튜터의 목소리와 말투는 계정 내에서만 사용됩니다.</p>
        <p><b>제5조 (환불)</b> 구독은 결제일 기준 7일 이내 전액 환불됩니다.</p>
      </div>
      <div style={{ display:"flex", gap:10, marginTop:18 }}>
        <button onClick={onBack} className="lift" style={{
          flex:1, padding:"12px 16px", borderRadius:10, fontSize:13, fontWeight:700,
          background:palette.primary, color:"var(--surface-strong)",
        }}>동의하고 계속</button>
      </div>
    </>
  );
}

export default Auth;
