import { useEffect, useMemo, useState } from 'react';
import {
  CheckCircle2,
  KeyRound,
  Link,
  RefreshCcw,
  Save,
  Send,
  UserRound,
} from 'lucide-react';
import { env } from '@/core/env';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import {
  ApiError,
  completeOnboarding,
  getMyStats,
  getProfile,
  getTelegramStatus,
  linkTelegram,
  loginWithGoogle,
  loginWithKakao,
  setAccessToken,
  updateProfile,
  updateSettings,
} from '@/apis';
import type { ApiResponse, LoginResponse } from '@/apis';

type ApiResult = {
  ok: boolean;
  title: string;
  status?: number;
  payload: unknown;
};

const TOKEN_STORAGE_KEY = 'api_test_access_token';
const API_BASE_LABEL = import.meta.env.DEV
  ? `/api -> ${env.apiSpring}/api`
  : `${env.apiSpring}/api`;

const emptyToUndefined = (value: string): string | undefined => {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
};

function toApiResult(title: string, error: unknown): ApiResult {
  if (error instanceof ApiError) {
    return {
      ok: false,
      title,
      status: error.status,
      payload: error.errorResponse ?? { message: error.message },
    };
  }

  if (error instanceof Error) {
    return { ok: false, title, payload: { message: error.message } };
  }

  return { ok: false, title, payload: error };
}

function ApiTest() {
  const [token, setToken] = useState('');
  const [googleIdToken, setGoogleIdToken] = useState('');
  const [kakaoCode, setKakaoCode] = useState('');
  const [nickname, setNickname] = useState('');
  const [grade, setGrade] = useState('고등학교 2학년');
  const [learningGoal, setLearningGoal] = useState('수능 대비');
  const [preferredTone, setPreferredTone] = useState('격려형');
  const [interestsText, setInterestsText] = useState('수학, 과학');
  const [highContrast, setHighContrast] = useState(false);
  const [keyboardNavigation, setKeyboardNavigation] = useState(true);
  const [lessonNotification, setLessonNotification] = useState(true);
  const [marketingNotification, setMarketingNotification] = useState(false);
  const [loadingTitle, setLoadingTitle] = useState<string | null>(null);
  const [result, setResult] = useState<ApiResult | null>(null);

  useEffect(() => {
    const savedToken = localStorage.getItem(TOKEN_STORAGE_KEY) ?? '';
    setToken(savedToken);
    setAccessToken(savedToken || null);
  }, []);

  const interests = useMemo(
    () =>
      interestsText
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean),
    [interestsText],
  );

  const rememberToken = (nextToken: string): void => {
    setToken(nextToken);
    setAccessToken(nextToken || null);
    if (nextToken) {
      localStorage.setItem(TOKEN_STORAGE_KEY, nextToken);
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
    }
  };

  const run = async <T,>(title: string, request: () => Promise<T>): Promise<void> => {
    setLoadingTitle(title);
    try {
      const payload = await request();
      setResult({ ok: true, title, payload });
    } catch (error) {
      setResult(toApiResult(title, error));
    } finally {
      setLoadingTitle(null);
    }
  };

  const runLogin = async (
    title: string,
    request: () => Promise<ApiResponse<LoginResponse>>,
  ): Promise<void> => {
    await run(title, async () => {
      const response = await request();
      if (response.data?.accessToken) {
        rememberToken(response.data.accessToken);
      }
      return response;
    });
  };

  const profileBody = {
    nickname: emptyToUndefined(nickname),
    grade: emptyToUndefined(grade),
    learningGoal: emptyToUndefined(learningGoal),
    interests,
    preferredTone: emptyToUndefined(preferredTone),
  };

  return (
    <main className="min-h-screen bg-[var(--bg)] px-6 py-8 text-[var(--ink)]">
      <div className="mx-auto flex max-w-6xl flex-col gap-5">
        <header className="flex flex-col gap-2 border-b border-[var(--line)] pb-5">
          <div className="text-xs font-extrabold tracking-[0.16em] text-[var(--ink-3)]">
            LOCAL API TEST
          </div>
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h1 className="m-0 text-3xl font-black tracking-normal">Spring API 연동 확인</h1>
              <p className="mt-2 text-sm text-[var(--ink-2)]">
                Base URL: <code>{API_BASE_LABEL}</code>
              </p>
            </div>
            <Button
              variant="outline"
              onClick={() => run('GET /users/me', getProfile)}
              disabled={!!loadingTitle}
            >
              <RefreshCcw />
              프로필 새로고침
            </Button>
          </div>
        </header>

        <section className="grid gap-4 lg:grid-cols-[420px_1fr]">
          <div className="flex flex-col gap-4">
            <Panel title="Access Token" icon={<KeyRound size={17} />}>
              <Textarea
                value={token}
                onChange={(event) => setToken(event.target.value)}
                placeholder="로그인 응답의 accessToken을 붙여넣으세요"
                className="min-h-28 font-mono text-xs"
              />
              <div className="flex gap-2">
                <Button onClick={() => rememberToken(token.trim())}>
                  <Save />
                  토큰 저장
                </Button>
                <Button variant="outline" onClick={() => rememberToken('')}>
                  지우기
                </Button>
              </div>
            </Panel>

            <Panel title="Auth" icon={<Send size={17} />}>
              <Field label="Google ID Token">
                <Textarea
                  value={googleIdToken}
                  onChange={(event) => setGoogleIdToken(event.target.value)}
                  placeholder="Google ID Token"
                  className="min-h-20 font-mono text-xs"
                />
              </Field>
              <Button
                variant="outline"
                disabled={!!loadingTitle || !googleIdToken.trim()}
                onClick={() =>
                  runLogin('POST /auth/login/google', () =>
                    loginWithGoogle({ idToken: googleIdToken.trim() }),
                  )
                }
              >
                Google 로그인 요청
              </Button>

              <Field label="Kakao Authorization Code">
                <Input
                  value={kakaoCode}
                  onChange={(event) => setKakaoCode(event.target.value)}
                  placeholder="Kakao authorization code"
                />
              </Field>
              <Button
                variant="outline"
                disabled={!!loadingTitle || !kakaoCode.trim()}
                onClick={() =>
                  runLogin('POST /auth/login/kakao', () =>
                    loginWithKakao({ authorizationCode: kakaoCode.trim() }),
                  )
                }
              >
                Kakao 로그인 요청
              </Button>
            </Panel>
          </div>

          <div className="grid gap-4">
            <Panel title="User Read" icon={<UserRound size={17} />}>
              <div className="grid gap-2 sm:grid-cols-3">
                <Button disabled={!!loadingTitle} onClick={() => run('GET /users/me', getProfile)}>
                  내 프로필
                </Button>
                <Button
                  variant="outline"
                  disabled={!!loadingTitle}
                  onClick={() => run('GET /users/telegram/status', getTelegramStatus)}
                >
                  텔레그램 상태
                </Button>
                <Button
                  variant="outline"
                  disabled={!!loadingTitle}
                  onClick={() => run('GET /users/me/stats', getMyStats)}
                >
                  내 통계
                </Button>
              </div>
            </Panel>

            <Panel title="Profile / Onboarding" icon={<CheckCircle2 size={17} />}>
              <div className="grid gap-3 md:grid-cols-2">
                <Field label="닉네임">
                  <Input
                    value={nickname}
                    onChange={(event) => setNickname(event.target.value)}
                    placeholder="비워두면 전송하지 않음"
                  />
                </Field>
                <Field label="학년">
                  <Input value={grade} onChange={(event) => setGrade(event.target.value)} />
                </Field>
                <Field label="학습 목표">
                  <Input
                    value={learningGoal}
                    onChange={(event) => setLearningGoal(event.target.value)}
                  />
                </Field>
                <Field label="선호 말투">
                  <Input
                    value={preferredTone}
                    onChange={(event) => setPreferredTone(event.target.value)}
                  />
                </Field>
                <Field label="관심사, 쉼표 구분">
                  <Input
                    value={interestsText}
                    onChange={(event) => setInterestsText(event.target.value)}
                  />
                </Field>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button
                  disabled={!!loadingTitle}
                  onClick={() => run('PUT /users/me', () => updateProfile(profileBody))}
                >
                  프로필 수정
                </Button>
                <Button
                  variant="outline"
                  disabled={!!loadingTitle || interests.length === 0}
                  onClick={() =>
                    run('POST /users/onboarding', () =>
                      completeOnboarding({
                        interests,
                        grade,
                        learningGoal,
                        preferredTone,
                      }),
                    )
                  }
                >
                  온보딩 완료 요청
                </Button>
              </div>
            </Panel>

            <Panel title="Settings / Telegram" icon={<Link size={17} />}>
              <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                <Toggle
                  label="고대비"
                  checked={highContrast}
                  onChange={setHighContrast}
                />
                <Toggle
                  label="키보드 네비게이션"
                  checked={keyboardNavigation}
                  onChange={setKeyboardNavigation}
                />
                <Toggle
                  label="강의 알림"
                  checked={lessonNotification}
                  onChange={setLessonNotification}
                />
                <Toggle
                  label="마케팅 알림"
                  checked={marketingNotification}
                  onChange={setMarketingNotification}
                />
              </div>
              <div className="flex flex-wrap gap-2">
                <Button
                  disabled={!!loadingTitle}
                  onClick={() =>
                    run('PUT /users/settings', () =>
                      updateSettings({
                        highContrast,
                        keyboardNavigation,
                        notifications: {
                          lesson: lessonNotification,
                          marketing: marketingNotification,
                        },
                      }),
                    )
                  }
                >
                  설정 저장
                </Button>
                <Button
                  variant="outline"
                  disabled={!!loadingTitle}
                  onClick={() => run('POST /users/telegram/link', linkTelegram)}
                >
                  텔레그램 링크 발급
                </Button>
              </div>
            </Panel>

            <Panel title="Response" icon={<RefreshCcw size={17} />}>
              <div className="flex min-h-10 items-center justify-between gap-3 rounded-lg border border-[var(--line-2)] bg-[var(--sand)] px-3 py-2 text-sm">
                <span className="font-bold">
                  {loadingTitle ? `${loadingTitle} 요청 중` : result?.title ?? '아직 요청 없음'}
                </span>
                {result && (
                  <span className={result.ok ? 'text-[var(--grass-ink)]' : 'text-[var(--danger)]'}>
                    {result.ok ? 'OK' : `ERROR${result.status ? ` ${result.status}` : ''}`}
                  </span>
                )}
              </div>
              <pre className="max-h-[420px] overflow-auto rounded-lg bg-[var(--ink)] p-4 text-xs leading-6 text-[var(--paper)]">
                {result ? JSON.stringify(result.payload, null, 2) : '버튼을 눌러 응답을 확인하세요.'}
              </pre>
            </Panel>
          </div>
        </section>
      </div>
    </main>
  );
}

interface PanelProps {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}

function Panel({ title, icon, children }: PanelProps) {
  return (
    <section className="rounded-lg border border-[var(--line)] bg-[var(--paper)] p-4 shadow-[var(--shadow-sm)]">
      <div className="mb-3 flex items-center gap-2 text-sm font-black text-[var(--ink)]">
        {icon}
        <span>{title}</span>
      </div>
      <div className="flex flex-col gap-3">{children}</div>
    </section>
  );
}

interface FieldProps {
  label: string;
  children: React.ReactNode;
}

function Field({ label, children }: FieldProps) {
  return (
    <label className="grid gap-1.5 text-xs font-bold text-[var(--ink-2)]">
      <span>{label}</span>
      {children}
    </label>
  );
}

interface ToggleProps {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}

function Toggle({ label, checked, onChange }: ToggleProps) {
  return (
    <label className="flex min-h-10 items-center gap-2 rounded-lg border border-[var(--line-2)] bg-[var(--surface)] px-3 text-sm font-bold">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>{label}</span>
    </label>
  );
}

export default ApiTest;
