/// <reference types="vite/client" />

// Vite 환경변수 타입. 여기 선언한 변수만 import.meta.env에서 타입이 잡힌다.
interface ImportMetaEnv {
  readonly VITE_API_BASE_SPRING: string;
  readonly VITE_API_BASE_FASTAPI: string;
  readonly VITE_GOOGLE_CLIENT_ID: string;
  readonly VITE_KAKAO_CLIENT_ID: string;
  readonly VITE_KAKAO_REDIRECT_URI: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
