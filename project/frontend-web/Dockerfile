# 멀티 스테이지 프로덕션 빌드
# 스테이지 1: Node 22 Alpine에서 Vite로 빌드한다.
FROM node:22-alpine AS builder

WORKDIR /app

# 의존성 레이어를 캐시하려고 package.json을 먼저 복사한다.
COPY package.json package-lock.json ./
RUN npm ci --frozen-lockfile

COPY . .

# Vite는 빌드 시점의 VITE_* 값만 클라이언트 번들에 주입한다.
ARG VITE_API_BASE_SPRING
ARG VITE_API_BASE_FASTAPI
ARG VITE_API_BASE_RUST
ARG VITE_GOOGLE_CLIENT_ID
ARG VITE_KAKAO_CLIENT_ID
ARG VITE_KAKAO_REDIRECT_URI
ARG VITE_DEV_LOGIN
ENV VITE_API_BASE_SPRING=$VITE_API_BASE_SPRING
ENV VITE_API_BASE_FASTAPI=$VITE_API_BASE_FASTAPI
ENV VITE_API_BASE_RUST=$VITE_API_BASE_RUST
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
ENV VITE_KAKAO_CLIENT_ID=$VITE_KAKAO_CLIENT_ID
ENV VITE_KAKAO_REDIRECT_URI=$VITE_KAKAO_REDIRECT_URI
ENV VITE_DEV_LOGIN=$VITE_DEV_LOGIN
RUN npm run build

# 스테이지 2: nginx alpine에서 정적 파일만 서빙한다 (비루트 사용자 uid=1001).
FROM nginx:alpine AS runtime

RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -S appuser

COPY --from=builder /app/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

# 비루트 사용자로 nginx를 띄울 수 있게 쓰기 경로의 소유자를 바꾼다.
RUN chown -R appuser:appgroup /usr/share/nginx/html && \
    chown -R appuser:appgroup /var/cache/nginx && \
    chown -R appuser:appgroup /var/log/nginx && \
    chown -R appuser:appgroup /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && \
    chown appuser:appgroup /var/run/nginx.pid

USER appuser

# nginx.conf의 listen 포트와 같다.
EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]
