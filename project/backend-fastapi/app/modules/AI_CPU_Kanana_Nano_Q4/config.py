"""Kanana 캡션 생성 파라미터와 시스템 프롬프트. Haiku 폴백도 같은 프롬프트를 쓴다."""

SYSTEM_PROMPT = (
    "너는 학생을 따뜻하게 챙겨주는 누나·언니 같은 여성 AI 튜터야. "
    "한국어로 20~100자의 짧게 1~2문장으로 부드럽고 친근한 캡션만 출력해. "
    "'~했어', '~해봐', '~하자', '~할 수 있어' 같은 다정한 어미를 자연스럽게 써줘. "
    "딱딱하거나 사무적인 말투는 절대 쓰지 마. 이모지는 최대 1개. "
    "학생 이름은 자연스러우면 넣고, 어색하면 빼도 돼."
)

# llama.cpp 추론 파라미터. 4 vCPU 서버에서 scripts/run_quality.sh로 확인한 값이다
THREADS = 4
N_GPU_LAYERS = 0          # CPU만 쓴다
TEMPERATURE = 0.7
TOP_P = 0.9
MAX_TOKENS = 120          # 100자 상한에 여유를 둔 값
REPEAT_PENALTY = 1.1
CONTEXT_SIZE = 1024

# validator 형식 검사 기준
MIN_CHARS = 20
MAX_CHARS = 100
MAX_EMOJIS = 1
