// 음성 입력 중 텍스트 입력창 대신 보이는 VAD 상태 뷰.
// 에너지 바, 상태 점, 부분 인식 텍스트로 구성한다.
import type { Palette } from '@/lib';
import type { VADState } from '@/shared/audio';

interface VoiceInputDisplayProps {
  vadState: VADState;
  vadEnergy: number;
  partialTranscript?: string;
  palette: Palette;
}

/** VAD 상태별 안내 메시지 */
function getPlaceholderText(vadState: VADState): string {
  if (vadState === 'silence') return '발화 종료 감지 대기 중...';
  return '말씀해 주세요';
}

export function VoiceInputDisplay({ vadState, vadEnergy, partialTranscript, palette }: VoiceInputDisplayProps) {
  // VAD 상태별 점 색상: 음성 중 primary, 묵음 muted, 대기 amber
  const indicatorColor =
    vadState === 'speech'   ? palette.primary :
    vadState === 'silence'  ? 'var(--text-muted)' :
    'var(--honey-accent)';

  // speech 상태에서만 펄스를 준다. prefers-reduced-motion은 CSS에서 끈다.
  const pulseClass = vadState === 'speech' ? 'vad-pulse' : undefined;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '10px 0 2px' }}>
      {/* 상태 점과 에너지 레벨 바 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span
          className={pulseClass}
          style={{
            display: 'inline-block',
            width: 8,
            height: 8,
            borderRadius: '50%',
            background: indicatorColor,
            flexShrink: 0,
          }}
        />
        {/* 에너지 바 (높이 6px) */}
        <div style={{
          flex: 1,
          height: 6,
          borderRadius: 3,
          background: 'var(--sand-2)',
          overflow: 'hidden',
        }}>
          <div style={{
            height: '100%',
            borderRadius: 3,
            /* speech 상태는 primary, 나머지는 primary2 */
            background: vadState === 'speech' ? palette.primary : palette.primary2,
            width: `${Math.round(vadEnergy * 100)}%`,
            transition: 'width 0.08s ease-out',
          }} />
        </div>
      </div>

      {/* 부분 인식 텍스트 또는 안내 문구 */}
      {partialTranscript ? (
        <div style={{
          fontSize: 14,
          color: 'var(--text-tertiary)',
          fontStyle: 'italic',
          minHeight: 20,
          lineHeight: 1.4,
        }}>
          {partialTranscript}
        </div>
      ) : (
        <div style={{ fontSize: 14, color: 'var(--text-muted)', minHeight: 20 }}>
          {getPlaceholderText(vadState)}
        </div>
      )}
    </div>
  );
}
