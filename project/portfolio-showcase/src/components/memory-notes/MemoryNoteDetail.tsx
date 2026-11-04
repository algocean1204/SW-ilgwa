// 선택된 노트 상세 패널. 노트 종류(qa/bullet/code/compare)마다 그리는 방식이 다르다.
// 패널을 닫을지는 부모가 onClose로 정한다.
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import type { MemoNote } from '@/shared/types';
import { renderMarkdown } from '@/shared/sanitizeHtml';

interface MemoryNoteDetailProps {
  note: MemoNote;
  palette: Palette;
  onClose: () => void;
  onToggleStar: () => void;
  /** 텔레그램 전송 콜백 */
  onSendTelegram?: () => void;
  /** 이미 전송했는지 여부 */
  telegramSent?: boolean;
}

export function MemoryNoteDetail({
  note, palette, onClose, onToggleStar, onSendTelegram, telegramSent,
}: MemoryNoteDetailProps) {
  return (
    <div className="scroll" style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
      {/* 헤더: 닫기 버튼, 주제 레이블 */}
      <div style={{ padding: '18px 20px 0', borderBottom: '1px solid var(--line)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <button
            onClick={onClose}
            style={{ width: 26, height: 26, borderRadius: 6, color: 'var(--ink-3)', display: 'grid', placeItems: 'center' }}
            title="닫기"
          >
            <Icon name="x" size={14} />
          </button>
          <span style={{ fontSize: 12, color: 'var(--ink-3)', fontWeight: 600, flex: 1 }}>{note.topicLabel}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
          <span style={{
            fontSize: 12, fontWeight: 700, padding: '2px 7px', borderRadius: 5,
            background: palette.wash, color: palette.primary,
            border: `1px solid ${palette.soft}`,
            textTransform: 'uppercase', letterSpacing: '.05em',
          }}>
            {note.tag}
          </span>
          <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>{note.date} · {note.lesson}</span>
        </div>
        <h2 style={{
          margin: '0 0 16px', fontSize: 18, fontWeight: 700,
          color: 'var(--ink)', letterSpacing: '-.02em', lineHeight: 1.3,
        }}>
          {note.title}
        </h2>
      </div>

      {/* 바디: 종류별 콘텐츠 렌더 */}
      <div style={{ padding: '18px 20px', flex: 1 }}>
        {/* body는 서버가 HTML로 보내므로 DOMPurify로 정리한 뒤 렌더한다 */}
        <div
          className="note-html-content"
          style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6, marginBottom: 14 }}
          dangerouslySetInnerHTML={{ __html: renderMarkdown(note.body) }}
        />

        {/* qa 상세 설명 */}
        {note.detail && (
          <div style={{
            padding: '12px 14px', background: 'var(--bg)', border: '1px solid var(--line)',
            borderRadius: 10, fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6,
          }}>
            <span dangerouslySetInnerHTML={{ __html: renderMarkdown(note.detail) }} />
          </div>
        )}

        {/* bullet 목록 */}
        {note.bullets && (
          <ul style={{
            margin: 0, padding: '12px 18px', background: 'var(--bg)',
            border: '1px solid var(--line)', borderRadius: 10, listStyle: 'none',
          }}>
            {note.bullets.map((b, i) => (
              <li key={i} style={{
                padding: '5px 0', fontSize: 13, color: 'var(--ink-2)',
                display: 'flex', gap: 8, alignItems: 'flex-start',
              }}>
                <span className="mono" style={{ color: palette.primary, fontSize: 10, marginTop: 2, fontWeight: 700 }}>
                  {String(i + 1).padStart(2, '0')}
                </span>
                <span>{b}</span>
              </li>
            ))}
          </ul>
        )}

        {/* code 블록 */}
        {note.code && (
          <pre className="mono" style={{
            margin: 0, padding: '12px 14px', background: 'var(--code-bg)',
            borderRadius: 10, fontSize: 11, lineHeight: 1.6,
            color: 'var(--syn-default)', overflow: 'auto', whiteSpace: 'pre',
          }}>
            {note.code}
          </pre>
        )}

        {/* compare 비교 표 */}
        {note.compare && (
          <div style={{ border: '1px solid var(--line)', borderRadius: 10, overflow: 'hidden', background: 'var(--paper)' }}>
            <div style={{
              display: 'grid', gridTemplateColumns: '80px 1fr 1fr',
              padding: '8px 12px', background: 'var(--sand)',
              fontSize: 12, fontWeight: 700, color: 'var(--ink-3)',
              letterSpacing: '.04em', textTransform: 'uppercase',
            }}>
              <span>항목</span><span>A</span><span>B</span>
            </div>
            {note.compare.map((row, i) => (
              <div key={i} style={{
                display: 'grid', gridTemplateColumns: '80px 1fr 1fr',
                padding: '9px 12px', fontSize: 12,
                borderTop: '1px solid var(--line)', color: 'var(--ink-2)',
              }}>
                <span style={{ fontWeight: 600, color: 'var(--ink)' }}>{row[0]}</span>
                <span>{row[1]}</span>
                <span style={{ color: palette.primary, fontWeight: 600 }}>{row[2]}</span>
              </div>
            ))}
          </div>
        )}

        {/* 편집, 즐겨찾기 버튼 */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 16 }}>
          <button className="lift" style={{
            padding: '10px', borderRadius: 10, background: 'var(--paper)',
            border: '1px solid var(--line)', fontSize: 12, fontWeight: 600,
            color: 'var(--ink-2)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
          }}>
            <Icon name="edit" size={11} /> 편집
          </button>
          <button onClick={onToggleStar} className="lift" style={{
            padding: '10px', borderRadius: 10,
            background: note.starred ? palette.wash : 'var(--paper)',
            border: `1px solid ${note.starred ? palette.soft : 'var(--line)'}`,
            fontSize: 12, fontWeight: 600,
            color: note.starred ? palette.primary : 'var(--ink-2)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
          }}>
            <Icon name="star" size={11}
              color={note.starred ? palette.primary : 'var(--ink-2)'}
              style={note.starred ? { fill: palette.primary } : undefined} />
            {note.starred ? '즐겨찾기 해제' : '즐겨찾기'}
          </button>
        </div>

        {/* 텔레그램 전송 버튼. 전송하면 색과 문구가 바뀌고 비활성화된다 */}
        <button
          onClick={onSendTelegram}
          disabled={telegramSent}
          className="lift"
          style={{
            width: '100%', marginTop: 8, padding: '11px', borderRadius: 10,
            background: telegramSent ? 'var(--telegram-sent-bg)' : 'var(--telegram-blue)',
            color: 'var(--paper)',
            fontSize: 13, fontWeight: 700,
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            cursor: telegramSent ? 'default' : 'pointer',
          }}
        >
          <Icon name="send" size={13} />
          {telegramSent ? '전송됨' : '텔레그램으로 보내기'}
        </button>
      </div>
    </div>
  );
}
