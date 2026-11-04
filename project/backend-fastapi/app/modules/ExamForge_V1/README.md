# ExamForge_V1 AI 모의고사 생성 모듈

학습 자료로 모의고사를 만드는 라이브러리형 모듈이다.

## 목적

학습 자료를 넣으면 LangGraph 6단계(자료 분석 → 출제 계획 → 문제 생성 → 오답 구성 →
정답 해설 → 교차 검증)와 검증 루프를 거쳐 모의고사를 만든다.
20종 문제 유형(한국어 9, 영어 8, 전문자격 3)을 지원한다. 문제는 Modal Qwen3.6-27B가 만들고
정답은 Claude Sonnet이 교차 검증하며, 난이도는 블룸 분류로 보정한다.

## IN/OUT 인터페이스

### IN (입력)

```python
MockExamRequest(
    source_text: str,       # 학습 자료 원문 (최소 100자)
    subject: str,           # 과목명
    exam_config: ExamConfig(
        total_questions: int,           # 5~200
        time_limit_minutes: int,        # 10~300
        locale: str,                    # "ko" | "en"
        category: str,                  # "korean" | "us" | "professional"
        question_types: list[str],      # 템플릿 ID 목록
        difficulty_distribution: dict,  # {1: 0.2, 2: 0.3, ...}
        passing_score: float,           # 합격 기준 (0~100)
        include_explanations: bool,     # 해설 포함 여부
    )
)
```

### OUT (출력)

```python
MockExamResponse(
    exam_id: str,
    exam_plan: ExamPlan,
    questions: list[Question],
    quality_metrics: QualityMetrics(
        answer_accuracy_rate: float,        # 교차 검증 통과율
        dedup_score: float,                 # 중복 점수 (1.0 = 중복 없음)
        coverage_score: float,              # 주제 커버리지
        distractor_plausibility_score: float, # 오답 그럴듯함
        length_variance_ratio: float,         # 보기 길이 분산 비율
        bloom_distribution_actual: dict,    # 실제 블룸 분포
        retry_count: int,                   # 재시도 횟수
        generation_time_sec: float,         # 총 생성 시간
    ),
    exam_html: str | None,      # 시험지 HTML
    answers_html: str | None,   # 답안지 HTML
)
```

## 환경 변수

| 변수명 | 필수 | 기본값 | 설명 |
|--------|------|--------|------|
| `ANTHROPIC_API_KEY` | 선택 | — | Claude 커넥터 키. `CLAUDE_SONNET_API_KEY`가 있으면 그것을 먼저 쓴다 |
| `EXAMFORGE_ACTIVE_TEXT_MODEL` | 선택 | `qwen27b_modal` | 문제 생성 커넥터. 기본 체인은 Modal Qwen3.6-27B → Claude Sonnet |
| `EXAMFORGE_ACTIVE_PLANNER_MODEL` | 선택 | 문제 생성 커넥터 | 출제 계획 커넥터 |
| `EXAMFORGE_ACTIVE_VERIFIER_MODEL` | 선택 | `claude_sonnet` | 정답 교차 검증과 표적 교정 커넥터 |
| `ACTIVE_GRADING_MODEL` | 선택 | `claude_sonnet` | 채점·총평 커넥터 |
| `EXAMFORGE_GEMINI_FALLBACK` | 선택 | `false` | `true`이고 `GOOGLE_API_KEY`가 있을 때만 Claude Sonnet 뒤에 Gemini 폴백을 붙인다 |
| `EXAMFORGE_ANSWER_KEY_SECRET` | 필수 | — | 정답 키 위변조를 막는 HMAC secret. 32자 이상 무작위 문자열이어야 하며 짧으면 서버 시작 시 바로 실패한다 |
| `EXAMFORGE_TARGETED_REPAIR` | 선택 | `true` | 검증 실패 문항의 표적 교정(fix_instructions 사용). `false`면 blind 재생성으로 간다 |
| `EXAMFORGE_DISTRACTOR_REWRITE` | 선택 | `true` | 오답 선택지 재작성 노드. 코드가 많은 보기로 JSON이 깨지는 환경에서 `false`로 끈다 |
| `MAX_RETRIES` | 선택 | `3` | 교차 검증 실패 문항의 최대 재시도 횟수 (`MOCK_EXAM_MAX_RETRIES`가 우선) |
| `GENERATION_CONCURRENCY` | 선택 | `4` | 문제 생성 병렬 수 |
| `VERIFICATION_CONCURRENCY` | 선택 | `3` | 검증 병렬 수 |
| `MOCK_EXAM_PORT` | 선택 | `8900` | 샌드박스 서버 포트 |

Gemini·OpenAI는 기본 체인에 없다. `EXAMFORGE_ACTIVE_TEXT_MODEL=gemini_flash`나
`EXAMFORGE_GEMINI_FALLBACK=true`처럼 직접 지정할 때만 쓴다.

## 사용 예시

### 라이브러리 호출

```python
from app.modules.ExamForge_V1 import generate_exam_forge, ExamForgeRequest

request = ExamForgeRequest(
    source_text="학습 자료 전문...",
    subject="소프트웨어공학",
)
response = await generate_exam_forge(request)
print(f"생성 문항: {len(response.questions)}개")
print(f"정답 정확률: {response.quality_metrics.answer_accuracy_rate:.0%}")
```

### 샌드박스 서버 실행

```bash
cd project/backend-fastapi
uv venv && uv pip install -r app/modules/ExamForge_V1/requirements.txt
cp app/modules/ExamForge_V1/.env.example app/modules/ExamForge_V1/.env  # API 키·EXAMFORGE_ANSWER_KEY_SECRET 설정
uv run uvicorn app.modules.ExamForge_V1.app.main:app --port 8900
# 브라우저에서 http://localhost:8900 접속
```

## 파이프라인 구조

parse_source(자료 분석) → plan_exam(출제 계획) → generate_questions(문제 생성) →
generate_distractors(오답 구성) → generate_answers(정답 해설) → verify_answers(교차 검증)
6단계 뒤에 검증 루프가 붙는다. 교차 검증에 실패한 문항만 최대 `MAX_RETRIES`(3)회 고치고,
고치지 못한 문항만 다시 생성한다.

```
parse_source → plan_exam → generate_questions → generate_distractors
    → generate_answers → verify_answers
         │
         ├─ [통과] calibrate_difficulty → format_output → END
         │
         └─ [실패] repair_questions
                    │
                    ├─ [교정 성공] verify_answers (재검증)
                    │
                    └─ [교정 실패 / EXAMFORGE_TARGETED_REPAIR=false]
                          generate_questions (blind 재생성, 최대 MAX_RETRIES회)
                               └─ [소진] calibrate_difficulty → format_output → END
```

### 품질 장치

- `<think>` 제거: 모든 LLM 응답에 `common.llm_output.strip_thinking`을 적용해 Qwen 같은 추론 모델의 reasoning 블록이 문항 본문에 들어가지 않게 한다
- few-shot 예시: 문제 생성 프롬프트에 고차원 인지수준 문항 예시를 넣어 암기형 문항이 반복되는 것을 줄인다
- 표적 교정(`repair_questions_node`): 검증 모델(`EXAMFORGE_ACTIVE_VERIFIER_MODEL`)이 낸 `fix_instructions`로 실패 문항의 지적된 부분만 고친다. 교정이 실패하거나 `EXAMFORGE_TARGETED_REPAIR=false`면 blind 재생성으로 간다
- 오답 재작성(`generate_distractors_node`): 같은 과목의 오개념으로 오답지를 만든다. `EXAMFORGE_DISTRACTOR_REWRITE`로 켜고 끈다. 줄바꿈·언어 키워드가 든 코드 보기는 JSON이 깨지기 쉬워 원본을 둔다
- 식별자 고정: 교정·재작성 중에도 `question_id`, `template_id`, `draft_id` 같은 식별자·메타데이터는 바꾸지 않는다. 바꿀 수 있는 필드(`stem`, `options`, `correct_answer`, `explanation` 등)는 화이트리스트로 관리한다
- 해설 오개념 라벨: 해설에 오답별 오개념을 적도록 프롬프트가 요구한다
- 출처 관련성 게이트(`quality/relevance_gate.py`): 출처 발췌·주제 키워드와 겹치는 토큰이 없는 상식형 문항을 규칙으로 떨어뜨려 재시도로 보낸다. 출처 어휘가 적으면(스텁 source_text) 오탐을 피하려고 건너뛴다
- 난이도 발현 게이트(`quality/difficulty_scorer.check_difficulty_manifestation`): 난이도 4~5(분석·평가) 문항이 "~란 무엇인가" 같은 정의 회상형 발문이면 결함으로 본다. 코드가 들어간 발문은 추적 문항이라 검사하지 않는다. 생성 쪽에서는 blueprint의 난이도별 요구사항(비교·추적·엣지 케이스 분석)과 짝을 이룬다
- 정답 위치 균등화(`quality/answer_positions.py`): 보기 수가 같은 객관식끼리 정답 번호를 고르게 다시 배치한다
- 비동기 생성 출처: `/api/exam-forge/mock/generate-async`는 courseId로 `public.chapter`+`public.slide` 본문을 모아 source_text로 쓴다(`/api/mock-exams/generate`와 같은 방식). DB 실패나 본문이 없을 때만 subject/topic 스텁을 쓴다

## 지원 문제 유형

### 한국어 (9종)
- 4지선다, 5지선다, 단답형, 서술형, 논술형, OX형, 빈칸채우기, 순서배열, 연결형

### English (8종)
- Multiple Choice (4/5), True/False, Short Answer, Essay, Fill Blank, Matching, Ordering

### 전문자격 (3종)
- 정보처리기사 필기, 정보처리기사 실기, 자격시험 기본형

## 의존성

- `common/llm_output.py`(backend-fastapi 공통): `strip_thinking`·`extract_json_block`·`loads_lenient`. 모델(Qwen·Claude)이 바뀌어도 출력을 같은 형태로 맞추며, ExamForge 파서는 모두 이것을 먼저 거친다
- `common/ai_bridge.py`: ExamForge 커넥터 팩토리. Modal Qwen 커넥터만 ChapterStudio 구현을 감싸 쓴다
- LangGraph: 6단계 노드와 검증 루프 구성
- Pydantic: 스키마 검증
- FastAPI: 샌드박스 API 서버

## 폴백·에러 정책

- 모델 폴백: 문제 생성·출제 계획은 Modal Qwen3.6-27B → Claude Sonnet, 검증·채점은 Claude Sonnet 단독이다
- AI 응답 파싱 실패: 그 문제를 빈 정답으로 표시하고 검증 단계에서 재생성 대상으로 분류한다
- 표적 교정 실패: blind 재생성으로 간다(최대 `MAX_RETRIES`회)
- 재시도 소진: 지금까지 통과한 문제로 출력하고 품질 메트릭에 남긴다
- `EXAMFORGE_ANSWER_KEY_SECRET`이 없거나 32자 미만: 서버 시작 시 `RuntimeError`로 종료한다

---

## Spring 연결 엔드포인트

### POST /api/mock-exams/generate: 모의고사 생성 트리거

Spring `MockExamService.generateExam()`이 응답을 기다리지 않고 호출하는 진입점이다.
Spring은 `mock_exam`을 `status=GENERATING`으로 저장한 뒤 이 경로를 비동기 호출하고,
이후 `getExamDetail`로 폴링한다(READY 상태에서만 문항 조회).

#### 흐름

```
Spring POST /api/mock-exams/generate (Void)
   → 어댑터 202 즉시 반환
   → BackgroundTasks: public.mock_exam+course 조회 → source_text 조립(chapter/slide)
      → ExamForge generate_exam_forge() 실행
      → public.mock_exam_question INSERT + mock_exam.status='READY', total_points 갱신
```

#### 요청 바디 (Spring 발신)

| 키 | 타입 | 설명 |
|----|------|------|
| `examId` | str | `public.mock_exam.id` (연결 키, `mex_` ULID) |
| `courseId` | str | `public.course.id` (출제 자료 출처) |
| `examType` | str | 시험 유형 (midterm/final/practice 등) |
| `questionCount` | int | 문항 수 (1~50, 5 미만이면 5로 보정) |
| `difficulty` | str | easy/medium/hard → 난이도 분포로 매핑 |
| `focusTopics` | list[str] | 집중 주제 (자료 부족 시 보강에 사용) |
| `timeLimit` | int | 제한 시간(분) |

#### Spring이 읽는 결과 위치

- `public.mock_exam`: `status` GENERATING→READY 전이, `total_points` 합산 저장
- `public.mock_exam_question`: `id(mq_ ULID)`, `question_idx`, `question_text`,
  `question_type='multiple_choice'`, `options`(보기 텍스트 JSON 배열),
  `correct_option`(0-based 인덱스 문자열), `explanation`, `points`(정수)

#### 제약/정책

- ExamForge 객관식(options 보유)만 저장한다. 서술형 등 보기 없는 유형은 Spring 단일 정답 채점 스키마와 비호환이라 제외한다(전부 제외되면 READY 미전이)
- 실패하면 상태를 GENERATING으로 두어 Spring이 미완료로 알게 한다(에러를 숨기지 않는다)
- 같은 `examId` 재요청은 멱등(기존 문항 삭제 후 재삽입)

---

### POST /api/mock-exams/analyze: 채점 결과 AI 총평 생성

Spring `submitExam`이 채점 결과를 보내 동기 호출하면, 채점 커넥터(기본 Claude Sonnet)로 개인별 약점 진단 총평을 만들어 돌려준다.

#### 요청 바디 (Spring 발신)

| 키 | 타입 | 설명 |
|----|------|------|
| `examId` | str | `public.mock_exam.id` |
| `subject` | str | 과목명 |
| `score` | int | 취득 점수 |
| `totalPoints` | int | 만점 |
| `grade` | str | 등급 문자열 (예: "A", "합격") |
| `results` | list | 문항별 채점 결과 (questionIdx, questionText, correct, options, selectedOption, correctOption, explanation) |

#### 응답

```json
{ "analysis": "<한국어 총평 문자열>" }
```

총평은 틀린 보기 텍스트를 복원해 인용하고, 오개념과 다음 학습 연습을 구조화해 제시한다.

응답 JSON에는 `weakTopics`(약점 주제 목록)도 담긴다. 총평 마지막 `약점주제: a | b | c` 줄을 파싱한 값이며 ChapterStudio 약점 집계가 읽는다.

#### 출력 정제

일부 텍스트 모델이 한국어 총평 앞뒤에 자기 사고 과정을 섞어 냈다(예: `Total is around 750 characters`, `**Trimming the Draft:**` 같은 글자수 계산·초안 라벨). 두 단계로 막는다.

1. 프롬프트(`_analysis_prompt.py`): system과 instruction 양쪽에서 사고 과정·글자수 계산·초안 메타·작성 단계 라벨·영어 메타 발화를 금지하고, 약점주제 줄을 반드시 쓰게 한다.
2. 출력 정제(`_analysis_sanitize.sanitize_analysis`): 응답에서 `<think>` 블록과 영문 메타 줄(글자수 계산·초안 라벨·`**...:**` 작성 단계 헤더)을 줄 단위 규칙으로 지운다. 한국어가 한 글자라도 있는 줄과 `약점주제:` 줄은 남겨 본문이 사라지지 않게 한다. 정제한 뒤 `split_weak_topics`로 본문과 약점주제를 나눈다.

#### 폴백 정책

- 커넥터 호출이 실패하거나 응답이 비면 200과 안내문을 돌려준다. Spring 흐름을 끊지 않으려는 것이며 실패 사유는 로그에 남긴다.
- 정제 후 본문이 비면(메타만 나온 경우) 대체 문구(`총평 본문을 생성하지 못했어요. ...`)를 쓴다.

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-06-13 | 총평 사고 과정 노출 장애 대응으로 출력 정제 추가 |
