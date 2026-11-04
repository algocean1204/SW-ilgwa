# 공통 Python 템플릿. uv로 의존성을 설치하고 slim-bookworm 런타임을 쓴다
FROM python:3.12-slim-bookworm AS deps
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv
WORKDIR /app
ENV UV_LINK_MODE=copy
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

FROM python:3.12-slim-bookworm AS runtime
RUN useradd -r -u 1001 appuser
WORKDIR /app
COPY --from=deps /app/.venv /app/.venv
COPY . /app
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1
USER appuser
EXPOSE 8082
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8082"]
