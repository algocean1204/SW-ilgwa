// 슬라이드 TTS 음성을 HTMLAudioElement로 재생하는 하단 컨트롤 바
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import type { Palette } from '@/lib';
import { env } from '@/core/env';
import Icon from '@/Icon';
import { BackIcon, ForwardIcon, PauseIcon } from './AudioPlayerIcons';
import { AudioSeekBar } from './AudioSeekBar';

type SpeedOption = 1 | 1.2 | 1.5;
const SPEED_OPTIONS: SpeedOption[] = [1, 1.2, 1.5];

interface AudioPlayerBarProps {
  palette: Palette;
  audioUrl: string | null;
  rightInset?: number;
  totalSeconds?: number;
  autoPlayToken?: number;
  onEnded?: () => void;
  onPlayingChange?: (playing: boolean) => void;
}
export function AudioPlayerBar({
  palette,
  audioUrl,
  rightInset = 32,
  totalSeconds = 0,
  autoPlayToken = 0,
  onEnded,
  onPlayingChange,
}: AudioPlayerBarProps) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const activeSpeedRef = useRef<SpeedOption>(1);
  const [playing, setPlaying] = useState(false);
  const [currentSeconds, setCurrentSeconds] = useState(0);
  const [durationSeconds, setDurationSeconds] = useState(totalSeconds);
  const [activeSpeed, setActiveSpeed] = useState<SpeedOption>(1);
  const resolvedAudioUrl = useMemo(() => resolveAudioUrl(audioUrl), [audioUrl]);
  const disabled = resolvedAudioUrl == null;
  const effectiveTotalSeconds = durationSeconds > 0 ? durationSeconds : Math.max(0, totalSeconds);
  const isAtStart = disabled || currentSeconds <= 0;
  const isAtEnd = disabled || effectiveTotalSeconds <= 0 || currentSeconds >= effectiveTotalSeconds;

  const notifyPlaying = useCallback((next: boolean) => {
    setPlaying(next);
    onPlayingChange?.(next);
  }, [onPlayingChange]);

  const startPlayback = useCallback(async (audio: HTMLAudioElement) => {
    try {
      await audio.play();
    } catch {
      notifyPlaying(false);
    }
  }, [notifyPlaying]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    // pause()가 onPause를 동기로 발생시켜 notifyPlaying(false)를 이미 부른다.
    // 상태 초기화(setCurrentSeconds, setDurationSeconds)는 React 18 자동 배칭으로
    // 한 렌더에 묶이므로 마이크로태스크로 미룰 필요가 없다.
    audio.pause();
    setCurrentSeconds(0);
    setDurationSeconds(Math.max(0, totalSeconds));

    if (!resolvedAudioUrl) {
      audio.removeAttribute('src');
      audio.load();
      // src가 없어도 load() 뒤 배속을 다시 적용해 playbackRate 리셋을 덮는다
      audio.playbackRate = activeSpeedRef.current;
      return;
    }

    audio.src = resolvedAudioUrl;
    audio.load();
    // load()가 playbackRate를 리셋하므로 그 뒤에 배속을 다시 적용한다
    audio.playbackRate = activeSpeedRef.current;
    if (autoPlayToken > 0) void startPlayback(audio);
  }, [autoPlayToken, resolvedAudioUrl, startPlayback, totalSeconds]);

  function handlePlayPause() {
    const audio = audioRef.current;
    if (!audio || disabled) return;
    if (audio.paused) void startPlayback(audio);
    else audio.pause();
  }

  function handleSeek(seconds: number) {
    const audio = audioRef.current;
    if (!audio || disabled) return;
    audio.currentTime = clampSeconds(seconds, effectiveTotalSeconds);
  }

  function handleSkip(delta: number) {
    const audio = audioRef.current;
    if (!audio || disabled) return;
    audio.currentTime = clampSeconds(audio.currentTime + delta, effectiveTotalSeconds);
  }

  function handleSpeedChange(speed: SpeedOption) {
    activeSpeedRef.current = speed;
    setActiveSpeed(speed);
    if (audioRef.current) audioRef.current.playbackRate = speed;
  }

  function handleLoadedMetadata() {
    const audio = audioRef.current;
    if (!audio) return;
    const duration = audio.duration ?? 0;
    setDurationSeconds(Number.isFinite(duration) ? duration : Math.max(0, totalSeconds));
    // 일부 브라우저는 loadedmetadata 시점에 playbackRate를 1.0으로 되돌려 한 번 더 적용한다
    audio.playbackRate = activeSpeedRef.current;
  }

  function handleEnded() {
    notifyPlaying(false);
    setCurrentSeconds(effectiveTotalSeconds);
    onEnded?.();
  }

  return (
    <div
      className="audio-player-bar"
      aria-label="음성 수업 플레이어"
      style={{ '--audio-player-right': `${rightInset}px` } as CSSProperties}
    >
      <audio
        ref={audioRef}
        preload="metadata"
        onTimeUpdate={(event) => setCurrentSeconds(event.currentTarget.currentTime)}
        onLoadedMetadata={handleLoadedMetadata}
        onDurationChange={handleLoadedMetadata}
        onPlay={() => notifyPlaying(true)}
        onPause={() => notifyPlaying(false)}
        onEnded={handleEnded}
      />

      {/* 컨트롤 순서: [−10초] [재생·일시정지] [+10초] */}
      <button className="audio-player-skip" onClick={() => handleSkip(-10)} disabled={isAtStart} aria-label="10초 뒤로 이동" title="-10초">
        <BackIcon />
        <span>10</span>
      </button>

      <button
        className="audio-player-main"
        aria-pressed={playing}
        aria-label={playing ? '음성 일시정지' : '음성 재생'}
        onClick={handlePlayPause}
        disabled={disabled}
        title={disabled ? '음성 준비 중' : playing ? '일시정지' : '재생'}
        style={{ '--audio-main-bg': disabled ? 'var(--sand-2)' : palette.primary } as CSSProperties}
      >
        {playing ? <PauseIcon /> : <Icon name="play" size={12} color="var(--paper)" />}
      </button>

      <button className="audio-player-skip" onClick={() => handleSkip(10)} disabled={disabled || isAtEnd} aria-label="10초 앞으로 이동" title="+10초">
        <span>10</span>
        <ForwardIcon />
      </button>

      <AudioSeekBar
        currentSeconds={currentSeconds}
        totalSeconds={effectiveTotalSeconds}
        onSeek={handleSeek}
        palette={palette}
        disabled={disabled}
      />

      {disabled ? (
        <span
          className="audio-player-status"
          style={{
            '--audio-status-bg': palette.wash,
            '--audio-status-color': palette.primary,
          } as CSSProperties}
        >
          음성 준비 중
        </span>
      ) : (
        <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
          {SPEED_OPTIONS.map(speed => (
            <button
              key={speed}
              className="audio-player-speed"
              data-active={activeSpeed === speed}
              aria-pressed={activeSpeed === speed}
              aria-label={`${speed}배속으로 듣기`}
              onClick={() => handleSpeedChange(speed)}
              style={{
                '--audio-speed-color': activeSpeed === speed ? palette.primary : 'var(--ink-3)',
                '--audio-speed-border': activeSpeed === speed ? `1px solid ${palette.primary}` : '1px solid transparent',
              } as CSSProperties}
            >
              {speed}x
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function resolveAudioUrl(value: string | null): string | null {
  if (!value || value.startsWith('file://')) return null;
  if (/^(https?:|blob:|data:)/.test(value)) return value;
  if (value.startsWith('/media/') && env.apiFastapi) return `${env.apiFastapi}${value}`;
  return value;
}

function clampSeconds(value: number, totalSeconds: number): number {
  const max = totalSeconds > 0 ? totalSeconds : Number.MAX_SAFE_INTEGER;
  return Math.min(Math.max(0, value), max);
}
