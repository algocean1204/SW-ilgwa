// 재생 진행 바와 현재/전체 시간 표시.
// 시크 이벤트는 부모로 넘겨 AudioPlayerBar 상태 변경과 분리한다.
import type { CSSProperties } from 'react';
import type { Palette } from '@/lib';

/** 초를 mm:ss 형식으로 바꾼다 */
export function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

interface AudioSeekBarProps {
  /** 현재 재생 위치 (초) */
  currentSeconds: number;
  /** 전체 길이 (초) */
  totalSeconds: number;
  /** 바를 클릭해 시크할 때 이동할 초를 넘겨 호출한다 */
  onSeek: (seconds: number) => void;
  palette: Palette;
  disabled?: boolean;
}

/** 클릭 시크와 thumb 핸들이 있는 재생 진행 바 */
export function AudioSeekBar({ currentSeconds, totalSeconds, onSeek, palette, disabled = false }: AudioSeekBarProps) {
  /** 진행률 퍼센트 (0~100) */
  const safeTotalSeconds = Math.max(0, totalSeconds);
  const safeCurrentSeconds = Math.min(Math.max(0, currentSeconds), safeTotalSeconds);
  const progressPct = safeTotalSeconds > 0 ? (safeCurrentSeconds / safeTotalSeconds) * 100 : 0;

  return (
    <div className="audio-seekbar">
      {/* tabular-nums로 숫자 너비를 고정한다 */}
      <span className="audio-seekbar__time">
        {formatTime(safeCurrentSeconds)} / {formatTime(safeTotalSeconds)}
      </span>

      {/* range 입력이라 마우스와 키보드로 모두 조작할 수 있다. */}
      <input
        className="audio-seek-input"
        type="range"
        min={0}
        max={safeTotalSeconds}
        value={safeCurrentSeconds}
        aria-label="음성 수업 재생 위치"
        aria-valuemin={0}
        aria-valuemax={safeTotalSeconds}
        aria-valuenow={safeCurrentSeconds}
        aria-valuetext={`${formatTime(safeCurrentSeconds)} / ${formatTime(safeTotalSeconds)}`}
        disabled={disabled}
        onChange={(event) => onSeek(Number(event.currentTarget.value))}
        title="클릭해서 이동"
        style={{
          '--audio-progress': `${progressPct}%`,
          '--audio-primary': palette.primary,
          '--audio-primary2': palette.primary2,
        } as CSSProperties}
      />
    </div>
  );
}
