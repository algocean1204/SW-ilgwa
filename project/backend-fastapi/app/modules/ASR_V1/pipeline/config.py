"""ASR_V1 파이프라인 파라미터.

모든 값은 ASR_V1_* 환경변수로 덮어쓸 수 있다.
"""
from __future__ import annotations

import os

# VAD(음성 활동 감지) 파라미터
VAD_MIN_SILENCE_MS: int = int(os.getenv("ASR_V1_VAD_MIN_SILENCE_MS", "800"))
VAD_SPEECH_PAD_MS: int = int(os.getenv("ASR_V1_VAD_SPEECH_PAD_MS", "200"))
VAD_MIN_SPEECH_MS: int = int(os.getenv("ASR_V1_VAD_MIN_SPEECH_MS", "250"))
VAD_THRESHOLD: float = float(os.getenv("ASR_V1_VAD_THRESHOLD", "0.5"))

# 턴 커밋 파라미터
# 턴 커밋에 필요한 최소 글자수
TURN_MIN_CHARS: int = int(os.getenv("ASR_V1_TURN_MIN_CHARS", "2"))
# 한국어 어미가 잘리지 않게 마지막 음성 뒤 더 기다리는 시간
TURN_GRACE_MS: int = int(os.getenv("ASR_V1_TURN_GRACE_MS", "300"))

# Partial 전사 파라미터
# 부분 전사 업데이트 주기 (UI 전용, 확정에 영향 없음)
PARTIAL_INTERVAL_MS: int = int(os.getenv("ASR_V1_PARTIAL_INTERVAL_MS", "500"))

# 플러시 정책 파라미터
# 2차 트리거 최소 버퍼 길이
FLUSH_SECONDARY_MIN: int = int(os.getenv("ASR_V1_FLUSH_SECONDARY_MIN", "30"))
# 2차 트리거 쉼표 스캔 범위(끝에서 몇 글자)
FLUSH_SECONDARY_SCAN: int = int(os.getenv("ASR_V1_FLUSH_SECONDARY_SCAN", "10"))

# 서버 파라미터
PORT: int = int(os.getenv("ASR_V1_PORT", "8011"))
SAMPLE_RATE: int = int(os.getenv("ASR_V1_SAMPLE_RATE", "16000"))
# 기본은 Modal GPU Whisper large-v3-turbo(ai_connectors 의 whisper-modal)
ASR_MODEL: str = os.getenv("ASR_V1_MODEL", "whisper-modal")

# 버퍼 상한
# 버퍼 최대 누적 시간(초). 여기에 닿으면 바로 커밋한다
BUFFER_MAX_SEC: float = float(os.getenv("ASR_V1_BUFFER_MAX_SEC", "20.0"))
