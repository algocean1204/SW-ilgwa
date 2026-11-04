// 학습실 우측 AI 노트 탭.
// getLessonNotes()로 현재 챕터 노트를 불러와 카드로 그린다.
import { useState, useEffect, useCallback, useRef } from 'react';
import type { Palette } from '@/lib';
import { getLessonNotes, sendNoteToTelegram, toggleLessonNoteStar, updateLessonNote } from '@/apis';
import type { NoteResponse } from '@/apis';
import Icon from '@/Icon';
import { renderMarkdown } from '@/shared/sanitizeHtml';

interface LearningNoteTabProps {
  courseId: string;
  chapterId: string;
  palette: Palette;
}

/** 노트 타입 → 태그 라벨 매핑 */
function typeToTag(type: string): string {
  const map: Record<string, string> = {
    KEY_CONCEPT: '핵심', SUMMARY: '요약', QUESTION: '질문',
    EXAMPLE: '예시', COMPARISON: '비교', CODE: '코드',
  };
  return map[type] ?? type;
}

export function LearningNoteTab({ courseId, chapterId, palette }: LearningNoteTabProps) {
  const [notes, setNotes] = useState<NoteResponse[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [sentIds, setSentIds] = useState<Set<string>>(new Set());
  const editRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!courseId || !chapterId) return;
    void getLessonNotes(courseId, chapterId).then((res) => {
      if (res.data) setNotes(res.data);
    }).catch(() => {});
  }, [courseId, chapterId]);

  const startEdit = (note: NoteResponse) => {
    setEditingId(note.id);
  };

  // contentEditable div에 새니타이즈한 HTML을 넣고 포커스한다
  useEffect(() => {
    if (!editingId || !editRef.current) return;
    const note = notes.find(n => n.id === editingId);
    if (note) {
      editRef.current.innerHTML = renderMarkdown(note.content);
      editRef.current.focus();
    }
  }, [editingId, notes]);

  const saveEdit = useCallback((noteId: string) => {
    if (!courseId || !chapterId || !editRef.current) return;
    const trimmed = editRef.current.innerHTML.trim();
    if (!trimmed) { setEditingId(null); return; }
    setNotes(prev => prev.map(n => n.id === noteId ? { ...n, content: trimmed } : n));
    setEditingId(null);
    void updateLessonNote(courseId, chapterId, noteId, { content: trimmed }).catch(() => {});
  }, [courseId, chapterId]);

  const handleToggleStar = useCallback((noteId: string) => {
    if (!courseId || !chapterId) return;
    setNotes(prev => prev.map(n => n.id === noteId ? { ...n, starred: !n.starred } : n));
    void toggleLessonNoteStar(courseId, chapterId, noteId).catch(() => {
      setNotes(prev => prev.map(n => n.id === noteId ? { ...n, starred: !n.starred } : n));
    });
  }, [courseId, chapterId]);

  const toggleTelegram = useCallback((noteId: string) => {
    if (sentIds.has(noteId)) return;
    setSentIds(s => new Set(s).add(noteId));
    void sendNoteToTelegram(noteId).catch(() => {
      setSentIds(s => { const next = new Set(s); next.delete(noteId); return next; });
    });
  }, [sentIds]);

  return (
    <div className="scroll" style={{ flex: 1, overflowY: 'auto', padding: '14px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--ink-3)', marginBottom: 10 }}>
        <span style={{ width: 6, height: 6, borderRadius: 3, background: palette.primary2 }} className="pulse" aria-hidden />
        <span style={{ flex: 1 }}>강의별 자동 생성 · <span className="mono">{notes.length}개</span></span>
      </div>

      {notes.length === 0 ? (
        <div style={{ padding: '40px 0', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
          <Icon name="note" size={24} color="var(--muted)" />
          <div style={{ marginTop: 10 }}>이 챕터의 노트가 없어요</div>
        </div>
      ) : (
        notes.map(n => {
          const sent = sentIds.has(n.id);
          return (
            <div key={n.id} className="rise" style={{
              padding: '12px 14px', borderRadius: 12, background: 'var(--paper)',
              border: `1px solid ${n.starred ? palette.soft : 'var(--line)'}`,
              marginBottom: 10,
            }}>
              {/* 메타: 태그, 슬라이드 번호, 별표, 텔레그램 */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 5 }}>
                <span style={{
                  fontSize: 12, fontWeight: 700, padding: '2px 7px', borderRadius: 5,
                  background: palette.wash, color: palette.primary,
                  border: `1px solid ${palette.soft}`,
                }}>
                  {typeToTag(n.type)}
                </span>
                <div style={{ flex: 1 }} />
                <button
                  onClick={() => handleToggleStar(n.id)}
                  aria-label={n.starred ? '즐겨찾기 해제' : '즐겨찾기'}
                  aria-pressed={n.starred}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, lineHeight: 0 }}
                >
                  <Icon
                    name="star"
                    size={12}
                    color={n.starred ? palette.primary : 'var(--ink-3)'}
                    style={n.starred ? { fill: palette.primary } : undefined}
                  />
                </button>
                <button onClick={() => toggleTelegram(n.id)}
                  aria-label={sent ? '텔레그램 전송됨' : '텔레그램으로 보내기'}
                  style={{
                    width: 22, height: 22, borderRadius: 5, display: 'grid', placeItems: 'center',
                    background: sent ? 'var(--telegram-sent-bg)' : 'transparent',
                    color: sent ? 'var(--telegram-blue)' : 'var(--ink-3)',
                    border: sent ? '1px solid var(--telegram-sent-border)' : '1px solid transparent',
                    cursor: sent ? 'default' : 'pointer',
                  }}>
                  <Icon name="telegram" size={12} />
                </button>
              </div>

              {n.title && (
                <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink)', marginBottom: 4 }}>
                  {n.title}
                </div>
              )}

              {/* 본문: 편집 모드 / 읽기 모드 */}
              {editingId === n.id ? (
                <div>
                  <div
                    ref={editRef}
                    contentEditable
                    suppressContentEditableWarning
                    className="note-html-content"
                    onBlur={() => saveEdit(n.id)}
                    onKeyDown={e => { if (e.key === 'Escape') setEditingId(null); }}
                    style={{
                      width: '100%', minHeight: 60, fontSize: 13, lineHeight: 1.55,
                      color: 'var(--ink)', background: 'var(--bg)', border: `1px solid ${palette.soft}`,
                      borderRadius: 8, padding: '8px 10px', outline: 'none',
                    }}
                  />
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                    바깥 클릭으로 저장 · Esc로 취소
                  </div>
                </div>
              ) : (
                <div
                  onClick={() => startEdit(n)}
                  className="note-html-content"
                  style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink)', cursor: 'text' }}
                  dangerouslySetInnerHTML={{ __html: renderMarkdown(n.content) }}
                />
              )}
            </div>
          );
        })
      )}
    </div>
  );
}
