// 암기노트 모아보기. 좌측은 과외·챕터 트리, 중앙은 선택한 챕터의 노트 전문이다.
import { useState, useEffect, useCallback, useMemo } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import Icon from '@/Icon';
import { MemoryNotesSidebar } from '@/components/memory-notes/MemoryNotesSidebar';
import { MemoryNoteItem } from '@/components/memory-notes/MemoryNoteItem';
import TelegramLinkRequiredDialog from '@/components/memory-notes/TelegramLinkRequiredDialog';
import { getMyCourses, getAllNotes, toggleLessonNoteStar, updateLessonNote, sendNoteToTelegram, deleteLessonNote, getTelegramStatus } from '@/apis';
import type { CourseSummaryResponse, NoteListResponse } from '@/apis';
import type { AppState, MemoNote, NoteKind, RouteSetter } from '@/shared/types';

function formatRelativeDate(iso: string): string {
  const diffDays = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (diffDays === 0) return '오늘';
  if (diffDays === 1) return '어제';
  return `${diffDays}일 전`;
}

function toMemoNote(n: NoteListResponse): MemoNote {
  const kind = (['qa', 'bullet', 'code', 'compare'].includes(n.type) ? n.type : 'qa') as NoteKind;
  return {
    id: n.id, topic: n.courseId, topicLabel: n.courseName, kind,
    title: n.title, body: n.content.slice(0, 100), detail: n.content,
    starred: n.starred, tag: n.type || '노트', date: formatRelativeDate(n.createdAt),
    stamp: '—', lesson: n.chapterTitle, chapterId: n.chapterId,
    ...(kind === 'code' ? { code: n.content } : {}),
    ...(kind === 'bullet' ? { bullets: n.content.split('\n').filter(Boolean) } : {}),
  };
}

function MemoryNotes({ setRoute, appState }: { setRoute: RouteSetter; appState: AppState }) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;

  // 홈의 "많이 틀린 챕터 복습" 카드에서 넘어온 챕터 id가 있으면 처음에 선택한다
  const [selectedChapterId, setSelectedChapterId] = useState<string | null>(appState.selectedChapterId || null);
  // 선택한 챕터의 과외 ID. 과외 상태(CURRICULUM_READY 등)를 볼 때 쓴다.
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);
  const [courses, setCourses] = useState<CourseSummaryResponse[]>([]);
  const [notes, setNotes] = useState<MemoNote[]>([]);
  const [sentIds, setSentIds] = useState<Set<string>>(new Set());
  // 텔레그램 연동 여부. null은 조회 전이거나 실패한 상태이며, 이때는 전송을 막지 않는다.
  const [telegramLinked, setTelegramLinked] = useState<boolean | null>(null);
  // 미연동 상태에서 전송을 누르면 연동 안내 모달을 띄운다
  const [showLinkNotice, setShowLinkNotice] = useState(false);

  useEffect(() => {
    void getMyCourses({ size: 50 }).then((res) => {
      if (res.data?.content) setCourses(res.data.content);
    }).catch(() => {});
  }, []);

  useEffect(() => {
    void getAllNotes({ size: 200 }).then((res) => {
      if (res.data?.content) setNotes(res.data.content.map(toMemoNote));
    }).catch(() => {});
  }, []);

  // 텔레그램 연동 여부를 조회한다. 실패하면 null로 남겨 전송을 막지 않는다(fail-open).
  useEffect(() => {
    void getTelegramStatus().then((res) => {
      setTelegramLinked(res.data?.linked ?? null);
    }).catch(() => {});
  }, []);

  const handleToggleStar = useCallback((noteId: string) => {
    const note = notes.find(n => n.id === noteId);
    if (!note?.topic || !note.chapterId) return;
    setNotes(prev => prev.map(n => n.id === noteId ? { ...n, starred: !n.starred } : n));
    void toggleLessonNoteStar(note.topic, note.chapterId, noteId).catch(() => {
      setNotes(prev => prev.map(n => n.id === noteId ? { ...n, starred: !n.starred } : n));
    });
  }, [notes]);

  const handleSaveEdit = useCallback((noteId: string, content: string) => {
    const note = notes.find(n => n.id === noteId);
    if (!note?.topic || !note.chapterId) return;
    setNotes(prev => prev.map(n =>
      n.id === noteId ? { ...n, detail: content, body: content.slice(0, 100) } : n,
    ));
    void updateLessonNote(note.topic, note.chapterId, noteId, { content }).catch(() => {});
  }, [notes]);

  const handleSendTelegram = useCallback((noteId: string) => {
    // linked가 false일 때만 막고 연동 안내를 띄운다. null이면 그대로 전송한다(fail-open).
    if (telegramLinked === false) {
      setShowLinkNotice(true);
      return;
    }
    if (sentIds.has(noteId)) return;
    setSentIds(s => new Set(s).add(noteId));
    void sendNoteToTelegram(noteId).catch(() => {
      // 실패하면 전송됨 표시를 지워 다시 보낼 수 있게 한다
      setSentIds(s => { const next = new Set(s); next.delete(noteId); return next; });
    });
  }, [sentIds, telegramLinked]);

  const handleDeleteNote = useCallback((noteId: string) => {
    const note = notes.find(n => n.id === noteId);
    if (!note?.topic || !note.chapterId) return;
    // 낙관적 업데이트로 목록에서 먼저 지운다
    setNotes(prev => prev.filter(n => n.id !== noteId));
    void deleteLessonNote(note.topic, note.chapterId, noteId).catch(() => {
      // 삭제에 실패하면 노트를 목록에 되돌린다
      setNotes(prev => [...prev, note]);
    });
  }, [notes]);

  const noteCountByChapter = useMemo(() => {
    const map: Record<string, number> = {};
    for (const n of notes) {
      if (n.chapterId) map[n.chapterId] = (map[n.chapterId] ?? 0) + 1;
    }
    return map;
  }, [notes]);

  const currentNote = selectedChapterId
    ? notes.find(n => n.chapterId === selectedChapterId) ?? null
    : null;

  // 사이드바에서 고른 과외 id를 먼저 쓰고, 없으면(홈 딥링크로 들어온 경우)
  // 선택된 챕터의 노트에서 과외 id를 찾는다
  const effectiveCourseId = selectedCourseId ?? currentNote?.topic ?? null;

  return (
    <div style={{
      maxWidth: 'var(--page-max-w)', margin: '0 auto',
      padding: 'var(--page-py) var(--page-px) 40px',
      display: 'flex', gap: 0, alignItems: 'flex-start',
    }}>
      <MemoryNotesSidebar
        courses={courses} palette={palette}
        selectedChapterId={selectedChapterId}
        onSelectChapter={(courseId, chapterId) => {
          setSelectedCourseId(courseId);
          setSelectedChapterId(chapterId);
        }}
        noteCountByChapter={noteCountByChapter}
      />

      <main style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <div>
            <div style={{ fontSize: 12, fontWeight: 700, color: palette.primary, letterSpacing: '.1em', textTransform: 'uppercase' }}>MEMORY NOTES</div>
            <h1 style={{ margin: '4px 0 0', fontSize: 22, fontWeight: 800 }}>
              {currentNote?.lesson ?? '암기노트'}
            </h1>
          </div>
        </div>

        {/* 텔레그램 미연동 안내 모달. 설정 > 텔레그램 연동 탭으로 이동할 수 있다 */}
        <TelegramLinkRequiredDialog
          open={showLinkNotice && telegramLinked === false}
          onClose={() => setShowLinkNotice(false)}
          onGoToLink={() => { setShowLinkNotice(false); setRoute('settings'); }}
        />

        {!selectedChapterId ? (
          <div style={{ padding: '80px 20px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
            <Icon name="cards" size={28} color="var(--muted)" />
            <div style={{ marginTop: 12 }}>왼쪽에서 챕터를 선택하세요</div>
          </div>
        ) : !currentNote ? (
          <div style={{ padding: '80px 20px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
            <Icon name="note" size={28} color="var(--muted)" />
            {/* CURRICULUM_READY 과외는 아직 노트가 없다. 커리큘럼 확정 뒤 생긴다고 안내한다 */}
            {courses.find(c => c.id === effectiveCourseId)?.status === 'CURRICULUM_READY' ? (
              <div style={{ marginTop: 12 }}>
                <div style={{ fontWeight: 700, color: 'var(--ink-2)', marginBottom: 4 }}>노트가 아직 없어요</div>
                <div style={{ fontSize: 12, lineHeight: 1.6 }}>
                  커리큘럼을 확정하면 강의를 수강하면서 노트가 생성됩니다.
                </div>
              </div>
            ) : (
              <div style={{ marginTop: 12 }}>이 챕터의 노트가 없어요</div>
            )}
          </div>
        ) : (
          <MemoryNoteItem
            note={currentNote} palette={palette}
            onToggleStar={() => handleToggleStar(currentNote.id)}
            onSaveEdit={(content) => handleSaveEdit(currentNote.id, content)}
            onSendTelegram={() => handleSendTelegram(currentNote.id)}
            telegramSent={sentIds.has(currentNote.id)}
            onDelete={() => handleDeleteNote(currentNote.id)}
          />
        )}
      </main>
    </div>
  );
}

export default MemoryNotes;
