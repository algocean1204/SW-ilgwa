// TypeScript 경로 별칭(@/*)을 Vite resolver에 등록한다.
// test 블록은 vitest 전용이라 프로덕션 빌드에 영향이 없다.
import path from "path";
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const viteEnv = loadEnv(mode, process.cwd(), "");
  const apiSpring = viteEnv.VITE_API_BASE_SPRING || "http://localhost:8080";
  const apiFastapi = viteEnv.VITE_API_BASE_FASTAPI || "http://localhost:8000";

  return {
    plugins: [react()],
    resolve: {
      alias: {
        // tsconfig.app.json의 @/* 경로와 맞춘다.
        "@": path.resolve(__dirname, "./src"),
      },
    },
    test: {
      environment: "jsdom",
      globals: true,
      setupFiles: [],
    },
    server: {
      // 브라우저 접속 주소와 맞춰 ERR_CONNECTION_REFUSED가 나지 않게 IPv6 ::1 대신 127.0.0.1에 바인딩한다.
      host: "localhost",
      port: 4173,
      // 포트가 겹치면 다른 번호로 옮기지 않고 바로 실패한다.
      strictPort: true,
      proxy: {
        "/api": {
          target: apiSpring,
          changeOrigin: true,
        },
        "/media": {
          // FastAPI가 생성한 TTS 정적 파일을 dev 서버에서도 같은 경로로 재생한다.
          target: apiFastapi,
          changeOrigin: true,
        },
      },
    },
    preview: {
      // 프리뷰 서버도 dev 서버처럼 IPv4에 바인딩한다.
      host: "127.0.0.1",
      // preview는 4174를 쓴다. strictPort라 dev(4173)와 같은 포트를 쓸 수 없다.
      port: 4174,
      strictPort: true,
    },
  };
});
