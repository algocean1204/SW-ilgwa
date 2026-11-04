const optional = (value: string | undefined): string => value ?? '';

// 빈 값은 same-origin 상대경로로 폴백한다는 뜻이라 오류가 아니다.
// DEV에서만 키마다 한 번 디버그 로그를 남긴다.
const _warnedKeys = new Set<string>();
const optionalApiBase = (value: string | undefined, key: string): string => {
  if (value) return value;
  if (import.meta.env.DEV && !_warnedKeys.has(key)) {
    _warnedKeys.add(key);
    console.debug(`[env] ${key} 미설정 → 상대경로(/api)로 요청합니다.`);
  }
  return '';
};

export const env = {
  // Spring Boot: 메인 REST API와 SSE 실시간 강의 스트리밍
  // 빈 값이면 client.ts가 same-origin /api 로 폴백한다.
  apiSpring: optionalApiBase(import.meta.env.VITE_API_BASE_SPRING, 'VITE_API_BASE_SPRING'),
  // Rust OCR 서버: 이미지 전처리와 OCR gRPC 파이프라인 (FastAPI와 연동)
  apiRust: optionalApiBase(import.meta.env.VITE_API_BASE_RUST, 'VITE_API_BASE_RUST'),
  // FastAPI + Celery: AI 파이프라인 (Claude, Modal GPU, OCR 추론)
  apiFastapi: optionalApiBase(import.meta.env.VITE_API_BASE_FASTAPI, 'VITE_API_BASE_FASTAPI'),
  // Google OAuth: Google Identity Services로 ID Token을 받을 때 쓰는 공개 Client ID
  googleClientId: optional(import.meta.env.VITE_GOOGLE_CLIENT_ID),
  // Kakao OAuth: Kakao 인증 화면으로 이동할 때 쓰는 공개 설정
  kakaoClientId: optional(import.meta.env.VITE_KAKAO_CLIENT_ID),
  kakaoRedirectUri: optional(import.meta.env.VITE_KAKAO_REDIRECT_URI),
} as const;
