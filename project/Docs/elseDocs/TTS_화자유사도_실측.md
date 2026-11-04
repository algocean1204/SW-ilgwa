# TTS 화자 유사도 실측

측정일 2026-11-03. 재현 스크립트는 `project/benchmarks/tts_similarity/`, 결과는 같은 폴더의 `results/summary.json`(슬라이드별 값 포함)에 있다.

## 1. 요약

| 비교 | 화자 유사도 |
|---|---|
| 원본(Sp1) ↔ 데모 합성 음성 240개 | 평균 **0.847** (중앙 0.849, 하위 5% 0.829, 최소 0.685, 최대 0.878) |
| 원본 ↔ 같은 사람의 다른 녹음 2개 | 0.845, 0.879 |
| 데모 합성 음성 ↔ 다른 사람 음성 2개 | 평균 0.282, 0.258 (최대 0.343) |

- 합성 음성은 원본과 같은 사람이 따로 녹음한 음성끼리와 비슷한 수준으로 닮았고, 다른 사람과는 크게 떨어진다.
- 과목별 평균: python 0.845, rust 0.847, os 0.850.

## 2. 방법

1. 새로 합성하지 않는다. 데모 사이트에 올린 음성(`project/portfolio-showcase/public/audio/{python,rust,os}/ch*/slide_*.mp3`, 240개, 총 457분)과 그 음성을 만들 때 쓴 원본 음성(`project/portfolio-showcase/public/audio/reference/sp1_ref.wav`, 13초)만 쓴다. 데모 음성은 로컬 MLX Qwen3-TTS가 Sp1 참조 음성으로 합성했다(`_showcase_seed/scripts/tts_showcase.py`).
2. 음성마다 ffmpeg로 16kHz mono로 읽고, 화자 인식 모델 ECAPA-TDNN(`speechbrain/spkrec-ecapa-voxceleb`)으로 화자 임베딩(192차원) 1개를 뽑는다. 임베딩은 말한 내용과 상관없이 목소리 특징만 담는다.
3. 두 임베딩의 코사인 유사도를 화자 유사도로 쓴다. 1에 가까울수록 같은 목소리다.
4. 대조군: 같은 사람의 다른 녹음(ref_5, ref_7)과 다른 사람 음성(Sp2, preset_voice2)을 같은 방식으로 비교한다.

## 3. 해석과 한계

- 이 모델에서 같은 사람의 실제 녹음끼리도 0.845~0.879가 나온다. 그래서 예전 기준 0.85는 이 모델에 맞는 기준이 아니고, 대조군과 비교해 판단한다(0.85 이상인 슬라이드는 43.3%).
- 2026-04-20 측정(0.964 · 0.976)은 다른 기준 음성과 기록되지 않은 임베딩 모델로 잰 값이라 이번 값과 직접 비교하지 않는다.
- 데모 음성은 mp3로 압축됐다. 원본 wav로 재면 조금 높게 나올 수 있다.
- 같은 사람 · 다른 사람 분류는 참조 음성끼리의 유사도로 판단했다(ref_5 · ref_7 · preset_voice1은 Sp1과 0.85~0.98, Sp2와 preset_voice2는 서로 0.969).

## 4. 재현

```bash
cd project/benchmarks/tts_similarity
./run.sh
```
