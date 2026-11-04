// 텔레그램 전송 대기 노트 목록. 최대 4개를 보여주고 "지금 모두 보내기" 버튼을 둔다.
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import type { MemoNote } from '@/shared/types';

interface TelegramQueueListProps {
  palette: Palette;
  queued: MemoNote[];
}

export function TelegramQueueList({ palette, queued }: TelegramQueueListProps) {
  return (
    <>
      {/* 섹션 헤더와 대기 개수 */}
      <div style={{
        fontSize: 12, fontWeight: 600, color: 'var(--muted)',
        letterSpacing: '.1em', textTransform: 'uppercase',
        margin: '20px 0 8px', padding: '0 2px',
        display: 'flex', alignItems: 'center',
      }}>
        <span>전송 대기</span>
        <span style={{
          marginLeft: 'auto', fontSize: 10, color: palette.primary,
          fontWeight: 700, textTransform: 'none', letterSpacing: 0,
        }}>
          {queued.length}개
        </span>
      </div>

      {/* 대기 항목 목록 (최대 4개) */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {queued.slice(0, 4).map(q => (
          <div key={q.id} style={{
            padding: '9px 12px', borderRadius: 9,
            background: 'var(--bg)', border: '1px solid var(--line)',
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
            <div style={{ width: 6, height: 6, borderRadius: 3, background: palette.primary2 }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{
                fontSize: 12, fontWeight: 600, color: 'var(--ink)',
                whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
              }}>
                {q.title}
              </div>
              <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{q.topicLabel}</div>
            </div>
          </div>
        ))}
      </div>

      {/* 일괄 전송 버튼 */}
      <button className="lift" style={{
        width: '100%', marginTop: 10, padding: '10px', borderRadius: 10,
        background: 'var(--telegram-blue)', color: 'var(--paper)',
        fontSize: 12, fontWeight: 700,
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
      }}>
        <Icon name="send" size={12} /> 지금 모두 보내기
      </button>
    </>
  );
}
