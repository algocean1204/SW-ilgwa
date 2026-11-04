# portfolio-showcase

`frontend-web`을 바탕으로 만든 정적 포트폴리오 데모다. MSW가 `/api/*` 요청을 가로채므로 백엔드 없이 동작한다.

## 로컬 실행

```bash
npm i
npm run generate:mock-data   # artifacts → showcase-store.json (최초·데이터 갱신 시)
npm run build
npm run preview              # http://127.0.0.1:4174
```

개발 모드: `npm run dev` (`.env`에 `VITE_MOCK=1` 필요)

## 데모 데이터

- 소스: `_showcase_seed/artifacts/{python,rust,os}/` (저장소에는 포함하지 않는다)
- 변환: `scripts/generate_mock_data.py` (`inject_showcase.py` 슬라이드 렌더 재사용)
- 저장: `src/mocks/data/showcase-store.json`

## 환경 변수

| 변수 | 값 | 설명 |
|---|---|---|
| `VITE_MOCK` | `1` | MSW 활성화 |
| `VITE_DEV_LOGIN` | `true` | dev-login 버튼 노출 |
