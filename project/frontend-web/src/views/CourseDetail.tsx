// 커리큘럼 초안을 확인, 조정, 확정하는 페이지
import { useTweaks, PALETTES } from '@/lib';
import type { AppState, RouteSetter } from '@/shared/types';
import { Breadcrumb }   from '@/components/course-detail/Breadcrumb';
import { PhaseBanner }  from '@/components/course-detail/PhaseBanner';
import { CourseHero }   from '@/components/course-detail/CourseHero';
import { ChapterList }  from '@/components/course-detail/ChapterList';
import { ConfirmBar }   from '@/components/course-detail/ConfirmBar';
import { CourseLoadErrorView, CurriculumFailedView, CurriculumGeneratingView } from '@/components/course-detail/CourseDetailStateViews';
import { LessonGenerationBanner } from '@/components/course-detail/LessonGenerationBanner';
import { useCourseDetailController } from '@/components/course-detail/useCourseDetailController';
import { useConfirmedLessonProgress } from '@/components/course-detail/useConfirmedLessonProgress';
import type { Chapter } from '@/components/course-detail/types';

interface CourseDetailProps {
  setRoute: RouteSetter;
  appState: AppState;
}


function CourseDetail({ setRoute, appState }: CourseDetailProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;
  const courseId = appState.activeCourseId;
  const {
    phase, courseStatus, sourceType, title, setTitle, displayTutorName,
    editingTitle, setEditingTitle, chapters, loaded, loadedCourseId,
    generating, generationTimedOut, generationNotice, curriculumFailed, loadError,
    totalMin, totalSlides, readOnly, removeChapter, moveChapterUp, moveChapterDown,
    handleManualPoll, handleTitleSave, handleConfirm,
  } = useCourseDetailController({
    courseId,
    setActiveCourseId: appState.setActiveCourseId,
  });

  // 확정(readOnly) 뒤 강의 생성 진행을 폴링한다. 배너와 챕터 배지가 같은 결과를 쓴다.
  // 훅 규칙 때문에 early return 위에서 항상 호출하고, 폴링 여부는 enabled로 정한다.
  const lessonProgress = useConfirmedLessonProgress({
    courseId,
    enabled: readOnly && !!courseId,
  });
  // 아직 LOCKED인 챕터 id. ChapterList "생성중" 배지에 넘긴다.
  const generatingChapterIds = new Set(
    lessonProgress.chapters.filter(c => c.status === 'LOCKED').map(c => c.id),
  );

  const handleChapterClick = (ch: Chapter) => {
    if (ch.status === 'locked') return;
    appState.setSelectedChapterId(ch.id);
    setRoute("learning");
  };

  const currentLoadError = courseId
    ? loadError
    : '선택된 과외가 없습니다. 새 과외를 먼저 만들어 주세요.';

  if (courseId && (!loaded || loadedCourseId !== courseId)) {
    return (
      <div style={{ padding: 40, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
        커리큘럼을 불러오는 중...
      </div>
    );
  }

  if (currentLoadError) {
    return (
      <CourseLoadErrorView
        palette={palette}
        message={currentLoadError}
        setRoute={setRoute}
      />
    );
  }

  // CURRICULUM_FAILED면 실패 안내 화면을 보인다. 재시도는 handleManualPoll로 상태를 다시 조회한다.
  if (curriculumFailed) {
    return (
      <CurriculumFailedView
        palette={palette}
        onRetry={() => void handleManualPoll()}
        setRoute={setRoute}
      />
    );
  }

  if (generating) {
    return (
      <CurriculumGeneratingView
        palette={palette}
        generationNotice={generationNotice}
        generationTimedOut={generationTimedOut}
        onManualPoll={() => void handleManualPoll()}
        setRoute={setRoute}
        sourceType={sourceType}
        courseId={courseId ?? ''}
      />
    );
  }

  return (
    <div style={{
      maxWidth: "var(--page-max-w)", margin: "0 auto",
      padding: "var(--page-py) var(--page-px) 56px",
    }}>
      {/* 탐색 경로. sourceType으로 PDF와 주제를 나눈다 */}
      <Breadcrumb setRoute={setRoute} sourceType={sourceType} />

      {/* draft, confirmed 단계 안내 배너 */}
      <PhaseBanner phase={phase} palette={palette} setRoute={setRoute} readOnly={readOnly} sourceType={sourceType} />

      {/* 확정 직후 강의 생성 진행 배너. 미완 챕터가 있을 때만 보인다 */}
      <LessonGenerationBanner
        palette={palette}
        readOnly={readOnly}
        courseStatus={courseStatus}
        progress={lessonProgress}
      />

      {/* 히어로 카드(전체 폭) */}
      <section
        id="title-edit"
        style={{ marginBottom: 24 }}
      >
        <div id="curriculum-review">
          <CourseHero
            palette={palette}
            title={title}
            setTitle={setTitle}
            editingTitle={editingTitle}
            setEditingTitle={setEditingTitle}
            onTitleSave={handleTitleSave}
            chapterCount={chapters.length}
            totalSlides={totalSlides}
            totalMin={totalMin}
            readOnly={readOnly}
            tutorName={displayTutorName}
            sourceType={sourceType}
          />
        </div>
      </section>

      {/* 커리큘럼 챕터 목록 */}
      <ChapterList
        palette={palette}
        chapters={chapters}
        removeChapter={removeChapter}
        moveChapterUp={moveChapterUp}
        moveChapterDown={moveChapterDown}
        readOnly={readOnly}
        onChapterClick={readOnly ? handleChapterClick : undefined}
        generatingChapterIds={readOnly ? generatingChapterIds : undefined}
      />

      {/* 하단 확정 바는 draft 단계에서만 보인다 */}
      {phase === "draft" && (
        <div id="confirm">
          <ConfirmBar
            palette={palette}
            chapterCount={chapters.length}
            totalMin={totalMin}
            totalSlides={totalSlides}
            onConfirm={handleConfirm}
            setRoute={setRoute}
          />
        </div>
      )}
    </div>
  );
}

export default CourseDetail;
