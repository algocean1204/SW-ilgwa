// "남은 과제 확인" 체크리스트 위젯. 활성 과외의 미완료 과제를 최대 3개까지 보여준다.
import { useState, useEffect } from 'react';
import Icon from '@/Icon';
import { getAssignments } from '@/apis';
import type { AssignmentListResponse } from '@/apis';
import type { RouteSetter } from '@/shared/types';

// 체크리스트에 노출할 최대 과제 수
const MAX_VISIBLE = 3;

// 마감일을 "n월 n일"로 짧게 쓴다
function formatDueShort(raw: string): string {
  const d = new Date(raw);
  if (isNaN(d.getTime())) return raw;
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

interface HomeAssignmentChecklistProps {
  activeCourseId: string | null | undefined;
  setRoute: RouteSetter;
  onCourseSelect?: (courseId: string) => void;
}

function HomeAssignmentChecklist({ activeCourseId, setRoute, onCourseSelect }: HomeAssignmentChecklistProps) {
  const [pending, setPending] = useState<AssignmentListResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!activeCourseId) {
      void Promise.resolve().then(() => {
        setPending([]);
        setIsLoading(false);
      });
      return;
    }
    void Promise.resolve().then(() => setIsLoading(true));
    void getAssignments(activeCourseId).then((res) => {
      const list = Array.isArray(res.data) ? res.data : [];
      setPending(list.filter((a) => a.status !== 'completed'));
    }).catch((err) => {
      console.warn('과제 목록 조회 실패:', err);
      setPending([]);
    }).finally(() => {
      setIsLoading(false);
    });
  }, [activeCourseId]);

  const visible = pending.slice(0, MAX_VISIBLE);
  const openHomework = () => {
    if (activeCourseId) onCourseSelect?.(activeCourseId);
    setRoute('myHomework');
  };

  return (
    <section className="sh-split-card" aria-label="남은 과제 확인">
      <div className="sh-split-card__header">
        <span className="sh-split-card__title">
          <Icon name="note" size={15} />
          남은 과제 확인
        </span>
        <button
          className="sh-split-card__link"
          onClick={openHomework}
          title="과제 전체 보기"
        >
          전체 보기
        </button>
      </div>

      {isLoading ? (
        <p className="sh-split-card__empty">과제를 불러오는 중...</p>
      ) : visible.length === 0 ? (
        <p className="sh-split-card__empty">남은 과제가 없어요. 잘하고 있어요! 🎉</p>
      ) : (
        <ul className="sh-assignment-checklist">
          {visible.map((a) => (
            // TODO: 과제 상세/제출 전용 라우트가 생기면 아래 setRoute('myHomework')를
            // setRoute('assignmentDetail') + 파라미터 전달 방식으로 교체한다.
            // 현재는 개별 과제 상세 라우트가 없으므로 과제 목록으로 이동한다.
            <li key={a.id} className="sh-assignment-checklist__item">
              <button
                className="sh-assignment-checklist__item-btn"
                onClick={openHomework}
                title={`${a.title} — 과제 목록으로 이동`}
              >
                <span className="sh-assignment-checklist__icon" aria-hidden="true">
                  <span className="sh-assignment-checklist__circle" />
                </span>
                <span className="sh-assignment-checklist__title">{a.title}</span>
                {a.dueDate && (
                  <small className="sh-assignment-checklist__due">{formatDueShort(a.dueDate)}</small>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export default HomeAssignmentChecklist;
