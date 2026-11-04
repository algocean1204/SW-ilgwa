// 260px 사이드바용 과제 카드 목록.
// PageContextSidebar에서 route === 'myHomework'일 때 그린다.
// 선택 상태는 appState.selectedHomeworkId / setSelectedHomeworkId를 써서
// MyHomework 상세 패널과 맞춘다.
// 과제 목록은 appState.activeCourseId 기준으로 직접 조회한다(GlobalSidebar와 같은 방식).
import { useState, useEffect } from 'react';
import type { Palette } from '@/lib';
import type { AppState } from '@/shared/types';
import { getAssignments } from '@/apis';
import type { AssignmentListResponse } from '@/apis';
import StatusBadge from '@/components/ui/StatusBadge';

// 백엔드 Assignment status → 표시 레이블·톤 인라인 매핑
// active → 제출 대기(danger), completed → 완료(success)
const STATUS_DISPLAY: Record<string, { label: string; tone: 'success' | 'warning' | 'danger' }> = {
  active:    { label: '제출 대기', tone: 'danger'  },
  completed: { label: '완료',     tone: 'success' },
};

/** 상태 문자열로 표시 정보를 찾는다. 모르는 상태는 danger로 둔다 */
function getStatusDisplay(status: string) {
  return STATUS_DISPLAY[status] ?? { label: status, tone: 'danger' as const };
}

interface HomeworkSidebarListProps {
  palette: Palette;
  appState: AppState;
}

export default function HomeworkSidebarList({ palette, appState }: HomeworkSidebarListProps) {
  const [assignments, setAssignments] = useState<AssignmentListResponse[]>([]);
  const selectedId = appState.selectedHomeworkId;

  // activeCourseId가 바뀌면 그 과외의 과제 목록을 다시 불러온다
  const courseId = appState.activeCourseId;
  const visibleAssignments = courseId ? assignments : [];
  useEffect(() => {
    if (!courseId) return;
    void getAssignments(courseId)
      .then(res => {
        setAssignments(res.data ?? []);
      })
      .catch(() => {
        setAssignments([]);
      });
  }, [courseId]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: '0 6px 12px' }}>
      {visibleAssignments.length === 0 && (
        <div style={{ padding: '20px 8px', fontSize: 12, color: 'var(--ink-3)', textAlign: 'center' }}>
          과제가 없어요
        </div>
      )}
      {visibleAssignments.map(h => {
        const active = h.id === selectedId;
        const statusInfo = getStatusDisplay(h.status);
        return (
          <button
            key={h.id}
            onClick={() => appState.setSelectedHomeworkId(h.id)}
            className="lift"
            style={{
              // 활성 카드는 palette.primary 테두리로 표시한다
              padding: '10px 12px', borderRadius: 10,
              background: active ? 'var(--surface)' : 'var(--paper)',
              border: active ? `2px solid ${palette.primary}` : '1px solid var(--line)',
              textAlign: 'left', display: 'flex', flexDirection: 'column', gap: 6,
              boxShadow: active ? 'var(--shadow-md)' : 'none',
              width: '100%',
            }}
          >
            {/* 상태 배지 */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <StatusBadge tone={statusInfo.tone} label={statusInfo.label} size="sm" />
            </div>

            {/* 과제 제목. 폭이 좁아 두 줄까지 보여준다 */}
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink)', letterSpacing: '-.01em', lineHeight: 1.4 }}>
              {h.title}
            </div>

            {/* 마감일. active 과제에만 보인다 */}
            {h.dueDate && h.status === 'active' && (
              <div style={{ fontSize: 11, color: 'var(--coral-primary)', fontWeight: 700 }}>
                마감 {h.dueDate}
              </div>
            )}
          </button>
        );
      })}
    </div>
  );
}
