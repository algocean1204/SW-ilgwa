import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, CheckCircle2, Loader2 } from 'lucide-react';
import { ApiError, loginWithKakao } from '@/apis';
import type { ApiResponse, LoginResponse } from '@/apis';
import type { RouteSetter } from '@/shared/types';

interface KakaoCallbackProps {
  setRoute: RouteSetter;
}

type CallbackState = 'loading' | 'success' | 'error';

let kakaoLoginPromise: Promise<ApiResponse<LoginResponse>> | null = null;

function getKakaoLoginPromise(code: string): Promise<ApiResponse<LoginResponse>> {
  kakaoLoginPromise ??= loginWithKakao({ authorizationCode: code }).finally(() => {
    kakaoLoginPromise = null;
  });
  return kakaoLoginPromise;
}

function toMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.errorResponse?.message ?? error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '카카오 로그인 처리 중 오류가 발생했습니다.';
}

function KakaoCallback({ setRoute }: KakaoCallbackProps) {
  const callbackParams = useMemo(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const oauthError = params.get('error_description') ?? params.get('error');
    return { code, oauthError };
  }, []);

  const [state, setState] = useState<CallbackState>(
    callbackParams.oauthError || !callbackParams.code ? 'error' : 'loading',
  );
  const [message, setMessage] = useState(
    callbackParams.oauthError ??
      (callbackParams.code ? '카카오 인증 정보를 확인하고 있습니다.' : '카카오 인증 코드가 없습니다.'),
  );

  useEffect(() => {
    let active = true;
    const { code, oauthError } = callbackParams;

    if (oauthError || !code) {
      return () => {
        active = false;
      };
    }

    void getKakaoLoginPromise(code)
      .then(async (response) => {
        if (!active) return;
        if (import.meta.env.DEV && response.data?.accessToken) {
          localStorage.setItem('api_test_access_token', response.data.accessToken);
        }

        setState('success');
        setMessage('로그인 완료. 프로필을 불러오는 중입니다.');
        window.history.replaceState({}, '', '/');

        if (!active) return;
        setRoute(response.data?.onboardingCompleted ? 'home' : 'onboarding');
      })
      .catch((error: unknown) => {
        if (!active) return;
        setState('error');
        setMessage(toMessage(error));
      });

    return () => {
      active = false;
    };
  }, [callbackParams, setRoute]);

  const isLoading = state === 'loading';
  const isSuccess = state === 'success';

  return (
    <main className="grid min-h-screen place-items-center bg-[var(--bg)] px-6 text-[var(--ink)]">
      <section className="w-full max-w-md rounded-lg border border-[var(--line)] bg-[var(--paper)] p-6 text-center shadow-[var(--shadow-md)]">
        <div className="mx-auto mb-4 grid size-12 place-items-center rounded-lg bg-[var(--sand)]">
          {isLoading && <Loader2 className="animate-spin text-[var(--ink-2)]" size={24} />}
          {isSuccess && <CheckCircle2 className="text-[var(--grass-ink)]" size={24} />}
          {!isLoading && !isSuccess && <AlertCircle className="text-[var(--danger)]" size={24} />}
        </div>
        <h1 className="m-0 text-xl font-black tracking-normal">
          {isSuccess ? '카카오 로그인 완료' : isLoading ? '카카오 로그인 처리 중' : '카카오 로그인 실패'}
        </h1>
        <p className="mt-3 text-sm leading-6 text-[var(--ink-2)]">{message}</p>
        {state === 'error' && (
          <button
            className="mt-5 h-9 rounded-lg bg-[var(--ink)] px-4 text-sm font-bold text-[var(--paper)]"
            onClick={() => setRoute('auth')}
          >
            로그인 화면으로 돌아가기
          </button>
        )}
      </section>
    </main>
  );
}

export default KakaoCallback;
