// 채팅 입력 영역: 빠른 질문 칩, 텍스트/음성 입력창, 전송 버튼.
// 음성 모드에서는 textarea 대신 VoiceInputDisplay를 그리고 전송 버튼을 끈다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';
import AICoinIcon from '@/components/AICoinIcon';
import type { VADState } from '@/shared/audio';
import { VoiceInputDisplay } from './VoiceInputDisplay';

const QUICK_CHIPS = ['요약해줘', '예제 더', '헷갈려요'] as const;

interface ChatInputBarProps {
  input: string;
  setInput: (v: string) => void;
  send: () => void;
  palette: Palette;
  coins: number;
  /** 현재 음성 모드 활성 여부 */
  voiceMode?: boolean;
  /** 음성 모드 토글 핸들러 */
  onToggleVoice?: () => void;
  /** 음성 모드 UI에 쓰는 VAD 상태 */
  vadState?: VADState;
  /** 실시간 에너지 레벨 (0~1) */
  vadEnergy?: number;
  /** STT 부분 인식 텍스트 */
  partialTranscript?: string;
}

/** VAD 상태에 따른 하단 안내 문구를 반환 */
function getVoiceFooterText(vadState: VADState): string {
  if (vadState === 'speech') return '음성 감지 중...';
  if (vadState === 'silence') return '말이 끝나면 자동으로 전송됩니다';
  return '음성 입력 대기 중';
}

export function ChatInputBar({
  input, setInput, send, palette, coins,
  voiceMode = false,
  onToggleVoice,
  vadState = 'idle',
  vadEnergy = 0,
  partialTranscript,
}: ChatInputBarProps) {
  // 음성 모드에서는 endpoint 이벤트가 전송하므로 버튼을 끈다. 입력이 없을 때도 끈다.
  const sendDisabled = voiceMode || !input.trim();

  return (
    <div style={{ padding: '12px 16px 16px', borderTop: '1px solid var(--line)', background: 'var(--paper)' }}>
      {/* 빠른 질문 칩은 음성 모드에서도 보이되 비활성화한다 */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 10, flexWrap: 'wrap' }}>
        {QUICK_CHIPS.map(q => (
          <button
            key={q}
            className="learning-chip-button"
            disabled={voiceMode}
            onClick={() => !voiceMode && setInput(q)}
            style={{
            padding: '5px 10px',
            borderRadius: 999,
            fontSize: 12,
            color: voiceMode ? 'var(--text-muted)' : 'var(--ink-2)',
            background: 'var(--sand)',
            border: '1px solid var(--line)',
            fontWeight: 500,
            cursor: voiceMode ? 'default' : 'pointer',
            opacity: voiceMode ? 0.5 : 1,
            }}
          >
            {q}
          </button>
        ))}
      </div>

      {/* 음성 모드에서는 primary 계열 테두리가 맥동한다 */}
      <div style={{
        background: 'var(--paper)',
        border: `1.5px solid ${voiceMode ? palette.soft : input ? palette.soft : 'var(--line)'}`,
        borderRadius: 10,
        padding: '10px 12px',
        display: 'flex',
        gap: 8,
        alignItems: 'flex-end',
        /* 맥동 box-shadow는 @keyframes 대신 CSS animation 클래스로 준다 */
        animation: voiceMode ? 'voice-pulse 1.6s ease-in-out infinite' : 'none',
      }}>
        {voiceMode ? (
          <div style={{ flex: 1 }}>
            <VoiceInputDisplay
              vadState={vadState}
              vadEnergy={vadEnergy}
              partialTranscript={partialTranscript}
              palette={palette}
            />
          </div>
        ) : (
          <textarea
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } }}
            placeholder="이 페이지 내용에 대해 물어보세요"
            rows={1}
            style={{
              flex: 1,
              resize: 'none',
              border: 'none',
              outline: 'none',
              fontSize: 13,
              fontFamily: 'inherit',
              background: 'transparent',
              minHeight: 24,
            }}
          />
        )}

        {/* 마이크 토글 버튼. 음성 모드에서는 primary 색으로 강조한다 */}
        {onToggleVoice && (
          <button
            className="learning-icon-button"
            data-active={voiceMode}
            aria-pressed={voiceMode}
            onClick={onToggleVoice}
            title={voiceMode ? '음성 모드 끄기' : '음성 모드 켜기'}
            style={{
              width: 32,
              height: 30,
              borderRadius: 6,
              background: voiceMode ? palette.primary : 'var(--sand)',
              color: voiceMode ? 'var(--paper)' : 'var(--ink-3)',
              display: 'grid',
              placeItems: 'center',
              position: 'relative',
            }}
          >
            <Icon name="mic" size={14} color={voiceMode ? 'var(--paper)' : 'var(--ink-3)'} />
            {/* 음성 모드일 때만 보이는 활성 표시 점 */}
            {voiceMode && (
              <span
                className="vad-pulse"
                style={{
                  position: 'absolute',
                  top: 3,
                  right: 3,
                  width: 5,
                  height: 5,
                  borderRadius: '50%',
                  background: palette.primary2,
                }}
              />
            )}
          </button>
        )}

        <button
          className="learning-icon-button"
          onClick={send}
          disabled={sendDisabled}
          style={{
            width: 32,
            height: 30,
            borderRadius: 6,
            background: sendDisabled ? 'var(--sand-2)' : palette.primary,
            color: 'var(--paper)',
            fontSize: 13,
            fontWeight: 700,
            cursor: sendDisabled ? 'default' : 'pointer',
          }}
        >
          <Icon name="arrowUp" size={14} color="var(--paper)" />
        </button>
      </div>

      {/* 하단 안내 텍스트 + 코인 잔액 */}
      <div style={{
        marginTop: 6,
        fontSize: 12,
        color: 'var(--muted)',
        display: 'flex',
        justifyContent: 'space-between',
      }}>
        <span>{voiceMode ? getVoiceFooterText(vadState) : '엔터로 전송'}</span>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3 }}>
          <AICoinIcon size={11} decorative /> {coins} · Q&amp;A 무료
        </span>
      </div>
    </div>
  );
}
