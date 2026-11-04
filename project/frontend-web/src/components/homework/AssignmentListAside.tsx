// '내 과제' 좌측 과제 목록 패널. 과제 선택, 상태, 마감일을 보여준다.
// MyHomework에서 목록 패널만 따로 뺐다.
import type { AssignmentListResponse, CourseSummaryResponse } from '@/apis';
import type { Palette } from '@/lib';

function formatDueDate(raw: string): string {
  const d = new Date(raw);
  if (isNaN(d.getTime())) return raw;
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  const month = d.getMonth() + 1;
  const date = d.getDate();
  const day = days[d.getDay()];
  const diffMs = d.getTime() - Date.now();
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
  const rel = diffDays < 0 ? `${Math.abs(diffDays)}일 지남` : diffDays === 0 ? '오늘 마감' : `${diffDays}일 후`;
  return `${month}월 ${date}일 (${day}) · ${rel}`;
}

// 과제 상태 레이블과 색상. completed가 아니면 모두 "제출 대기"다.
const statusMap: Record<string, { label: string; color: string }> = { completed: { label: '완료', color: 'var(--grass-primary)' } };
const getStatusDisplay = (s: string) => statusMap[s] ?? { label: '제출 대기', color: 'var(--coral-primary)' };

interface AssignmentListAsideProps {
  assignments: AssignmentListResponse[];
  selectedId?: string;
  activeCourseId: string;
  courses: CourseSummaryResponse[];
  palette: Palette;
  onSelect: (id: string) => void;
}

/**
 * 과제 목록을 챕터(강의) 순서로 정렬한다.
 * 백엔드는 Assignment.dueDate ASC 순으로 과제를 생성하므로 dueDate가 챕터 순서를 반영한다.
 * dueDate가 null인 항목은 createdAt 오름차순을 폴백으로 사용하고, 동순위는 기존 순서를 유지한다(안정 정렬).
 */
function sortByChapterOrder(list: AssignmentListResponse[]): AssignmentListResponse[] {
  return [...list].sort((a, b) => {
    const aTime = a.dueDate ? new Date(a.dueDate).getTime() : new Date(a.createdAt).getTime();
    const bTime = b.dueDate ? new Date(b.dueDate).getTime() : new Date(b.createdAt).getTime();
    return aTime - bTime;
  });
}

function AssignmentListAside({ assignments, selectedId, activeCourseId, courses, palette, onSelect }: AssignmentListAsideProps) {
  const sortedAssignments = sortByChapterOrder(assignments);

  return (
    <aside className="myhw-aside" style={{ maxHeight: 'calc(100vh - 140px)', overflowY: 'auto' }}>
      <div className="myhw-aside__label">과제</div>
      {assignments.length === 0 && (() => {
        // 미확정(CURRICULUM_READY) 과외는 과제가 아직 없으므로 확정 후 생긴다고 안내한다
        const activeCourseStatus = courses.find(c => c.id === activeCourseId)?.status;
        return activeCourseStatus === 'CURRICULUM_READY' ? (
          <div style={{ padding: '16px 12px', fontSize: 12, color: 'var(--ink-3)', textAlign: 'center', lineHeight: 1.6 }}>
            <div style={{ fontWeight: 700, color: 'var(--ink-2)', marginBottom: 4 }}>과제가 아직 없어요</div>
            커리큘럼을 확정하면 과제가 생성됩니다.
          </div>
        ) : (
          <div style={{ padding: '20px 12px', fontSize: 12, color: 'var(--ink-3)', textAlign: 'center' }}>과제가 없어요</div>
        );
      })()}
      {sortedAssignments.map(h => {
        const active = h.id === (selectedId ?? '');
        const { label: statusLabel, color: statusColor } = getStatusDisplay(h.status);
        return (
          <button key={h.id} onClick={() => onSelect(h.id)} style={{
            width: '100%', textAlign: 'left', padding: '10px 12px', borderRadius: 9,
            background: active ? palette.wash : 'transparent',
            color: active ? palette.primary : 'var(--ink-2)',
            borderLeft: active ? `2px solid ${palette.primary}` : '2px solid transparent',
            display: 'flex', flexDirection: 'column', gap: 4,
          }}>
            <span style={{ fontSize: 12, fontWeight: active ? 800 : 600, lineHeight: 1.4, color: active ? palette.primary : 'var(--ink)' }}>{h.title}</span>
            <span style={{ fontSize: 10, fontWeight: 700, color: statusColor }}>{statusLabel}</span>
            {h.dueDate && h.status !== 'completed' && (
              <span style={{ fontSize: 10, color: 'var(--coral-primary)', fontWeight: 600 }}>마감 {formatDueDate(h.dueDate)}</span>
            )}
          </button>
        );
      })}
    </aside>
  );
}

export default AssignmentListAside;
