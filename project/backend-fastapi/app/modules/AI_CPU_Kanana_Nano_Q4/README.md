# AI_CPU_Kanana_Nano_Q4

텔레그램 과제 알림 캡션을 CPU에서 만드는 모듈이다.
모델은 Kanana Nano 2.1B를 친구 대화 약 22만 줄로 LoRA SFT한 [aisamdasu/algocean-2.1B-nano-friend](https://huggingface.co/aisamdasu/algocean-2.1B-nano-friend)를 Q4_K_M GGUF(약 1.3GB)로 양자화해 llama.cpp로 서빙한다. GPU는 쓰지 않는다.

---

## 공개 API

```python
from app.modules.AI_CPU_Kanana_Nano_Q4 import generate_caption, CaptionResult
```

외부에서는 이 두 심볼만 쓴다. 내부 파일(`caption.py`, `model_loader.py` 등)은 직접 import하지 않는다.

### 사용 예

```python
from app.modules.AI_CPU_Kanana_Nano_Q4 import generate_caption, CaptionResult

result = generate_caption(
    student_name="김민준",
    assignment_name="파이썬 리스트 컴프리헨션",
    weakness=None,
    deadline="내일 23:59",
)
print(result.text)        # "민준아, 오늘 리스트 컴프리헨션 같이 해보자! 내일 23:59까지야, 할 수 있어!"
print(result.source)      # "kanana", "haiku", "fallback" 중 하나
print(result.char_count)  # 글자수 정수
```

---

## IN / OUT

### 입력 (`generate_caption` 인자)

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `student_name` | `str` | 예 | 학생 이름 |
| `assignment_name` | `str` | 예 | 과제 이름 |
| `weakness` | `str \| None` | - | 약점 키워드 (없으면 생략) |
| `difficulty` | `str \| None` | - | 난이도 표현 (없으면 생략) |
| `deadline` | `str \| None` | - | 마감 표현 (없으면 생략) |

### 출력 (`CaptionResult` 필드)

| 필드 | 타입 | 설명 |
|---|---|---|
| `text` | `str` | 20~100자 캡션 본문 |
| `source` | `"kanana" \| "haiku" \| "fallback"` | 생성 경로. Kanana 통과 시 `"kanana"`, Haiku 폴백 시 `"haiku"`, 템플릿 폴백 시 `"fallback"` |
| `char_count` | `int` | `text` 글자수 |

---

## 출력 제약

길이 20~100자, 1~2문장, 이모지 최대 1개. 학생 이름은 자연스러우면 넣고 어색하면 뺀다. 말투 정책은 `config.py`의 `SYSTEM_PROMPT`에 고정돼 있고 외부에 공개하지 않는다. `MIN_CHARS` / `MAX_CHARS` / `MAX_EMOJIS` 상수로 코드에서 검사하며, 임의로 바꾸지 않는다.

---

## 폴백 흐름

```
generate_caption() 호출
       |
       v
Kanana 추론 (_run_kanana)
       |
       v
validator.sanitize() 형식 검사
  - 20자 미만 → None
  - 100자 초과 → None
  - 이모지 2개 이상 → None
       |
    통과? ── YES ──> source="kanana"
       | NO
       v
Claude Haiku 추론 (_run_haiku, 모델 claude-haiku-4-5)
       |
       v
validator.sanitize() 형식 검사
       |
    통과? ── YES ──> source="haiku"
       | NO
       v
Jinja2 템플릿 렌더링 (fallback.py) ──> source="fallback"
```

Haiku는 `CAPTION_BACKEND`가 `auto`(기본) 또는 `haiku`이고 `ANTHROPIC_API_KEY`가 있을 때만 부른다.
Jinja2 템플릿은 항상 100자 이내다. Kanana나 Haiku 호출이 예외를 던져도 다음 단계로 넘어간다.

---

## 파라미터 (config.py 기준)

| 상수 | 값 | 설명 |
|---|---|---|
| `THREADS` | `4` | llama.cpp 스레드 수 (4 vCPU 서버 기준) |
| `N_GPU_LAYERS` | `0` | GPU 레이어 0. CPU만 쓴다 |
| `TEMPERATURE` | `0.7` | 샘플링 온도 |
| `TOP_P` | `0.9` | Top-p 샘플링 |
| `MAX_TOKENS` | `120` | 최대 생성 토큰 (100자 여유분) |
| `REPEAT_PENALTY` | `1.1` | 반복 억제 |
| `CONTEXT_SIZE` | `1024` | 컨텍스트 윈도우 크기 |

---

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `KANANA_MODEL_PATH` | `app/modules/AI_CPU_Kanana_Nano_Q4/models/kanana-nano-2.1b-instruct.Q4_K_M.gguf` | LoRA SFT 모델 Q4 GGUF 파일 절대 경로. 운영 환경에서는 볼륨 마운트 경로로 덮어쓴다. |
| `KANANA_GGUF_REPO` | `aisamdasu/algocean-2.1B-nano-friend` | `scripts/setup.sh`가 GGUF를 받을 HuggingFace 저장소 |
| `KANANA_GGUF_FILE` | `kanana-nano-2.1b-instruct.Q4_K_M.gguf` | `scripts/setup.sh`가 받을 GGUF 파일명 |
| `CAPTION_BACKEND` | `auto` | `auto`/`haiku`면 Kanana가 형식 검사에 걸릴 때 Haiku로 폴백한다. 그 외 값이면 Haiku를 건너뛴다 |
| `ANTHROPIC_API_KEY` | 없음 | Haiku 폴백용 키. 없으면 Haiku를 건너뛴다 |

모델 파일은 git에 넣지 않는다. `.gitignore`에 `models/*.gguf`가 등록돼 있다.

---

## 모델 다운로드

`scripts/setup.sh`는 llama.cpp를 설치하고 GGUF를 내려받는다.
`aisamdasu/algocean-2.1B-nano-friend` 저장소에는 지금 LoRA 어댑터만 있고 GGUF가 없다. 병합·Q4 양자화한 GGUF를 올린 저장소와 파일명을 `KANANA_GGUF_REPO`, `KANANA_GGUF_FILE`로 지정해서 실행한다.

```bash
cd app/modules/AI_CPU_Kanana_Nano_Q4
bash scripts/setup.sh
```

끝나면 `models/` 아래에 `KANANA_GGUF_FILE` 이름의 파일이 생긴다.

---

## 학습 설정

모델: [huggingface.co/aisamdasu/algocean-2.1B-nano-friend](https://huggingface.co/aisamdasu/algocean-2.1B-nano-friend)

| 항목 | 값 |
|---|---|
| 기본 모델 | Kanana Nano 2.1B |
| 데이터 | 친구 대화 약 22만 줄 |
| 방식 | LoRA SFT (r=128, alpha=256, q/k/v/o + MLP) |
| 학습 환경 | Modal H100 |
| 평가 주기 | 200 step마다 eval loss 측정 |
| 채택 체크포인트 | 6600 step. eval loss가 1.285 → 1.319로 반등하기 직전 |
| eval loss | 1.640 → 1.241 (−24.3%) |

---

## 실측 품질

4 vCPU 서버에서 `scripts/run_quality.sh`로 잰 결과다.

| 항목 | 수치 |
|---|---|
| 생성 속도 | 22.3 tok/s (GPU 0%) |
| 같은 질문 응답 | 5.38초·150토큰 → 1.32초·39토큰 (기본 모델 대비 LoRA SFT) |
| eval loss | 1.640 → 1.241 (−24.3%, 6600 step 채택) |
| 100자 이내 비율 | 15샘플 중 13개 (86.7%) |
| 할루시네이션 | 0건 |

100자를 넘은 2건은 `validator.sanitize()`가 걸러 다음 폴백(Haiku)으로 넘긴다.

---

## 테스트 실행

```bash
pytest app/modules/AI_CPU_Kanana_Nano_Q4/tests/
```

| 테스트 파일 | 검증 내용 |
|---|---|
| `test_config.py` | `SYSTEM_PROMPT` 내용 일치, `MAX_CHARS == 100`, `THREADS == 4` 등 출력 제약 회귀 확인 |
| `test_validator.py` | 10자 미만·100자 초과·이모지 2개 → `None` 반환, 정상 캡션 통과 |
| `test_caption_order.py` | Kanana → Haiku → Jinja2 폴백 순서와 Haiku 사용 조건 |
| `test_fallback.py` | 이름/약점/마감 조합별 Jinja2 출력이 100자 이내인지 확인 |

---

## 주의

#### Celery prefork 메모리

모델은 워커당 1회 로드하고 워커마다 약 1.3GB를 쓴다. 16GB 서버 기준 워커를 2~3개로 제한한다. Celery `--concurrency` 값을 확인한다.

---

## 명명 근거

폴더명 `AI_CPU_Kanana_Nano_Q4`는 대문자와 숫자가 섞여 PEP 8과 맞지 않는다. Python 식별자로는 유효해 import는 된다. 팀이 정한 이름이라 바꾸지 않는다.
