# 공통 Rust 서버 템플릿. cargo-chef로 의존성 캐시를 분리한다
#
# 주의: `lib-rust`는 PyO3 라이브러리(wheel)라서
# 이 Dockerfile로 빌드하지 않는다. 별도 Rust 서버가 생길 때 쓰는
# 공통 템플릿이다. <BIN_NAME> 자리를 실제 바이너리 이름으로 바꿔 쓴다.
FROM rust:1-slim-bookworm AS chef
RUN cargo install cargo-chef --locked
WORKDIR /app

FROM chef AS planner
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo chef prepare --recipe-path recipe.json

FROM chef AS builder
COPY --from=planner /app/recipe.json recipe.json
RUN cargo chef cook --release --recipe-path recipe.json
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo build --release

FROM debian:bookworm-slim AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates libssl3 && rm -rf /var/lib/apt/lists/*
RUN useradd -r -u 1001 rustuser
WORKDIR /app
COPY --from=builder /app/target/release/<BIN_NAME> /app/<BIN_NAME>
USER rustuser
EXPOSE 8081
ENTRYPOINT ["/app/<BIN_NAME>"]
