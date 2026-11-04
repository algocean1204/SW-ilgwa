# 개발 환경 설정

팀원 온보딩 가이드다. 처음 합류한 경우 이 순서대로 진행한다.

---

## 사전 요구사항

| 도구 | 요구 버전 |
|---|---|
| Docker Desktop 또는 OrbStack | 최신 stable |
| Docker Compose | v2 이상 (`docker compose` 명령어) |
| Git | - |

---

## 설정 단계

### 1. 레포 클론

```bash
git clone https://github.com/SW-EZ-Team/infra.git
cd infra
```

### 2. 환경 변수 설정

```bash
cp .env.example .env
```

`.env`를 열어 비밀번호 항목을 채운다. 기본값 그대로 커밋하지 않는다.

### 3. 인프라 기동

```bash
./scripts/dev-up.sh
```

스크립트가 공용 네트워크 `sw-ez-shared`를 없으면 자동으로 생성하고, compose를 detach 모드로 실행한다.

### 4. 서비스 상태 확인

```bash
docker compose ps
```

아래 4개 서비스가 `healthy` 상태여야 한다.

| 컨테이너 | 포트 |
|---|---|
| sw-ez-postgres | 5432 |
| sw-ez-redis | 6379 |
| sw-ez-qdrant | 6333, 6334 |
| sw-ez-flyway | (1회성 실행 후 종료) |

### 5. 마이그레이션 확인

```bash
docker compose logs flyway
```

`Successfully applied N migration(s)` 메시지가 있으면 정상이다.

---

## 백엔드 레포와 연결

각 백엔드 레포의 `docker-compose.yml`에 아래 네트워크 블록을 추가하면 서비스명으로 접속 가능하다.

```yaml
networks:
  sw-ez-shared:
    external: true
```

연결 후 `postgres`, `redis`, `qdrant` 호스트명으로 직접 접속한다. 별도 IP 설정 불필요.

---

## 종료

```bash
# 컨테이너만 종료 (볼륨 보존)
./scripts/dev-down.sh

# 컨테이너 + 볼륨 완전 삭제
docker compose down -v
```

볼륨을 삭제하면 DB 데이터가 전부 사라진다. 로컬 테스트 데이터 초기화 목적 외엔 사용하지 않는다.

---

## 트러블슈팅

#### 포트 충돌

5432, 6379, 6333, 6334 중 하나가 이미 사용 중이면 기동에 실패한다. `lsof -i :<포트>` 또는 `ss -ltnp | grep <포트>`로 점유 프로세스를 확인하고 종료한다.

#### `.env` 파일 없음

`./scripts/dev-up.sh` 실행 전 반드시 `.env`가 존재해야 한다. 없으면 compose가 환경 변수 오류로 종료한다.

#### 네트워크 충돌

`sw-ez-shared` 네트워크가 다른 compose 프로젝트와 충돌하는 경우 `docker network inspect sw-ez-shared`로 기존 연결을 확인한다.
