// 홈 Hero 블록. 현재 수업 이어하기, 오늘 미션, 과외 전환 셀렉터를 한곳에 둔다.
// 과외 전환 셀렉터(HeroCourseSelector)는 좌측 상단에 있어 바로 다른 과외로 바꿀 수 있다.
import Icon from '@/Icon';
import HeroCourseSelector from '@/components/student-home/HeroCourseSelector';
import type { ChapterSummaryResponse, CourseSummaryResponse, MissionItem } from '@/apis';
import { DRAFT_STATUSES } from '@/shared/constants/courseStatus';
import { formatRelativeTime } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import { isCourseFullyCompleted } from './homeCourseProgress';

interface HomeHeroProps {
  activeCourse: CourseSummaryResponse;
  // 현재 챕터(현재 수업). 있으면 챕터명이 제목이 되고, 없으면 코스명을 쓴다.
  currentChapter: ChapterSummaryResponse | null;
  missions: MissionItem[];
  setRoute: RouteSetter;
  // 과외 전환 상태와 핸들러. StudentHome이 관리한다.
  courses: CourseSummaryResponse[];
  activeCourseId: string | null | undefined;
  onCourseSelect: (courseId: string) => void;
  onContinueChapterSelect?: (chapterId: string) => void;
  // 현재 챕터의 슬라이드 진도(%). StudentHome이 completedSlides / slideCount × 100으로 계산한다.
  // Hero 제목이 현재 챕터라 진행 바도 챕터 기준으로 맞춘다.
  slideProgressPct: number;
  completedSlides: number;
  totalSlides: number;
}

function HomeHero({
  activeCourse,
  currentChapter,
  missions,
  setRoute,
  courses,
  activeCourseId,
  onCourseSelect,
  onContinueChapterSelect,
  slideProgressPct,
  completedSlides,
  totalSlides,
}: HomeHeroProps) {
  const heroTitle = currentChapter?.title ?? activeCourse.title;
  // 진행 바와 텍스트는 슬라이드 진행률(%)을 쓴다. NaN이면 0, 나머지는 0~100으로 자른다.
  const progressPct = Number.isFinite(slideProgressPct) ? Math.min(100, Math.max(0, slideProgressPct)) : 0;
  const safeTotalSlides = Math.max(0, totalSlides);
  const safeCompletedSlides = Math.min(Math.max(0, completedSlides), safeTotalSlides);
  const isDraftCourse = DRAFT_STATUSES.has(activeCourse.status);
  const isCurriculumGenerating = activeCourse.status === 'CURRICULUM_GENERATING';
  const isLearningComplete = !isDraftCourse && isCourseFullyCompleted(activeCourse) && !currentChapter;

  // 현재 수업 상태에 따라 학습실 또는 커리큘럼 확인 화면으로 분기한다
  const handleContinue = () => {
    if (isLearningComplete) return;
    onCourseSelect(activeCourse.id);
    if (!isDraftCourse && currentChapter?.id) {
      onContinueChapterSelect?.(currentChapter.id);
    }
    setRoute(isDraftCourse ? 'courseDetail' : 'learning');
  };
  const primaryButtonTitle = isLearningComplete
    ? '모든 강의를 완료했어요'
    : isCurriculumGenerating
      ? '생성 중인 과외 확인하기'
      : '현재 수업 바로 이어가기';
  const primaryButtonLabel = isLearningComplete
    ? '학습 완료'
    : isCurriculumGenerating
      ? '생성 중 · 확인하기'
      : '이어서 학습하기';

  return (
    <section className="sh-hero" aria-label="현재 수업 및 오늘 미션">
      {/* 좌측: 수업 정보 + CTA */}
      <div className="sh-hero__main">
        {/* 과외 전환 셀렉터와 새 과외 버튼을 Hero 맨 위 한 줄에 둔다 */}
        {courses.length > 0 ? (
          <HeroCourseSelector
            courses={courses}
            activeCourseId={activeCourseId}
            onCourseSelect={onCourseSelect}
            setRoute={setRoute}
          />
        ) : (
          // 전환할 과외가 없어도 '새 과외' 버튼은 맨 위에 보인다
          <div className="sh-hero__course-selector sh-hero__course-selector--empty">
            <button
              className="sh-hero__course-new"
              onClick={() => setRoute('explore')}
              title="새 과외 만들기"
            >
              <Icon name="plus" size={12} />
              새 과외
            </button>
          </div>
        )}

        {/* 상단 라벨 행. 제목이 챕터명이라 코스명은 여기에 따로 적는다 */}
        <div className="sh-hero__eyebrow-row">
          <span className="sh-hero__eyebrow">
            <Icon name="play" size={13} />
            현재 수업 · {activeCourse.title}
          </span>
        </div>

        <h1 className="sh-hero__title">{heroTitle}</h1>

        {/* 진행 텍스트. 슬라이드가 있으면 슬라이드 기준, 없으면(slideCount=0) 강의 기준이다 */}
        <p className="sh-hero__desc">
          {isCurriculumGenerating
            ? '커리큘럼을 생성하고 있어요.'
            : safeTotalSlides > 0
              ? `${safeCompletedSlides} / ${safeTotalSlides} 슬라이드 완료`
              : activeCourse.totalLessons > 0
                ? `${activeCourse.completedLessons} / ${activeCourse.totalLessons} 강의 완료`
                : '슬라이드 준비 중'}
        </p>

        <div className="sh-hero__progress-track" role="progressbar"
          aria-valuenow={progressPct} aria-valuemin={0} aria-valuemax={100}>
          <span className="sh-hero__progress-fill" style={{ width: `${progressPct}%` }} />
        </div>

        {activeCourse.lastStudiedAt && (
          <small className="sh-hero__last-studied">
            마지막 {formatRelativeTime(activeCourse.lastStudiedAt)}
          </small>
        )}

        <div className="sh-hero__actions">
          <button
            className="sh-hero__btn-primary"
            onClick={handleContinue}
            disabled={isLearningComplete}
            title={primaryButtonTitle}
          >
            <Icon name={isLearningComplete ? 'check' : isCurriculumGenerating ? 'clock' : 'play'} size={15} />
            {primaryButtonLabel}
          </button>
          {/* 복습하기는 아웃라인 스타일로 덜 눈에 띄게 한다 */}
          {!isCurriculumGenerating && (
            <button
              className="sh-hero__btn-secondary"
              onClick={() => setRoute('notes')}
              title="암기노트 먼저 복습하기"
            >
              복습하기
            </button>
          )}
        </div>
      </div>

      {/* 우측: 오늘 미션 목록 */}
      <div className="sh-hero__missions" aria-label="오늘 미션">
        <span className="sh-hero__missions-label">
          <Icon name="flag" size={13} />
          오늘 미션
        </span>
        {missions.length > 0 ? (
          <ul className="sh-hero__mission-list">
            {missions.map((m) => {
              const pct = m.target > 0 ? Math.round((m.current / m.target) * 100) : 0;
              return (
                <li key={m.id} className="sh-mission-item" data-done={m.isCompleted}>
                  <span className="sh-mission-item__icon" aria-hidden="true">
                    {m.isCompleted ? (
                      <Icon name="check" size={13} />
                    ) : (
                      <span className="sh-mission-item__circle" />
                    )}
                  </span>
                  <div className="sh-mission-item__body">
                    <span className="sh-mission-item__title">{m.title}</span>
                    <div className="sh-mission-item__bar-track">
                      <span
                        className="sh-mission-item__bar-fill"
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                    <small className="sh-mission-item__progress">
                      {m.current} / {m.target}
                    </small>
                  </div>
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="sh-hero__missions-empty">오늘 미션이 없어요</p>
        )}
      </div>
    </section>
  );
}

export default HomeHero;
