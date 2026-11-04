-- Postgres 초기 확장 설치 스크립트
-- docker-entrypoint-initdb.d에 마운트돼서 최초 컨테이너 기동 시 한 번만 실행된다
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
