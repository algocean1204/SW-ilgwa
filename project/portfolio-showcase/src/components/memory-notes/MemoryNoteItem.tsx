// 암기노트 노트 하나의 보기·편집 화면
import { useState, useEffect, useRef } from 'react';
import type { Palette } from '@/lib';
import type { MemoNote } from '@/shared/types';
import Icon from '@/Icon';
import { renderMarkdown } from '@/shared/sanitizeHtml';

// textarea 자동 높이의 최소값(px)
const EDIT_MIN_HEIGHT = 120;

interface MemoryNoteItemProps {
  note: MemoNote;
  palette: Palette;
  onToggleStar: () => void;
  onSaveEdit: (content: string) => void;
  /** 텔레그램 전송 콜백 */
  onSendTelegram?: () => void;
  /** 이미 전송했는지 여부 */
  telegramSent?: boolean;
  /** 노트 삭제 콜백 */
  onDelete?: () => void;
}

export function MemoryNoteItem({
  note, palette, onToggleStar, onSaveEdit, onSendTelegram, telegramSent, onDelete,
}: MemoryNoteItemProps) {
  const [editing, setEditing] = useState(false);
  // 편집 중인 마크다운 원문(textarea controlled value)
  const [editValue, setEditValue] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const startEdit = () => {
    setEditValue(note.detail ?? note.body);
    setEditing(true);
  };

  useEffect(() => {
    if (!editing || !textareaRef.current) return;
    const el = textareaRef.current;
    el.focus();
    // 내용에 맞춰 높이 자동 조절
    el.style.height = 'auto';
    el.style.height = `${Math.max(el.scrollHeight, EDIT_MIN_HEIGHT)}px`;
  }, [editing]);

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setEditValue(e.target.value);
    const el = e.target;
    el.style.height = 'auto';
    el.style.height = `${Math.max(el.scrollHeight, EDIT_MIN_HEIGHT)}px`;
  };

  const saveEdit = () => {
    const trimmed = editValue.trim();
    if (trimmed) onSaveEdit(trimmed);
    setEditing(false);
  };

  return (
    <div style={{
      padding: '20px 24px', borderRadius: 14, background: 'var(--paper)',
      border: `1px solid ${note.starred ? palette.soft : 'var(--line)'}`,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
        <span style={{
          fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 5,
          background: palette.wash, color: palette.primary, border: `1px solid ${palette.soft}`,
        }}>
          {note.tag}
        </span>
        <div style={{ flex: 1 }} />
        <button onClick={startEdit} aria-label="편집"
          style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, lineHeight: 0, marginRight: 6 }}>
          <Icon name="edit" size={18} color="var(--ink-3)" />
        </button>
        <button onClick={onToggleStar} aria-label={note.starred ? '즐겨찾기 해제' : '즐겨찾기'}
          style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, lineHeight: 0 }}>
          <Icon name="star" size={18}
            color={note.starred ? palette.primary : 'var(--ink-3)'}
            style={note.starred ? { fill: palette.primary } : undefined} />
        </button>
      </div>

      {note.title && (
        <div style={{ fontSize: 17, fontWeight: 700, color: 'var(--ink)', marginBottom: 10, lineHeight: 1.35 }}>
          {note.title}
        </div>
      )}

      {editing ? (
        <div>
          <textarea
            ref={textareaRef}
            value={editValue}
            onChange={handleChange}
            onBlur={saveEdit}
            onKeyDown={e => { if (e.key === 'Escape') setEditing(false); }}
            style={{
              width: '100%', minHeight: EDIT_MIN_HEIGHT, fontSize: 13, lineHeight: 1.6,
              fontFamily: 'inherit', resize: 'vertical',
              color: 'var(--ink)', background: 'var(--bg)', border: `1px solid ${palette.soft}`,
              borderRadius: 10, padding: '12px 14px', outline: 'none',
            }}
          />
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
            바깥 클릭으로 저장 · Esc로 취소
          </div>
        </div>
      ) : (
        <div onClick={startEdit}
          className="note-html-content"
          style={{ fontSize: 13, lineHeight: 1.65, color: 'var(--ink-2)', cursor: 'text' }}
          dangerouslySetInnerHTML={{ __html: renderMarkdown(note.detail ?? note.body) }}
        />
      )}

      {/* 액션 버튼: 텔레그램 전송, 노트 삭제 */}
      <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
        {onSendTelegram && (
          <button
            onClick={onSendTelegram}
            disabled={telegramSent}
            className="lift"
            style={{
              flex: 1, padding: '9px', borderRadius: 10,
              background: telegramSent ? 'var(--telegram-sent-bg)' : 'var(--telegram-blue)',
              color: 'var(--paper)',
              fontSize: 12, fontWeight: 700,
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
              cursor: telegramSent ? 'default' : 'pointer',
              border: 'none',
            }}
          >
            <Icon name="send" size={11} />
            {telegramSent ? '전송됨' : '텔레그램 전송'}
          </button>
        )}
        {onDelete && (
          <button
            onClick={onDelete}
            className="lift"
            aria-label="노트 삭제"
            style={{
              padding: '9px 14px', borderRadius: 10,
              background: 'var(--paper)', border: '1px solid var(--line)',
              fontSize: 12, fontWeight: 600, color: 'var(--ink-3)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
              cursor: 'pointer',
            }}
          >
            <Icon name="trash" size={11} color="var(--ink-3)" /> 삭제
          </button>
        )}
      </div>

    </div>
  );
}
