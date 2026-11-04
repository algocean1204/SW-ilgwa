// 과외·챕터 아코디언 트리 사이드바
import { useState, useEffect } from 'react';
import type { Palette } from '@/lib';
import Icon from '@/Icon';
import { getCurriculum } from '@/apis';
import type { CourseSummaryResponse, ChapterSummaryResponse } from '@/apis';

interface MemoryNotesSidebarProps {
  courses: CourseSummaryResponse[];
  palette: Palette;
  selectedChapterId: string | null;
  onSelectChapter: (courseId: string, chapterId: string) => void;
  noteCountByChapter: Record<string, number>;
}

export function MemoryNotesSidebar({
  courses, palette, selectedChapterId, onSelectChapter, noteCountByChapter,
}: MemoryNotesSidebarProps) {
  const [expandedCourseId, setExpandedCourseId] = useState<string | null>(null);
  const [chaptersMap, setChaptersMap] = useState<Record<string, ChapterSummaryResponse[]>>({});

  const loadAndExpand = (courseId: string) => {
    if (expandedCourseId === courseId) { setExpandedCourseId(null); return; }
    setExpandedCourseId(courseId);
    if (!chaptersMap[courseId]) {
      void getCurriculum(courseId).then((res) => {
        if (res.data?.chapters) {
          setChaptersMap(prev => ({ ...prev, [courseId]: res.data!.chapters }));
        }
      }).catch(() => {});
    }
  };

  useEffect(() => {
    const firstId = courses[0]?.id;
    if (!firstId || expandedCourseId) return;
    void getCurriculum(firstId).then((res) => {
      setExpandedCourseId(firstId);
      if (res.data?.chapters) {
        setChaptersMap(prev => ({ ...prev, [firstId]: res.data!.chapters }));
      }
    }).catch(() => {
      setExpandedCourseId(firstId);
    });
  }, [courses, expandedCourseId]);

  return (
    <aside className="scroll" style={{
      width: 230, flexShrink: 0, marginRight: 16,
      position: 'sticky', top: 74,
      maxHeight: 'calc(100vh - 90px)', overflowY: 'auto',
      background: 'var(--paper)', borderRadius: 14,
      border: '1px solid var(--line)', padding: 8,
    }}>
      <div style={{
        fontSize: 10, fontWeight: 700, letterSpacing: '.1em',
        color: 'var(--ink-3)', textTransform: 'uppercase', padding: '6px 8px 2px',
      }}>커리큘럼</div>

      {courses.map(c => {
        const expanded = expandedCourseId === c.id;
        const chapters = chaptersMap[c.id] ?? [];
        return (
          <div key={c.id}>
            <button onClick={() => loadAndExpand(c.id)} style={{
              width: '100%', textAlign: 'left', padding: '10px 10px', borderRadius: 9,
              background: expanded ? palette.wash : 'transparent',
              color: expanded ? palette.primary : 'var(--ink-2)',
              fontSize: 12.5, fontWeight: expanded ? 800 : 600,
              display: 'flex', alignItems: 'center', gap: 6,
            }}>
              <Icon name="arrowRight" size={10}
                style={{ transform: expanded ? 'rotate(90deg)' : 'none', transition: 'transform .15s ease' }} />
              <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {c.title}
              </span>
            </button>
            {expanded && chapters.map(ch => {
              const active = selectedChapterId === ch.id;
              const count = noteCountByChapter[ch.id] ?? 0;
              return (
                <button key={ch.id}
                  onClick={() => onSelectChapter(c.id, ch.id)}
                  style={{
                    width: '100%', textAlign: 'left',
                    padding: '7px 10px 7px 28px', borderRadius: 7,
                    background: active ? `${palette.primary}12` : 'transparent',
                    color: active ? palette.primary : 'var(--ink-3)',
                    fontSize: 12, fontWeight: active ? 700 : 500,
                    display: 'flex', alignItems: 'center', gap: 6,
                  }}>
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {ch.title}
                  </span>
                  {count > 0 && (
                    <span style={{ fontSize: 10, fontWeight: 700, color: active ? palette.primary : 'var(--ink-3)' }}>
                      {count}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        );
      })}
    </aside>
  );
}
