# SW-EZ-Team backend-fastapi 서비스 컨테이너
# uv로 의존성을 설치한 뒤 slim 런타임 이미지로 옮긴다
# api / celery worker / celery beat 가 이 이미지를 같이 쓴다

# Stage 1: uv로 의존성 설치
FROM python:3.12-slim-bookworm AS deps

# uv 공식 이미지에서 바이너리만 복사한다
COPY --from=ghcr.io/astral-sh/uv:0.11.6 /uv /usr/local/bin/uv

WORKDIR /app

# uv 캐시와 venv 가 다른 파일시스템이라 하드링크 대신 copy 로 설치한다
ENV UV_LINK_MODE=copy

# 의존성 명세만 먼저 복사해 레이어 캐시를 쓴다
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

# Stage 2: 런타임
FROM python:3.12-slim-bookworm AS runtime

# 네트워크/SSL 과 오디오 처리에 필요한 최소 런타임 라이브러리
# libsndfile1: soundfile/librosa 가 쓰는 네이티브 공유 라이브러리
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

# root 가 아닌 전용 사용자로 실행한다
RUN useradd -r -u 1001 appuser

WORKDIR /app

# deps 스테이지에서 만든 venv 를 복사한다
COPY --from=deps /app/.venv /app/.venv

COPY . /app

RUN chown -R appuser:appuser /app \
    && chmod +x /app/docker-entrypoint.sh

# venv 의 bin 을 PATH 에 넣고 파이썬 출력 버퍼링과 .pyc 생성을 끈다
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

USER appuser

EXPOSE 8000

ENTRYPOINT ["/app/docker-entrypoint.sh"]

# 기본 커맨드는 api 서버다. compose 가 worker/beat 용으로 바꾼다
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
