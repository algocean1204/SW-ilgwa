# 서비스 간 API 계약 (OpenAPI)

SW-EZ-Team 서비스 간에 공유할 OpenAPI 스펙 파일을 두는 디렉토리다.
지금은 비어 있으며 스펙 파일은 아직 추가하지 않았다.

---

## 예정 구조

```
contracts/
├── spring-api.yaml         # backend-spring REST API 스펙
├── rust-api.yaml           # (deprecated) 과거 backend-rust gRPC·REST API 스펙. lib-rust는 PyO3 라이브러리라 네트워크 API가 없다
└── fastapi-api.yaml        # backend-fastapi AI 파이프라인 API 스펙
```

---

## 사용 목적

- 프론트엔드(frontend-web)가 백엔드 API 스펙을 기반으로 타입을 자동 생성한다.
- 서비스 간 호출 인터페이스를 명시해 breaking change를 막는다.
- API 변경 시 이 저장소에 PR을 먼저 올려 계약 변경을 공지한다.
