// 학습 튜터 채팅 말풍선. 튜터와 사용자를 나눠 그린다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';
import type { ChatMessage } from './useLearningChat';
import { renderMarkdown } from '@/shared/sanitizeHtml';

// 재시도 아이콘은 Icon 세트에 없어 인라인 SVG로 정의한다
function RetryIcon() {
  return (
    <svg width={10} height={10} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"
      style={{ display: 'inline-block', verticalAlign: 'middle', marginRight: 3 }}>
      <path d="M3 12a9 9 0 1 0 3-6.7L3 3v5h5" />
    </svg>
  );
}

interface LearningBubbleProps {
  m: ChatMessage;
  palette: Palette;
  /** 전송 실패 메시지 재시도 핸들러. 메시지 인덱스를 넘긴다 */
  onRetry?: () => void;
}

export function LearningBubble({ m, palette, onRetry }: LearningBubbleProps) {
  // 사용자 발화: 오른쪽 정렬, 팔레트 배경
  if (m.who === 'me') {
    // 전송 실패면 에러 안내와 재시도 버튼을 보여 준다
    if (m.failed) {
      return (
        <div className="rise" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 5 }}>
          <div style={{
            maxWidth: '88%',
            background: 'var(--state-danger-bg)',
            border: '1px solid var(--state-danger)',
            color: 'var(--state-danger)',
            padding: '10px 13px',
            borderRadius: '12px 12px 2px 12px',
            fontSize: 13,
            lineHeight: 1.58,
            whiteSpace: 'pre-wrap',
          }}>
            {m.text}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontSize: 11, color: 'var(--state-danger)' }}>전송 실패</span>
            {onRetry && (
              <button
                onClick={onRetry}
                style={{
                  padding: '4px 9px',
                  borderRadius: 6,
                  fontSize: 11,
                  fontWeight: 700,
                  background: 'var(--state-danger)',
                  color: 'var(--paper)',
                  border: 'none',
                  cursor: 'pointer',
                }}
              >
                <RetryIcon />
                재시도
              </button>
            )}
          </div>
        </div>
      );
    }

    return (
      <div className="rise" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
        <div style={{
          maxWidth: '88%',
          background: palette.primary,
          color: 'var(--paper)',
          padding: '10px 13px',
          borderRadius: '12px 12px 2px 12px',
          fontSize: 13,
          lineHeight: 1.58,
          whiteSpace: 'pre-wrap',
        }}>
          {m.text}
        </div>
        {m.time && <div style={{ fontSize: 12, color: 'var(--muted)' }}>{m.time}</div>}
      </div>
    );
  }

  // 튜터 발화: 왼쪽 정렬, 올빼미 아이콘, **bold** 렌더
  return (
    <div className="rise" style={{ display: 'flex', gap: 9, alignItems: 'flex-start' }}>
      <div style={{
        width: 26,
        height: 26,
        borderRadius: '50%',
        background: 'var(--honey-pale)',
        display: 'grid',
        placeItems: 'center',
        color: 'var(--honey-ink3)',
        flexShrink: 0,
      }}>
        <Icon name="owl" size={13} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          className="chat-markdown"
          dangerouslySetInnerHTML={{ __html: renderMarkdown(m.text) }}
          style={{
            background: 'var(--paper)',
            border: '1px solid var(--line)',
            padding: '10px 13px',
            borderRadius: '2px 12px 12px 12px',
            fontSize: 13,
            lineHeight: 1.62,
          }}
        />
        {/* 튜터가 제안하는 선택지 칩 */}
        {m.chips && (
          <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap', marginTop: 6 }}>
            {m.chips.map(c => (
              <button key={c} style={{
                padding: '4px 8px',
                borderRadius: 999,
                fontSize: 12,
                fontWeight: 600,
                background: 'var(--paper)',
                border: `1px solid ${palette.soft}`,
                color: palette.primary,
              }}>
                {c}
              </button>
            ))}
          </div>
        )}
        {m.time && <div style={{ fontSize: 10, color: 'var(--muted)', marginTop: 4 }}>{m.time}</div>}
      </div>
    </div>
  );
}
