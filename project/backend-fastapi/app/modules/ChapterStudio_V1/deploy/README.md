# deploy

## 모듈 목적
Modal 배포 스크립트를 둔다. `modal_app.py`는 B200에서 Qwen3.6-27B FP8 vLLM 서버를, `modal_kanana_app.py`는 Kanana2 교정 서버를 띄운다.

## IN·OUT
IN은 Modal secret과 모델 설정이며 OUT은 Modal endpoint다.

## 환경변수
`MODAL_TOKEN_ID`, `MODAL_TOKEN_SECRET`로 배포한다. `CHAPTERSTUDIO_MODAL_*` 값으로 앱 이름·모델·컨텍스트 길이·scaledown(기본 30초)을 바꿀 수 있다.

## 의존성
`modal`에 의존한다.

## 사용 예시
```bash
# 가중치를 HF 캐시 Volume에 미리 받아 둔다(최초 1회)
modal run deploy/modal_app.py::download_model
modal deploy deploy/modal_app.py
```

`Qwen27BServer`는 메모리·GPU 스냅샷을 켠다. 첫 기동 때 vLLM을 띄워 가중치를 올리고 sleep 상태로 스냅샷을 뜬 뒤, 다음 콜드 스타트부터는 스냅샷을 복원하고 `wake_up`만 한다.

## 에러 정책
배포 실패는 Modal CLI 오류로 드러난다. 런타임 연결 실패는 커넥터가 `TimeoutError`·`AuthError`로 바꿔 올리고, 폴백 래퍼가 Claude로 넘긴다.
