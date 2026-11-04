"""오디오 입출력 유틸.

업로드 바이트를 지정 샘플레이트의 mono float32 numpy 로 바꾸거나,
mono float32 numpy 를 16-bit PCM WAV 바이트로 인코딩한다.
커넥터마다 librosa/scipy 를 따로 부르지 않게 여기 모았다.
"""
from __future__ import annotations

import tempfile
from io import BytesIO
from pathlib import Path

import librosa
import numpy as np
import soundfile as sf
from scipy.io import wavfile


def load_audio_from_bytes(
    data: bytes, target_sr: int = 16000
) -> tuple[np.ndarray, int]:
    """bytes 형태 오디오를 float32 numpy + 샘플레이트로 반환한다.

    빠른 경로: librosa.load(BytesIO). libsndfile 이 wav/flac/ogg 를 바로 읽는다.
    느린 경로: 브라우저 MediaRecorder 의 webm/opus 는 libsndfile 이 못 읽어 LibsndfileError 가 난다.
    그때는 임시 파일로 써서 librosa 의 audioread(ffmpeg) 경로로 다시 읽는다.
    ffmpeg 이 설치돼 있어야 한다(Homebrew: `brew install ffmpeg`).
    """
    try:
        # 빠른 경로: libsndfile 이 BytesIO 를 바로 읽는다(wav/flac/ogg-vorbis)
        y, sr = librosa.load(BytesIO(data), sr=target_sr, mono=True)
    except sf.LibsndfileError:
        # 느린 경로: webm/opus/m4a 는 임시 파일로 넘겨야 audioread(ffmpeg)가 읽는다
        y, sr = _load_via_tempfile(data, target_sr)
    # librosa 버전에 따라 dtype 이 다를 수 있어 float32 로 맞춘다
    return y.astype(np.float32, copy=False), sr


def _load_via_tempfile(data: bytes, target_sr: int) -> tuple[np.ndarray, int]:
    """디스크에 쓴 뒤 librosa.load(path) 로 읽어 audioread(ffmpeg) 경로를 탄다.

    확장자를 .audio 로 두어 ffmpeg 이 컨테이너 헤더로 포맷을 판별하게 한다.
    .webm 으로 고정하면 실제 포맷이 m4a/opus 일 때 일부 ffmpeg 빌드가 경고를 낸다.
    임시 파일은 with 블록이 끝나면 지워진다.
    """
    with tempfile.NamedTemporaryFile(suffix=".audio", delete=True) as tmp:
        tmp.write(data)
        tmp.flush()
        return librosa.load(Path(tmp.name), sr=target_sr, mono=True)


def ensure_mono(y: np.ndarray) -> np.ndarray:
    """2D(채널×샘플)로 들어오면 평균 내서 mono 1D로 변환."""
    # librosa.load(..., mono=True) 결과는 이미 1D 지만 np.ndarray 를 직접 받는 경우가 있다
    if y.ndim == 1:
        return y
    if y.ndim == 2:
        return y.mean(axis=0).astype(np.float32, copy=False)
    # 3D 이상은 다루지 않는다
    raise ValueError(f"unsupported ndim={y.ndim} for mono conversion")


def duration_sec(y: np.ndarray, sample_rate: int) -> float:
    """샘플 수 / sr 로 길이(초)를 구한다."""
    if sample_rate <= 0:
        raise ValueError("sample_rate must be positive")
    return float(y.shape[-1]) / float(sample_rate)


def encode_wav_bytes(
    y: np.ndarray, sample_rate: int, bit_depth: int = 16
) -> bytes:
    """float32 mono 배열을 16-bit PCM WAV 바이트로 인코딩한다.

    브라우저 <audio> 태그에서 재생되게 16-bit PCM 으로 고정한다.
    [-1, 1] 범위로 자른 뒤 int16 으로 바꾼다.
    """
    if sample_rate <= 0:
        raise ValueError("sample_rate must be positive")
    if bit_depth != 16:
        # 16-bit 만 지원한다. 다른 포맷이 필요하면 soundfile.write 를 쓴다
        raise ValueError(f"unsupported bit_depth={bit_depth}, only 16 supported")
    mono = ensure_mono(y)
    # MossFormer2 출력이 가끔 ±1 을 넘어서 int16 오버플로가 나지 않게 자른다
    clipped = np.clip(mono, -1.0, 1.0).astype(np.float32, copy=False)
    # 32767 을 곱해 int16 으로 바꾼다
    pcm16 = (clipped * 32767.0).astype(np.int16)
    buf = BytesIO()
    wavfile.write(buf, sample_rate, pcm16)
    return buf.getvalue()
