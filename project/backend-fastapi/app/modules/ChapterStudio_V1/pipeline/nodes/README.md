# pipeline/nodes

## 모듈 목적
LangGraph 노드를 노드당 한 파일로 둔다.

## IN·OUT
IN은 `ChapterStudioState` 일부 필드이며 OUT은 갱신할 State 조각이다.

## 환경변수
해당 없음. 커넥터 설정은 `common.config`와 `ai_connectors`에 있다.

## 의존성
`pipeline` 하위 모듈, `ai_connectors`, `common.config`, `postprocess.pipeline`에 의존한다.

## 사용 예시
```python
from pipeline.state import ChapterStudioState
```

## 에러 정책
검증·교정·보강 같은 품질 패스가 실패하면 원본을 유지한다. 개수·인덱스 계약 위반은 `ConversionError`로 올린다.
