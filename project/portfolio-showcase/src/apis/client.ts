import { env } from '../core/env';
import type { ApiResponse, ErrorResponse } from './types';
import { ApiError } from './types';

const BASE_URL = import.meta.env.DEV ? '/api' : (env.apiSpring ? `${env.apiSpring}/api` : '/api');
export const AUTH_EXPIRED_EVENT = 'ezteam:auth-expired';

let accessToken: string | null = null;
let refreshPromise: Promise<boolean> | null = null;

// 콜드 로드에서 보호 API가 refresh보다 먼저 나가지 않게 하는 부트스트랩 게이트.
// App.tsx의 bootstrapAuth가 끝나면(성공·실패 무관) resolve된다.
// 보호 경로 요청은 이 Promise를 기다린 뒤에만 네트워크로 나간다.
// /api/auth/*와 /api/auth/dev-login은 부트스트랩 자체라서 게이트를 건너뛴다.
let bootstrapResolve: (() => void) | null = null;
const bootstrapReadyPromise: Promise<void> = new Promise<void>((resolve) => {
  bootstrapResolve = resolve;
});

// App.tsx의 bootstrapAuth().finally()에서 호출한다.
// 이미 resolve된 Promise라서 여러 번 호출해도 문제없다.
export function markBootstrapComplete(): void {
  if (bootstrapResolve) {
    bootstrapResolve();
    bootstrapResolve = null;
  }
}

// 부트스트랩 게이트를 건너뛸 공개 경로 접두사.
// 토큰 없이 호출하거나 토큰을 발급하는 엔드포인트다.
const PUBLIC_PATH_PREFIXES = [
  '/auth/refresh',
  '/auth/login',
  '/auth/dev-login',
  '/auth/logout',
] as const;

function isPublicPath(path: string): boolean {
  return PUBLIC_PATH_PREFIXES.some((prefix) => path.startsWith(prefix));
}

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function clearAccessToken(): void {
  accessToken = null;
}

function notifyAuthExpired(): void {
  clearAccessToken();
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
  }
}

const authHeaders = (contentType?: string): Record<string, string> => ({
  ...(contentType ? { 'Content-Type': contentType } : {}),
  ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
});

const bearerHeaders = (): Record<string, string> =>
  accessToken ? { Authorization: `Bearer ${accessToken}` } : {};

const retryHeaders = (body: BodyInit | null | undefined): Record<string, string> =>
  body instanceof FormData ? bearerHeaders() : authHeaders(body ? 'application/json' : undefined);

async function refreshToken(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE_URL}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    });
    if (!res.ok) {
      clearAccessToken();
      return false;
    }
    const body = (await res.json()) as ApiResponse<{ accessToken: string }>;
    if (body.data?.accessToken) {
      setAccessToken(body.data.accessToken);
      return true;
    }
    clearAccessToken();
    return false;
  } catch {
    clearAccessToken();
    return false;
  }
}

async function handleUnauthorized(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = refreshToken().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function parseErrorResponse(res: Response): Promise<ErrorResponse | null> {
  try {
    const body = (await res.json()) as { error?: ErrorResponse };
    return body.error ?? null;
  } catch {
    return null;
  }
}

async function fetchWithRefresh(url: string, init: RequestInit): Promise<Response> {
  let res: Response;

  try {
    res = await fetch(url, init);
  } catch {
    throw new ApiError(0, null);
  }

  if (res.status !== 401) {
    return res;
  }

  if (!(await handleUnauthorized())) {
    notifyAuthExpired();
    // 리프레시 실패는 컴포넌트 catch가 아니라 이벤트 리다이렉트가 처리하도록 throw한다
    throw new ApiError(401, null);
  }

  try {
    const retryRes = await fetch(url, { ...init, headers: retryHeaders(init.body) });
    if (retryRes.status === 401) {
      notifyAuthExpired();
      throw new ApiError(401, null);
    }
    return retryRes;
  } catch (e) {
    if (e instanceof ApiError) throw e;
    throw new ApiError(0, null);
  }
}

async function request<T>(
  path: string,
  method: HttpMethod,
  body?: unknown,
): Promise<ApiResponse<T>> {
  // 보호 경로는 부트스트랩(refresh)이 끝난 뒤에만 보낸다.
  // 콜드 F5에서 보호 API가 /api/auth/refresh보다 먼저 나가지 않게 한다.
  if (!isPublicPath(path)) {
    await bootstrapReadyPromise;
  }
  const url = `${BASE_URL}${path}`;
  const init: RequestInit = {
    method,
    credentials: 'include',
    headers: authHeaders(body !== undefined ? 'application/json' : undefined),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  };
  const res = await fetchWithRefresh(url, init);

  if (!res.ok) {
    throw new ApiError(res.status, await parseErrorResponse(res));
  }

  return res.json() as Promise<ApiResponse<T>>;
}

function buildQuery(params: Record<string, unknown>): string {
  const qs = new URLSearchParams();
  for (const [key, val] of Object.entries(params)) {
    if (val !== null && val !== undefined) {
      qs.append(key, String(val));
    }
  }
  const str = qs.toString();
  return str ? `?${str}` : '';
}

export const apiClient = {
  get<T>(path: string, params?: Record<string, unknown>): Promise<ApiResponse<T>> {
    const query = params ? buildQuery(params) : '';
    return request<T>(`${path}${query}`, 'GET');
  },

  post<T>(path: string, body?: unknown): Promise<ApiResponse<T>> {
    return request<T>(path, 'POST', body);
  },

  put<T>(path: string, body?: unknown): Promise<ApiResponse<T>> {
    return request<T>(path, 'PUT', body);
  },

  patch<T>(path: string, body?: unknown): Promise<ApiResponse<T>> {
    return request<T>(path, 'PATCH', body);
  },

  async del(path: string): Promise<void> {
    // 보호 경로이므로 부트스트랩 완료를 기다린다
    if (!isPublicPath(path)) await bootstrapReadyPromise;
    const res = await fetchWithRefresh(`${BASE_URL}${path}`, {
      method: 'DELETE',
      credentials: 'include',
      headers: authHeaders(),
    });

    if (!res.ok) {
      throw new ApiError(res.status, await parseErrorResponse(res));
    }
  },

  delete<T>(path: string): Promise<ApiResponse<T>> {
    return request<T>(path, 'DELETE');
  },

  async upload<T>(path: string, formData: FormData): Promise<ApiResponse<T>> {
    // 보호 경로이므로 부트스트랩 완료를 기다린다
    if (!isPublicPath(path)) await bootstrapReadyPromise;
    const res = await fetchWithRefresh(`${BASE_URL}${path}`, {
      method: 'POST',
      credentials: 'include',
      headers: bearerHeaders(),
      body: formData,
    });

    if (!res.ok) {
      throw new ApiError(res.status, await parseErrorResponse(res));
    }

    return res.json() as Promise<ApiResponse<T>>;
  },
};
