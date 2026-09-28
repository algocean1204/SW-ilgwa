# 일과 · ilgwa

AI 학습 과외 서비스는 주제를 넣으면 강의·슬라이드·음성 수업·시험·노트까지 만듭니다.

데모: https://algocean1204.github.io/SW-ilgwa/

로그인 없이 열립니다. 파이썬, Rust, 운영체제 3과목이 슬라이드와 음성 수업, 시험, 노트까지 미리 채워져 있어서 바로 눌러 볼 수 있습니다.

포트폴리오: https://algocean1204.github.io/SW-ilgwa/ptplo/

역할과 선택 이유는 포트폴리오에 있습니다. 이 README는 레포와 파이프라인을 적습니다.

## 시스템 아키텍처

![시스템 아키텍처](docs/architecture.png)

화면은 React SPA, 인증·결제·DB는 Spring, AI는 FastAPI가 맡습니다. 추론은 Modal GPU에서 Qwen을 직접 서빙합니다.

## 서버 구성 - MSA 구조

인증·결제·DB 같은 코어는 Spring, AI는 Python으로 구현하려고 FastAPI를 추가했습니다. 책임을 나누기 위해 서버를 완전 분리했습니다.

- 프론트 — 학습 UI
- Spring — 인증, 결제, DB 접근
- FastAPI — AI 파이프라인 (설계·구현 담당)

## Modal GPU 서빙

Qwen3.6-27B를 Modal GPU B200에 올리고 vLLM으로 직접 서빙합니다. 슬라이드는 병렬로 할당하고 검증과 음성 합성까지 한 강의에 넣었습니다. 클라우드 API는 폴백용입니다.

- 실측 — 15슬라이드 강의 1개(검증·음성 포함) 순차 462s → 병렬 120s
- 폴백 — Modal 실패 시 Gemini 3.5 Flash → Sonnet 4.5 순

## 사용 기술과 선택 근거

양산은 Modal GPU의 Qwen입니다. 클라우드 API는 폴백용으로만 썼습니다.

- LangGraph — OCR 품질게이트·강의 생성·출제 재시도 분기를 StateGraph로 묶습니다
- Qwen3.6-27B — Modal GPU B200 + vLLM. 슬라이드 병렬 할당, 검증·음성 포함
- 폴백 — Modal 실패 시 Gemini 3.5 Flash → Sonnet 4.5 순. API 키는 테스트·폴백용입니다
- OCR — Marker 1차, 품질 게이트 실패 페이지만 MinerU. 버린 경로: PaddleOCR ONNX CER(문자오류률) 48.8%, PaddleOCR-VL 페이지 CER(문자오류률) 75.9%
- Qdrant — OCR 청크를 BGE-M3 dense(1024) + sparse로 저장합니다. 검색은 둘을 병렬로 돌린 뒤 합쳐 그 문단을 강의 생성 컨텍스트로 주입합니다
- Qwen3-TTS — 강의 대본을 voice cloning으로 합성합니다
- Qwen3-ASR — 음성 질문을 인식합니다
- 슬라이드 후처리 — FastAPI에서 하이라이트·iframe sandbox
- 템플릿 — 프레임·난이도는 코드가 고정하고 AI는 `{{빈칸}}`만 채웁니다

## 파이프라인 흐름

### OCR (교재 → 검색 인덱스)

```mermaid
flowchart TD
  pdf[PDF] --> classify[페이지 분류]
  classify --> extract[Marker 추출]
  extract --> gate[품질 게이트]
  gate -->|통과 / 전부 실패| post[후처리]
  gate -->|일부 실패| mineru[MinerU 재추출]
  mineru --> post
  post --> chunk[청킹]
  chunk --> embed[BGE-M3 임베딩]
  embed --> qdrant[Qdrant 저장]
```

### 강의 생성

```mermaid
flowchart TD
  ctx[컨텍스트 준비] --> gen[산출물 생성]
  gen --> slides[슬라이드]
  gen --> quiz[퀴즈]
  gen --> note[노트]
  gen --> hw[과제]
  slides --> tts[Qwen3-TTS voice cloning]
  tts --> audio[음성파일]
```

슬라이드·퀴즈·노트·과제는 동시에 만듭니다. 음성은 슬라이드 대본을 받은 뒤 합성합니다.

### 퀴즈

```mermaid
flowchart TD
  outline[슬라이드 플랜] --> gen[퀴즈 생성]
  gen --> repair[품질 보강]
  repair --> balance[정답 위치 균등]
  balance --> verify[검증]
  verify --> save[저장]
```

### 노트

```mermaid
flowchart TD
  gen[노트 생성] --> md[마크다운 형식으로 포매팅]
  md --> save[저장]
```

### 과제

```mermaid
flowchart TD
  outline[슬라이드 플랜] --> gen[과제 생성]
  gen --> save[저장]
```

채점은 생성 그래프 밖에서 합니다. Spring이 제출을 넘기면 Claude → 실패 시 Gemini, 결과를 콜백합니다.

### 시험 출제

```mermaid
flowchart TD
  src[자료 분석] --> plan[출제 계획]
  plan --> q[문제 생성]
  q --> dist[오답]
  dist --> ans[해설]
  ans --> verify[교차 검증]
  verify -->|통과| out[출력]
  verify -->|실패| repair[교정]
  repair -->|교정 가능| verify
  repair -->|불가| q
```

### 음성

```mermaid
flowchart TD
  slides[슬라이드] --> script[대본]
  script --> tts[Qwen3-TTS voice cloning]
  ref[참조음성] --> tts
  tts --> audio[음성파일]
```

## 성능 (실측)

| 항목 | 값 |
|---|---|
| 초기 콜드 스타트 | ~12.4s |
| 모델 사전 로드 시 | ~0.8s |
| 15슬라이드 1강 (검증·음성 포함) | 순차 462s → 병렬 120s |

## 발표 자료

[발표 PDF 전체 다운로드](docs/ilgwa-presentation.pdf) · 18장 · 16:9

<details open>
<summary><b>발표 슬라이드 전체 보기 (18장)</b></summary>
<img src="docs/slides/slide-01.jpg" width="100%" alt="슬라이드 1">
<img src="docs/slides/slide-02.jpg" width="100%" alt="슬라이드 2">
<img src="docs/slides/slide-03.jpg" width="100%" alt="슬라이드 3">
<img src="docs/slides/slide-04.jpg" width="100%" alt="슬라이드 4">
<img src="docs/slides/slide-05.jpg" width="100%" alt="슬라이드 5">
<img src="docs/slides/slide-06.jpg" width="100%" alt="슬라이드 6">
<img src="docs/slides/slide-07.jpg" width="100%" alt="슬라이드 7">
<img src="docs/slides/slide-08.jpg" width="100%" alt="슬라이드 8">
<img src="docs/slides/slide-09.jpg" width="100%" alt="슬라이드 9">
<img src="docs/slides/slide-10.jpg" width="100%" alt="슬라이드 10">
<img src="docs/slides/slide-11.jpg" width="100%" alt="슬라이드 11">
<img src="docs/slides/slide-12.jpg" width="100%" alt="슬라이드 12">
<img src="docs/slides/slide-13.jpg" width="100%" alt="슬라이드 13">
<img src="docs/slides/slide-14.jpg" width="100%" alt="슬라이드 14">
<img src="docs/slides/slide-15.jpg" width="100%" alt="슬라이드 15">
<img src="docs/slides/slide-16.jpg" width="100%" alt="슬라이드 16">
<img src="docs/slides/slide-17.jpg" width="100%" alt="슬라이드 17">
<img src="docs/slides/slide-18.jpg" width="100%" alt="슬라이드 18">
</details>

## 데모에서 되는 것

위 데모는 정적 박제본입니다. 백엔드 없이 돌아갑니다. AI 생성 기능만 꺼져 있습니다. 3과목의 강의 열람, 음성 수업, 모의고사 응시, 노트, 과제는 미리 만들어 두었습니다. 그대로 해 볼 수 있습니다.
