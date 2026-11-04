// 과제 탭: 과제 목록과 상태 칩
import { useState, useEffect } from 'react';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import { getAssignments } from '@/apis';
import type { AssignmentListResponse } from '@/apis';

type UIHomeworkStatus = 'done' | 'grading' | 'pending';

function mapAssignmentStatus(status: string): UIHomeworkStatus {
  const upper = status.toUpperCase();
  if (upper === 'GRADED' || upper === 'COMPLETED') return 'done';
  if (upper === 'SUBMITTED' || upper === 'GRADING') return 'grading';
  return 'pending';
}

interface LearningHomeworkTabProps {
  courseId: string;
  palette: Palette;
  setRoute: RouteSetter;
}

/** 과제 상태별 칩렛 색상 톤 */
interface ChipletTone {
  primary: string;
  wash: string;
  soft: string;
}

/** 과제 상태별 칩 색상. --coral-* / --honey-* 토큰을 쓴다 */
function resolveHomeworkTone(status: 'done' | 'grading' | 'pending', palette: Palette): ChipletTone {
  if (status === 'done') return { primary: palette.primary, wash: palette.wash, soft: palette.soft };
  if (status === 'grading') return {
    primary: 'var(--honey-accent)',
    wash: 'var(--honey-light)',
    soft: 'var(--honey-mid)',
  };
  return {
    primary: 'var(--coral-ink)',
    wash: 'var(--coral-wash)',
    soft: 'var(--coral-soft)',
  };
}

export function LearningHomeworkTab({ courseId, palette, setRoute }: LearningHomeworkTabProps) {
  const [assignments, setAssignments] = useState<AssignmentListResponse[]>([]);

  useEffect(() => {
    if (!courseId) return;
    void getAssignments(courseId).then((res) => {
      if (res.data) setAssignments(res.data);
    }).catch(() => {});
  }, [courseId]);

  return (
    <div className="scroll" style={{ flex: 1, overflowY: 'auto', padding: '14px' }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-2)' }}>
        이 과외 과제 <span style={{ color: 'var(--ink-3)' }}>· {assignments.length}</span>
      </div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2, marginBottom: 12 }}>
        챕터가 끝날 때마다 과제를 만들어 텔레그램으로 보내드려요
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {assignments.map(h => {
          const uiStatus = mapAssignmentStatus(h.status);
          const tone = resolveHomeworkTone(uiStatus, palette);
          return (
            <button
              key={h.id}
              onClick={() => setRoute('myHomework')}
              className="lift"
              style={{
                padding: '11px 12px',
                borderRadius: 10,
                background: 'var(--bg)',
                border: '1px solid var(--line)',
                textAlign: 'left',
                display: 'flex',
                flexDirection: 'column',
                gap: 6,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{
                  fontSize: 12,
                  padding: '2px 7px',
                  borderRadius: 10,
                  fontWeight: 800,
                  background: tone.wash,
                  color: tone.primary,
                  border: `1px solid ${tone.soft}`,
                }}>
                  {uiStatus === 'done' ? '채점 완료' : uiStatus === 'grading' ? '채점 중' : '제출 대기'}
                </span>
              </div>

              <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink)' }}>{h.title}</div>

              {/* 마감 기한은 제출 대기 과제만 보여 준다 */}
              {uiStatus === 'pending' && h.dueDate && (
                <div style={{ fontSize: 12, color: 'var(--coral-ink)' }}>마감 {h.dueDate}</div>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
