# frontend-web

SW-EZ-Team의 웹 프론트엔드 저장소다.

## 개요

사용자 화면을 그리는 웹 클라이언트 코드가 들어 있다.
React + TS + Tailwind + shadcn/ui + Motion(Vite SPA)으로 만들었고,
백엔드(Spring Boot, FastAPI)와 REST로 통신한다.

UI는 shadcn/ui로 골격을 만들고 Tailwind CSS와 Framer Motion(현 Motion)으로 시각 효과를 더한다.

## 기술 스택

| 항목 | 버전 · 비고 |
|------|-------------|
| Node.js | 22.x LTS |
| Vite | 8.0.8 |
| React | 18.3.1 |
| TypeScript | 6.0.2 (strict 모드) |
| React Router | 6.30.3 (설치돼 있지만 라우팅은 `App.tsx`의 상태 기반 라우터가 하고 URL은 pushState로 맞춘다) |
| Tailwind CSS | 4.1.8 (유틸리티 CSS) |
| shadcn/ui | Radix UI 기반 컴포넌트 모음 (`components.json` 관리) |
| Framer Motion(현 Motion) | 12.38.0 (페이지 전환·인터랙션 애니메이션) |
| 패키지 관리 | npm |

## 요구 환경

- Node.js 22.x LTS (`.nvmrc`가 있어 `nvm use`로 맞출 수 있다)
- npm 10+

## 실행 방법

```bash
# 의존성 설치
npm install

# 개발 서버 실행 (http://localhost:4173)
npm run dev

# 프로덕션 빌드
npm run build

# 빌드 결과물 미리보기 (http://127.0.0.1:4174)
npm run preview
```

## 백엔드 연결 방법

```bash
# 예제 파일을 복사해 실제 환경변수 파일 생성
cp .env.example .env

# .env 파일에서 각 백엔드 주소를 실제 URL 로 수정
```

### 환경변수 목록

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `VITE_API_BASE_SPRING` | Spring Boot REST 엔드포인트 | `http://localhost:8080` |
| `VITE_API_BASE_FASTAPI` | FastAPI AI 파이프라인 (Claude · Modal GPU · OCR) | `http://localhost:8082` |

## 폴더 구조

```
src/
├── main.tsx          # 앱 진입점 (전역 ErrorBoundary)
├── App.tsx           # 앱 라우터와 전역 상태
├── routes/           # 라우트 키와 화면 매핑
├── views/            # 페이지 컴포넌트
├── components/       # 화면별 하위 컴포넌트와 공용 UI
├── api/              # ChapterStudio_V1 API 클라이언트
├── apis/             # 도메인별 API 함수와 타입, 공통 client
├── core/             # env.ts(환경변수), toss.ts(결제 SDK)
├── lib/              # 팔레트, 컨텍스트, 공용 훅
├── shared/           # 공용 타입, 상수, 모션 프리셋, VAD 훅
└── vite-env.d.ts     # Vite 환경변수 타입 선언
```

## Docker 빌드 (프로덕션)

```bash
docker build -t frontend-web .
docker run -p 3000:3000 frontend-web
```

내부 포트는 3000이고, nginx가 SPA 폴백(`/index.html`)을 처리한다.

## 관련 저장소

- [backend-spring](https://github.com/SW-EZ-Team/backend-spring): Spring Boot 메인 API 백엔드
- [backend-fastapi](https://github.com/SW-EZ-Team/backend-fastapi): FastAPI AI 파이프라인 백엔드
