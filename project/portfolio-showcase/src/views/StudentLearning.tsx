// 학습 세션 화면. 챕터 선택, 채팅, 음성 모드, 우측 패널을 묶는다.
import { useState, useEffect, useCallback, type CSSProperties } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import { ApiError, getCourse as fetchCourse, getChapters, getMyCourses } from '@/apis';
import type { ChapterSummaryResponse, CourseStatus } from '@/apis';
import { useLessonGenerationGuard } from '@/components/student-learning/useLessonGenerationGuard';
import type { Course } from '@/shared/types';
import type { AppState, RouteSetter } from '@/shared/types';
import { useVAD } from '@/shared/audio';
import type { VADEvent } from '@/shared/audio';
import { LearningHeader } from '@/components/student-learning/LearningHeader';
import { LearningContent } from '@/components/student-learning/LearningContent';
import type { LearningSlideProgress } from '@/components/student-learning/LearningContent';
import { LearningRightPanel } from '@/components/student-learning/LearningRightPanel';
import { useLearningChat } from '@/components/student-learning/useLearningChat';
import type { SlideContext } from '@/components/student-learning/useLearningChat';
import { LearningNotReady } from '@/components/student-learning/LearningNotReady';
import { LessonGeneratingProgress } from '@/components/student-learning/LessonGeneratingProgress';
import { resolveActiveChapterId } from '@/components/student-learning/learningChapterSelection';

/** 이 상태의 과외로 학습실에 들어오면 안내 화면을 보인다 */
const UNLEARNABLE_STATUSES: CourseStatus[] = [
  'CURRICULUM_GENERATING',
  'CURRICULUM_READY',
  'CURRICULUM_FAILED',
  'PAUSED',
  'DELETED',
];

// 하위 컴포넌트가 쓰는 Course 타입 기본값. API 응답을 여기에 매핑한다.
const DEFAULT_COURSE: Course = {
  id: '', title: '', sub: '', tutor: '', tutorIcon: 'owl',
  color: '#ADD87B', accent: '#207B4C', progress: 0, dDay: 0,
  chapters: 0, slidesDone: 0, slidesTotal: 0,
  homework: { pending: 0, grading: 0, done: 0 }, lastAt: '',
};

interface ChapterState {
  courseId: string;
  chapters: ChapterSummaryResponse[];
  activeChapterId: string;
}

interface SlideProgressState extends LearningSlideProgress {
  courseId: string;
  chapterId: string;
}

interface StudentLearningProps {
  setRoute: RouteSetter;
  appState: AppState;
}

function StudentLearning({ setRoute, appState }: StudentLearningProps) {
  const {
    activeCourseId: courseId,
    selectedChapterId,
    setActiveCourseId,
    setSelectedChapterId,
  } = appState;
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;

  const [course, setCourse] = useState<Course>(DEFAULT_COURSE);
  // 과외 상태. 학습 불가 상태면 진입을 막는 데 쓴다.
  const [courseStatus, setCourseStatus] = useState<CourseStatus | null>(null);
  const [chapterState, setChapterState] = useState<ChapterState>({
    courseId: '',
    chapters: [],
    activeChapterId: '',
  });
  const [slideProgress, setSlideProgress] = useState<SlideProgressState | null>(null);

  // activeCourseId 없이 직접 들어온 경우 여기서 과외를 정한다
  useEffect(() => {
    if (courseId) return;
    void getMyCourses({ page: 0, size: 1 }).then((res) => {
      const first = res.data?.content?.[0];
      if (first) setActiveCourseId(first.id);
    }).catch((err) => {
      console.warn('[StudentLearning] activeCourseId 자동 해소 실패:', err);
    });
  }, [courseId, setActiveCourseId]);

  const visibleCourse = course.id === courseId ? course : DEFAULT_COURSE;
  const chapters = chapterState.courseId === courseId ? chapterState.chapters : [];
  const activeChapterId = chapterState.courseId === courseId ? chapterState.activeChapterId : '';
  const activeChapter = chapters.find(c => c.id === activeChapterId);
  const visibleSlideProgress =
    slideProgress?.courseId === courseId && slideProgress.chapterId === activeChapterId
      ? slideProgress
      : null;
  const completedSlides = visibleSlideProgress?.completedSlides ?? activeChapter?.completedSlides ?? 0;
  const totalSlides = visibleSlideProgress?.totalSlides ?? activeChapter?.slideCount ?? 0;
  const chapterLabel = activeChapter
    ? `${activeChapter.order}강 · ${activeChapter.title}`
    : '수업 준비 중';
  const lessonTitle = visibleSlideProgress?.slideTitle ?? activeChapter?.title ?? visibleCourse.title ?? '수업 준비 중';

  useEffect(() => {
    if (!courseId) {
      void Promise.resolve().then(() => {
        setCourse(DEFAULT_COURSE);
        setCourseStatus(null);
      });
      return;
    }
    void fetchCourse(courseId).then((res) => {
      if (res.data) {
        const d = res.data;
        setCourseStatus(d.status);
        setCourse({
          ...DEFAULT_COURSE,
          id: d.id,
          title: d.title,
          sub: d.subject,
          tutor: d.tutorName,
          progress: Math.round(d.progress * 100),
          slidesDone: d.completedLessons,
          slidesTotal: d.totalLessons,
          chapters: d.totalLessons,
        });
      }
    }).catch((err) => {
      if (err instanceof ApiError && err.status === 404) {
        setCourse(DEFAULT_COURSE);
        setCourseStatus(null);
        setChapterState({ courseId: '', chapters: [], activeChapterId: '' });
        setActiveCourseId('');
      }
    });
  }, [courseId, setActiveCourseId]);

  // 챕터 목록을 불러와 선택된 챕터를, 없으면 첫 IN_PROGRESS나 AVAILABLE 챕터를 고른다
  useEffect(() => {
    if (!courseId) {
      void Promise.resolve().then(() => {
        setChapterState({ courseId: '', chapters: [], activeChapterId: '' });
      });
      return;
    }
    let cancelled = false;
    void getChapters(courseId).then((res) => {
      if (!cancelled && res.data) {
        const chapterList = res.data;
        setChapterState(prev => ({
          courseId,
          chapters: chapterList,
          activeChapterId: resolveActiveChapterId(
            chapterList,
            selectedChapterId,
            prev.courseId === courseId ? prev.activeChapterId : '',
          ),
        }));
        if (selectedChapterId) setSelectedChapterId('');
      }
    }).catch((err) => {
      if (cancelled) return;
      if (err instanceof ApiError && err.status === 404) {
        setChapterState({ courseId: '', chapters: [], activeChapterId: '' });
        setActiveCourseId('');
      }
    });
    return () => {
      cancelled = true;
    };
  }, [courseId, selectedChapterId, setActiveCourseId, setSelectedChapterId]);

  const handleSlideProgressChange = useCallback((progress: LearningSlideProgress | null) => {
    setSlideProgress(progress ? { ...progress, courseId, chapterId: activeChapterId } : null);
  }, [activeChapterId, courseId]);

  const handleChapterSelect = useCallback((chapterId: string) => {
    setSlideProgress(null);
    setChapterState(prev => {
      if (prev.courseId !== courseId) return prev;
      const selected = prev.chapters.find(ch => ch.id === chapterId && ch.status !== 'LOCKED');
      return selected ? { ...prev, activeChapterId: selected.id } : prev;
    });
  }, [courseId]);

  // 챕터를 마치면 목록을 다시 불러와 백엔드가 AVAILABLE로 바꾼 다음 챕터를 반영한다
  const handleChapterComplete = useCallback(() => {
    if (!courseId) return;
    void getChapters(courseId).then((res) => {
      const chapters = res.data;
      if (!chapters) return;
      setChapterState(prev => ({
        ...prev,
        chapters,
      }));
    }).catch(() => {});
  }, [courseId]);

  // 현재보다 order가 큰 AVAILABLE, IN_PROGRESS 챕터로 이동한다
  const handleNextChapter = useCallback(() => {
    if (!courseId) return;
    void getChapters(courseId).then((res) => {
      const chapters = res.data;
      if (!chapters) return;
      const currentOrder = chapterState.chapters.find(c => c.id === chapterState.activeChapterId)?.order ?? 0;
      const next = chapters
        .filter(c => c.order > currentOrder && c.status !== 'LOCKED')
        .sort((a, b) => a.order - b.order)[0];
      if (next) {
        setChapterState({ courseId, chapters, activeChapterId: next.id });
      } else {
        // 다음 챕터가 아직 생성 중이면 목록만 갱신한다
        setChapterState(prev => ({ ...prev, chapters }));
      }
    }).catch(() => {});
  }, [courseId, chapterState.activeChapterId, chapterState.chapters]);

  // 우측 패널 탭: 튜터, 커리큘럼, 과제
  const [rightTab, setRightTab] = useState('chat');
  // 집중 모드면 우측 패널을 숨긴다
  const [focusMode, setFocusMode] = useState(false);
  // 슬라이드를 접으면 아키텍처 카드와 코드 블록을 숨긴다
  const [slidesCollapsed, setSlidesCollapsed] = useState(false);
  const pageSubView = appState.pageSubView;
  const setPageSubView = appState.setPageSubView;
  const contextRightTab =
    pageSubView === 'learning-chat' ? 'chat'
      : pageSubView === 'learning-curriculum' ? 'curriculum'
        : pageSubView === 'learning-note' ? 'note'
          : null;
  const effectiveRightTab = contextRightTab ?? rightTab;
  const effectiveFocusMode = focusMode || pageSubView === 'learning-focus';

  // 현재 슬라이드 정보를 채팅 훅에 넘긴다. lessonId(chapterId), slideId, slideIdx,
  // slideTitle, slideContent를 담아 백엔드가 지금 보는 슬라이드를 프롬프트에 넣게 한다.
  const slideContext: SlideContext | undefined = visibleSlideProgress
    ? {
        lessonId: activeChapterId,
        slideId: visibleSlideProgress.slideId,
        slideIdx: visibleSlideProgress.slideIndex,
        slideTitle: visibleSlideProgress.slideTitle,
        slideContent: visibleSlideProgress.slideContent,
      }
    : undefined;

  // 채팅 상태: 메시지 목록, 타이핑, 입력, 전송, 재시도, 스크롤
  const { chat, typing, input, setInput, send, retry, scrollRef } = useLearningChat(courseId, effectiveRightTab, slideContext);

  const [voiceMode, setVoiceMode] = useState(false);

  // 마이크 입력에서 발화를 감지하는 VAD
  const vad = useVAD();

  const handleRightTabChange = useCallback((tab: string) => {
    setRightTab(tab);
    if (pageSubView?.startsWith('learning-')) {
      setPageSubView(null);
    }
  }, [pageSubView, setPageSubView]);

  const handleFocusModeChange = useCallback((value: boolean | ((prev: boolean) => boolean)) => {
    const next = typeof value === 'function' ? value(effectiveFocusMode) : value;
    setFocusMode(next);
    if (pageSubView?.startsWith('learning-')) {
      setPageSubView(null);
    }
  }, [effectiveFocusMode, pageSubView, setPageSubView]);

  // endpoint-candidate 이벤트를 받는 콜백. 백엔드에 시그널을 보내는 연동은 아직 없다.
  vad.onEvent(useCallback((event: VADEvent) => {
    if (event.type === 'endpoint-candidate') {
      // send()는 백엔드 confirm 뒤에 불러야 해서 지금은 아무것도 하지 않는다
    }
  }, []));

  /** 음성 모드를 켜고 끄며 마이크도 함께 start/stop한다 */
  const handleToggleVoice = useCallback(async () => {
    if (voiceMode) {
      vad.stop();
      setVoiceMode(false);
    } else {
      // 켤 때는 튜터 탭으로 바꾼다
      setRightTab('chat');
      if (pageSubView?.startsWith('learning-')) {
        setPageSubView(null);
      }
      await vad.start();
      setVoiceMode(true);
    }
  }, [voiceMode, vad, pageSubView, setPageSubView]);

  // AudioPlayerBar 우측 여백. 집중 모드는 패널이 없어 32px,
  // 패널이 있으면 420(패널)+32(여백)+18(gap) = 470px다.
  const rightInset = effectiveFocusMode ? 32 : 470;

  // courseStatus가 null이면 로딩 중이라 막지 않는다
  const isUnlearnable = courseStatus !== null && UNLEARNABLE_STATUSES.includes(courseStatus);

  // 슬라이드가 0개인 생성 중 상태와 첫 챕터 준비 완료 콜백은 훅이 처리한다
  const { isLessonGenerating, handleFirstChapterReady } = useLessonGenerationGuard({
    courseId,
    courseStatus,
    chapters,
    chapterCourseId: chapterState.courseId,
    setChapterState,
  });

  return (
    <div
      className="learning-workspace"
      data-focus={effectiveFocusMode}
      style={{ '--audio-player-right': `${rightInset}px` } as CSSProperties}
    >
      <div className="learning-layout-grid">
        {/* 좌측 메인 학습 영역 */}
        <main id="current-slide" className="scroll learning-main-card" style={{
        minWidth: 0,
        background: 'var(--paper)',
        borderRadius: 16,
        border: '1px solid var(--line)',
        /* 집중 모드에서는 좌우 패딩을 늘린다 */
        padding: effectiveFocusMode
          ? 'var(--learning-main-focus-padding, 28px 72px 36px)'
          : 'var(--learning-main-padding, 24px 36px 32px)',
        /* 우측 패널보다 진한 그림자로 메인을 구분한다 */
        boxShadow: 'var(--shadow-md, 0 4px 16px rgba(0,0,0,0.08))',
        transition: 'padding 0.3s ease, box-shadow 0.3s ease',
        }}>
        {/* 커리큘럼 미확정이나 생성 실패면 안내 화면을 보인다 */}
        {isUnlearnable ? (
          <LearningNotReady courseStatus={courseStatus} palette={palette} setRoute={setRoute} />
        ) : isLessonGenerating ? (
          /* 슬라이드 생성 중에는 챕터 수 기준 진행률을 보인다 */
          <LessonGeneratingProgress
            courseId={courseId}
            palette={palette}
            setRoute={setRoute}
            onFirstChapterReady={handleFirstChapterReady}
          />
        ) : (
          <>
            {/* 헤더: 브레드크럼, 제목, 토글 버튼 */}
            <LearningHeader
              course={visibleCourse}
              chapterLabel={chapterLabel}
              lessonTitle={lessonTitle}
              completedSlides={completedSlides}
              totalSlides={totalSlides}
              currentSlideIndex={visibleSlideProgress?.slideIndex ?? 0}
              palette={palette}
              slidesCollapsed={slidesCollapsed}
              setSlidesCollapsed={setSlidesCollapsed}
              focusMode={effectiveFocusMode}
              setFocusMode={handleFocusModeChange}
            />
            {/* 본문: 슬라이드, 퀴즈, 내비게이션 */}
            <LearningContent
              key={`${courseId}:${activeChapterId}`}
              courseId={courseId}
              chapterId={activeChapterId}
              palette={palette}
              slidesCollapsed={slidesCollapsed}
              setSlidesCollapsed={setSlidesCollapsed}
              rightInset={rightInset}
              onSlideProgressChange={handleSlideProgressChange}
              onChapterComplete={handleChapterComplete}
              onNextChapter={handleNextChapter}
            />
          </>
        )}
        </main>

        {/* 우측 보조 패널. 집중 모드에서는 숨긴다 */}
        {!effectiveFocusMode && (
          <div id="open-question" style={{ alignSelf: 'stretch' }}>
            <div id="curriculum-view" style={{ height: '100%' }}>
              <div id="note-view" style={{ height: '100%' }}>
                <LearningRightPanel
                  rightTab={effectiveRightTab}
                  setRightTab={handleRightTabChange}
                  chat={chat}
                  typing={typing}
                  input={input}
                  setInput={setInput}
                  send={send}
                  retry={retry}
                  scrollRef={scrollRef}
                  palette={palette}
                  appState={appState}
                  course={visibleCourse}
                  courseId={courseId}
                  chapterId={activeChapterId}
                  chapters={chapters}
                  activeChapterId={activeChapterId}
                  voiceMode={voiceMode}
                  onToggleVoice={handleToggleVoice}
                  vadState={vad.state}
                  vadEnergy={vad.energy}
                  onChapterSelect={handleChapterSelect}
                />
              </div>
            </div>
          </div>
        )}
      </div>
      {/* focus-mode 앵커. 실제 토글 버튼은 LearningHeader 안에 있다 */}
      <div id="focus-mode" style={{ display: 'none' }} />

    </div>
  );
}

export default StudentLearning;
