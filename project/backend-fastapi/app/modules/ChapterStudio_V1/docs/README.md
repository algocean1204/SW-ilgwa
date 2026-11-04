# docs

## 모듈 목적
ChapterStudio_V1의 설계·검증 기록을 둔다.

## IN·OUT
IN은 검증 명령 결과이며 OUT은 Markdown 기록이다.

## 환경변수
해당 없음.

## 의존성
해당 없음.

## 사용 예시
```bash
find docs -type f -name "*.md"
```

## 주요 문서
- `demo-langgraph-agent.md`: 수동 테스트용 LangGraph 단일 agent 흐름과 timeout 정책을 기록한다.
- `demo-template-catalog.md`: 템플릿 75종 구성(학습 46·visual 9·문항 유형 20), 프론트 색상 토큰, 템플릿별 슬라이드 구성을 기록한다.

## 에러 정책
문서와 구현이 다르면 구현을 기준으로 문서를 고친다.
