// 학습 튜터 탭: 채팅 목록, 타이핑 표시, 입력 바.
// 음성 모드 props는 ChatInputBar로 그대로 넘긴다.
import { type RefObject } from 'react';
import type { Palette } from '@/lib';
import type { AppState } from '@/shared/types';
import type { VADState } from '@/shared/audio';
import Icon from '@/Icon';
import { LearningBubble } from './LearningBubble';
import { ChatInputBar } from './ChatInputBar';
import type { ChatMessage } from './useLearningChat';

interface LearningChatTabProps {
  chat: ChatMessage[];
  typing: boolean;
  input: string;
  setInput: (v: string) => void;
  send: () => void;
  /** 전송 실패 메시지 재시도 핸들러. 실패 말풍선 인덱스를 넘긴다 */
  retry: (failedIdx: number) => void;
  scrollRef: RefObject<HTMLDivElement>;
  palette: Palette;
  appState: AppState;
  /** 현재 음성 모드 활성 여부 */
  voiceMode?: boolean;
  /** 음성 모드 토글 핸들러 */
  onToggleVoice?: () => void;
  /** VAD 상태 */
  vadState?: VADState;
  /** 실시간 에너지 레벨 (0~1) */
  vadEnergy?: number;
  /** 부분 인식 텍스트 */
  partialTranscript?: string;
}

export function LearningChatTab({
  chat, typing, input, setInput, send, retry, scrollRef, palette, appState,
  voiceMode, onToggleVoice, vadState, vadEnergy, partialTranscript,
}: LearningChatTabProps) {
  return (
    <>
      {/* 튜터 헤더. 메신저보다 학습 보조 도구처럼 보이게 낮춘다 */}
      <div style={{
        padding: '12px 16px 10px',
        /* 구분선이 눈에 띄지 않게 --border-soft를 쓴다 */
        borderBottom: '1px solid var(--border-soft)',
        display: 'flex',
        alignItems: 'center',
        gap: 9,
      }}>
        {/* 튜터 아바타. 본문 채팅 크기에 맞춘다 */}
        <div style={{
          width: 26, height: 26, borderRadius: '50%',
          background: 'var(--sand-2)', display: 'grid', placeItems: 'center', color: 'var(--ink-3)',
        }}>
          <Icon name="owl" size={13} strokeWidth={1.6} />
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-2)' }}>학습 튜터</div>
          <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>현재 슬라이드 참조 중</div>
        </div>
      </div>

      {/* 채팅 스크롤 영역. 배경은 패널과 같은 --bg */}
      <div
        ref={scrollRef}
        className="scroll"
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '14px 16px 18px',
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
          /* 패널 배경과 같은 색이라 채팅 영역 구분선이 없다 */
          background: 'var(--bg)',
        }}
      >
        {chat.map((m, i) => (
          <LearningBubble
            key={i}
            m={m}
            palette={palette}
            // 실패 메시지에만 재시도 핸들러를 넘겨 재시도 버튼을 보인다
            onRetry={m.failed ? () => retry(i) : undefined}
          />
        ))}

        {/* AI 응답 대기 중 타이핑 표시 */}
        {typing && (
          <div style={{ display: 'flex', gap: 9 }}>
            <div style={{
              width: 26, height: 26, borderRadius: '50%',
              background: 'var(--honey-pale)', display: 'grid', placeItems: 'center',
            }}>
              <Icon name="owl" size={13} />
            </div>
            <div style={{
              padding: '10px 13px',
              background: 'var(--paper)',
              border: '1px solid var(--line)',
              borderRadius: '2px 12px 12px 12px',
              display: 'flex',
              gap: 4,
            }}>
              {[0, 1, 2].map(idx => (
                <div key={idx} style={{
                  width: 5, height: 5, borderRadius: 3,
                  background: palette.primary2,
                  animation: `float 1.2s ease-in-out ${idx * 0.18}s infinite`,
                }} />
              ))}
            </div>
          </div>
        )}
      </div>

      <ChatInputBar
        input={input}
        setInput={setInput}
        send={send}
        palette={palette}
        coins={appState.coins}
        voiceMode={voiceMode}
        onToggleVoice={onToggleVoice}
        vadState={vadState}
        vadEnergy={vadEnergy}
        partialTranscript={partialTranscript}
      />
    </>
  );
}
