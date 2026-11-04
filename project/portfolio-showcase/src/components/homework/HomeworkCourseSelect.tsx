// '내 과제' 상단 과외 전환 드롭다운.
// 접근성을 위해 HeroCourseSelector처럼 네이티브 select를 쓴다.
import Icon from '@/Icon';
import type { CourseSummaryResponse } from '@/apis';

interface HomeworkCourseSelectProps {
  courses: CourseSummaryResponse[];
  activeCourseId: string;
  assignmentCount: number;
  onCourseSelect: (courseId: string) => void;
}

function HomeworkCourseSelect({ courses, activeCourseId, assignmentCount, onCourseSelect }: HomeworkCourseSelectProps) {
  if (courses.length === 0) {
    return (
      <div className="myhw-course-select-wrap myhw-course-select-wrap--empty">
        수강 중인 과외가 없어요
      </div>
    );
  }

  const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const selected = e.target.value;
    if (selected) onCourseSelect(selected);
  };

  return (
    <div className="myhw-course-select-wrap">
      <Icon name="cards" size={13} />
      <select
        className="myhw-course-select"
        value={activeCourseId}
        onChange={handleChange}
        aria-label="과외 전환"
        title="과제를 확인할 과외를 선택하세요"
      >
        {courses.map((c) => (
          <option key={c.id} value={c.id}>
            {c.title}
          </option>
        ))}
      </select>
      {/* 선택된 과외의 과제 수 */}
      {assignmentCount > 0 && (
        <span className="myhw-course-select__badge">{assignmentCount}개</span>
      )}
    </div>
  );
}

export default HomeworkCourseSelect;
