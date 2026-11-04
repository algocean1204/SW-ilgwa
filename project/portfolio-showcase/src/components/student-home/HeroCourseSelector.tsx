// Hero 상단의 과외 전환 셀렉터와 새 과외 만들기 버튼.
// 상태(activeCourseId, onCourseSelect)는 StudentHome이 넘긴다.
import Icon from '@/Icon';
import type { CourseSummaryResponse } from '@/apis';
import type { RouteSetter } from '@/shared/types';

interface HeroCourseSelectorProps {
  courses: CourseSummaryResponse[];
  activeCourseId: string | null | undefined;
  onCourseSelect: (courseId: string) => void;
  setRoute: RouteSetter;
}

function HeroCourseSelector({ courses, activeCourseId, onCourseSelect, setRoute }: HeroCourseSelectorProps) {
  const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const selected = e.target.value;
    if (selected) onCourseSelect(selected);
  };

  return (
    <div className="sh-hero__course-selector">
      {/* 과외 전환 드롭다운. 접근성을 위해 네이티브 select를 쓴다 */}
      <div className="sh-hero__course-select-wrap">
        <Icon name="cards" size={13} />
        <select
          className="sh-hero__course-select"
          value={activeCourseId ?? ''}
          onChange={handleChange}
          aria-label="과외 전환"
          title="학습할 과외를 선택하세요"
        >
          {courses.map((c) => (
            <option key={c.id} value={c.id}>
              {c.title}
            </option>
          ))}
        </select>
      </div>

      {/* 새 과외 만들기. 드롭다운과 같은 줄 오른쪽 끝에 둔다 */}
      <button
        className="sh-hero__course-new"
        onClick={() => setRoute('explore')}
        title="새 과외 만들기"
      >
        <Icon name="plus" size={12} />
        새 과외
      </button>
    </div>
  );
}

export default HeroCourseSelector;
