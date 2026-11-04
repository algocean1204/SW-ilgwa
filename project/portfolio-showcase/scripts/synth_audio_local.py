#!/usr/bin/env python3
"""포트폴리오 데모용. 파이썬 ch1 슬라이드 narration → 로컬 MLX Qwen3-TTS 합성 → public/ 번들.

x_vector_only 모드(ref_text="")로 ref 오염 없이 합성한다.
합성 후 mlx-whisper로 1~2개 표본 검증(ref 오염 0 + 내레이션 키워드 일치).

사용법 (backend-fastapi venv):
  cd portfolio-showcase
  ../backend-fastapi/.venv/bin/python scripts/synth_audio_local.py
  ../backend-fastapi/.venv/bin/python scripts/synth_audio_local.py --force
  ../backend-fastapi/.venv/bin/python scripts/synth_audio_local.py --limit 1
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import time
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
PORTFOLIO_ROOT = SCRIPT_DIR.parent
PROJECT_ROOT = PORTFOLIO_ROOT.parent
SHOWCASE_SEED = PROJECT_ROOT / "_showcase_seed"
TTS_SCRIPT = SHOWCASE_SEED / "scripts" / "tts_showcase.py"
CHAPTER_JSON = SHOWCASE_SEED / "artifacts" / "python" / "chapter_01.json"
OUT_DIR = PORTFOLIO_ROOT / "public" / "audio" / "python" / "ch1"

WHISPER_MODEL = "mlx-community/whisper-large-v3-turbo"
REF_CONTAMINATION = ("천천히 하셔도 되니까", "잘하고 계십니다", "편하게 하세요")
VALIDATE_SLIDES = (0, 3)  # 1~2개 표본


def _load_tts_module() -> Any:
    if str(SHOWCASE_SEED / "scripts") not in sys.path:
        sys.path.insert(0, str(SHOWCASE_SEED / "scripts"))
    spec = importlib.util.spec_from_file_location("tts_showcase", TTS_SCRIPT)
    if spec is None or spec.loader is None:
        raise ImportError(f"tts_showcase 로드 실패: {TTS_SCRIPT}")
    mod = importlib.util.module_from_spec(spec)
    sys.modules["tts_showcase"] = mod
    spec.loader.exec_module(mod)
    return mod


def _load_chapter_slides() -> list[dict[str, Any]]:
    with CHAPTER_JSON.open(encoding="utf-8") as fh:
        chapter = json.load(fh)
    slides = chapter.get("slides", [])
    if len(slides) < 8:
        raise RuntimeError(f"chapter_01.json 슬라이드 부족: {len(slides)}")
    return slides[:8]


def _narration_keywords(narration: str, n: int = 6) -> list[str]:
    return [w for w in narration.replace(".", "").replace(",", "").split()[:n] if len(w) > 1]


def _whisper_transcribe(wav_path: Path) -> str:
    import mlx_whisper

    result = mlx_whisper.transcribe(
        str(wav_path),
        path_or_hf_repo=WHISPER_MODEL,
        language="ko",
    )
    return str(result.get("text", "")).strip()


def _validate_sample(
    wav_path: Path,
    narration: str,
    slide_idx: int,
) -> None:
    txt = _whisper_transcribe(wav_path)
    contaminated = any(phrase in txt for phrase in REF_CONTAMINATION)
    keywords = _narration_keywords(narration)
    hits = sum(1 for w in keywords if w in txt)
    ok = (not contaminated) and hits >= 2

    status = "OK" if ok else "FAIL"
    print(
        f"[whisper] slide{slide_idx} {status} "
        f"contaminated={contaminated} keywords={hits}/{len(keywords)}",
        flush=True,
    )
    print(f"  transcript: {txt[:120]}", flush=True)

    if not ok:
        raise RuntimeError(
            f"slide{slide_idx} whisper 검증 실패 "
            f"(contaminated={contaminated}, keywords={hits}/{len(keywords)})"
        )


def run(args: argparse.Namespace) -> None:
    tts = _load_tts_module()
    slides = _load_chapter_slides()
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    ref_bytes, _ref_text = tts._load_ref_assets()
    tts._setup_backend_path()

    from app.modules.TTS_V2.connectors.tts_engine import TTSEngineSession

    targets = slides[: args.limit] if args.limit else slides
    done = 0
    skipped = 0

    print(
        f"[synth] python ch1 slides 0..{len(targets) - 1} "
        f"mode=x_vector_only out={OUT_DIR}",
        flush=True,
    )

    with TTSEngineSession(
        ref_audio_bytes=ref_bytes,
        ref_text="",  # x_vector_only 모드. 참조 음성 대사가 섞이지 않게 한다
        ref_sr=tts.TTS_SAMPLE_RATE,
        language="ko",
    ) as session:
        for slide in targets:
            slide_idx = int(slide["slide_idx"])
            out_path = OUT_DIR / f"slide_{slide_idx}.wav"

            if out_path.exists() and not args.force:
                skipped += 1
                print(f"[synth] skip slide{slide_idx} (exists)", flush=True)
                continue

            narration = str(slide.get("narration", "")).strip()
            if not narration:
                raise RuntimeError(f"slide{slide_idx} narration 비어 있음")

            t0 = time.time()
            wav_bytes, dur = tts.synthesize_slide_wav(session, narration)
            out_path.write_bytes(wav_bytes)
            done += 1
            mem = tts._mlx_trim()
            print(
                f"[synth] {done}/{len(targets)} slide{slide_idx} "
                f"{dur:.1f}s {out_path.name} {mem} "
                f"elapsed={time.time() - t0:.0f}s",
                flush=True,
            )

    # 합성했거나 이미 있는 파일을 whisper로 검증한다
    print("[synth] whisper 검증 시작", flush=True)
    for slide_idx in VALIDATE_SLIDES:
        if args.limit and slide_idx >= args.limit:
            continue
        wav_path = OUT_DIR / f"slide_{slide_idx}.wav"
        if not wav_path.exists():
            raise RuntimeError(f"검증 대상 없음: {wav_path}")
        narration = str(slides[slide_idx].get("narration", ""))
        _validate_sample(wav_path, narration, slide_idx)

    print(f"[synth] 완료 합성={done} 스킵={skipped}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="포트폴리오 파이썬 ch1 로컬 TTS 합성")
    parser.add_argument("--force", action="store_true", help="기존 WAV 덮어쓰기")
    parser.add_argument("--limit", type=int, default=None, metavar="N", help="테스트용 N슬라이드만")
    args = parser.parse_args()
    if args.limit is not None and args.limit <= 0:
        raise SystemExit("--limit는 양의 정수여야 합니다.")

    t0 = time.time()
    run(args)
    print(f"[synth] total elapsed {time.time() - t0:.1f}s", flush=True)


if __name__ == "__main__":
    main()