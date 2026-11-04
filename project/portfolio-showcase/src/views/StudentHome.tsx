// 학생 홈 대시보드. 상단 Hero(과외 전환 셀렉터 포함) 아래 좌측에 AI 비서 카드,
// 통계 3열 그리드, 하단 2분할을 두고 우측 사이드바에는 학습 스트릭만 둔다.
import { useState, useEffect, useCallback } from 'react';
import { getMyCourses, getMissionsToday, getConceptUnderstanding, getProfile, getChapters } from '@/apis';
import type { ChapterAccuracy, ChapterSummaryResponse, CourseSummaryResponse, MissionItem, WeeklyReportResponse } from '@/apis';
import type { AppState, RouteSetter } from '@/shared/types';
import HomeHero from '@/components/student-home/HomeHero';
import HomeStatsGrid from '@/components/student-home/HomeStatsGrid';
import HomeSidebar from '@/components/student-home/HomeSidebar';
import HaniAssistantCard from '@/components/student-home/HaniAssistantCard';
import HomeAssignmentChecklist from '@/components/student-home/HomeAssignmentChecklist';
import HomeWrongQuizCard from '@/components/student-home/HomeWrongQuizCard';
import { EmptyHomeState } from '@/components/student-home/EmptyHomeState';
import type { StreakInfo } from '@/components/student-home/StreakWidget';

interface StudentHomeProps {
  setRoute: RouteSetter;
  appState: AppState;
}

function StudentHome({ setRoute, appState }: StudentHomeProps) {
  const {
    authReady,
    activeCourseId,
    setActiveCourseId,
    setSelectedChapterId,
  } = appState;
  const [courses, setCourses] = useState<CourseSummaryResponse[]>([]);
  const [missions, setMissions] = useState<MissionItem[]>([]);
  // 빈 응답과 로딩 중을 구분해 스피너가 계속 돌지 않게 한다
  const [isLoading, setIsLoading] = useState(true);

  // AI 비서 하니의 한마디에 쓰는 주간 리포트와 스트릭 정보.
  // HomeStatsGrid, StreakWidget이 불러온 뒤 콜백으로 넘긴다.
  const [weeklyReport, setWeeklyReport] = useState<WeeklyReportResponse | null>(null);
  const [streakInfo, setStreakInfo] = useState<StreakInfo | null>(null);
  // 챕터별 정답률. "많이 틀린 챕터 복습" 카드가 가장 낮은 챕터를 찾을 때 쓴다.
  const [chapterAccuracies, setChapterAccuracies] = useState<ChapterAccuracy[]>([]);

  // Hero 제목에 보일 현재 챕터. activeCourse가 바뀔 때마다 갱신한다.
  const [currentChapter, setCurrentChapter] = useState<ChapterSummaryResponse | null>(null);

  // AI 비서 카드 제목에 넣을 닉네임. WorkspaceTopBar처럼 getProfile()로 조회한다.
  const [nickname, setNickname] = useState<string | null>(null);

  useEffect(() => {
    // 인증 부트스트랩 전에는 보호 API를 부르지 않는다
    if (!authReady) return;
    let cancelled = false;
    void Promise.resolve().then(() => {
      if (!cancelled) setIsLoading(true);
    });

    const coursesReq = getMyCourses({ size: 20 }).then((res) => {
      if (cancelled) return;
      if (Array.isArray(res.data?.content)) setCourses(res.data.content);
      else setCourses([]);
    }).catch((err) => {
      if (cancelled) return;
      console.warn('내 과외 목록 조회 실패:', err);
      setCourses([]);
    });

    const missionsReq = getMissionsToday().then((res) => {
      if (cancelled) return;
      if (Array.isArray(res.data?.missions)) setMissions(res.data.missions);
      else setMissions([]);
    }).catch((err) => {
      if (cancelled) return;
      console.warn('오늘 미션 조회 실패:', err);
      setMissions([]);
    });

    // 두 API가 성공이든 실패든 끝나면 로딩을 푼다
    void Promise.allSettled([coursesReq, missionsReq]).then(() => {
      if (!cancelled) setIsLoading(false);
    });

    return () => {
      cancelled = true;
    };
  }, [authReady]);

  // 닉네임은 메인 로딩과 따로 조회한다
  useEffect(() => {
    if (!authReady) return;
    void getProfile().then((res) => {
      if (res.data?.nickname) setNickname(res.data.nickname);
    }).catch((err) => {
      console.warn('프로필 닉네임 조회 실패:', err);
    });
  }, [authReady]);

  // activeCourseId 과외가 없으면 첫 번째 과외를 쓴다
  const activeCourse = courses.find((c) => c.id === activeCourseId) ?? courses[0];

  // localStorage에 삭제됐거나 잘못된 과외 ID가 남아 있으면 홈에 보이는 첫 과외로 전역 상태를 고친다
  useEffect(() => {
    if (!authReady || courses.length === 0) return;
    const hasActiveCourse = courses.some((c) => c.id === activeCourseId);
    if (!hasActiveCourse) setActiveCourseId(courses[0]!.id);
  }, [activeCourseId, authReady, setActiveCourseId, courses]);

  // "많이 틀린 챕터 복습" 카드용 챕터별 정답률을 불러온다.
  // activeCourse가 없으면 호출하지 않고, 이전 과외 값이 남지 않게 빈 배열로 둔다.
  useEffect(() => {
    const courseId = activeCourse?.id;
    if (!courseId) return;
    void getConceptUnderstanding(courseId).then((res) => {
      setChapterAccuracies(res.data?.chapters ?? []);
    }).catch((err) => {
      console.warn('챕터별 정답률 조회 실패:', err);
      setChapterAccuracies([]);
    });
  }, [activeCourse?.id]);

  // 챕터 목록에서 현재 챕터를 찾아 Hero 제목과 진행바에 쓴다.
  // StudentLearning과 같이 IN_PROGRESS를 먼저, 없으면 AVAILABLE을 고른다.
  // activeCourse가 없으면 호출하지 않고 이전 과외 챕터가 남지 않게 비운다.
  useEffect(() => {
    const courseId = activeCourse?.id;
    if (!courseId) {
      return;
    }
    void getChapters(courseId).then((res) => {
      const chapterList = res.data ?? [];
      const active = chapterList.find((c) => c.status === 'IN_PROGRESS')
        ?? chapterList.find((c) => c.status === 'AVAILABLE')
        ?? null;
      setCurrentChapter(active);
    }).catch((err) => {
      console.warn('현재 챕터 조회 실패:', err);
      setCurrentChapter(null);
    });
  }, [activeCourse?.id]);

  // 현재 챕터의 슬라이드 진도. Hero 제목이 currentChapter라 진행바도 같은 챕터 기준이다.
  // slideCount가 0(슬라이드 미생성)이면 과외 전체 강의 완료 비율을 쓴다.
  // 이 비율은 MyCourses, GlobalSidebar와 같은 분자·분모로 계산한다.
  const currentChapterSlideCount = currentChapter?.slideCount ?? 0;
  const currentChapterCompletedSlides = currentChapter
    ? Math.min(Math.max(currentChapter.completedSlides ?? 0, 0), currentChapterSlideCount)
    : 0;
  const slideProgressPct = currentChapterSlideCount > 0
    ? Math.round((currentChapterCompletedSlides / currentChapterSlideCount) * 100)
    : (activeCourse?.totalLessons ?? 0) > 0
      ? Math.round(((activeCourse?.completedLessons ?? 0) / (activeCourse?.totalLessons ?? 1)) * 100)
      : 0;

  // 퀴즈를 1개 이상 푼 챕터 중 정답률이 가장 낮은 챕터를 "많이 틀린 챕터"로 본다
  const weakChapter = chapterAccuracies
    .filter((c) => c.totalQuizCount > 0)
    .reduce<ChapterAccuracy | null>((weakest, c) => (
      !weakest || c.accuracyPercent < weakest.accuracyPercent ? c : weakest
    ), null);

  // Hero 드롭다운은 activeCourseId만 바꾼다. 사이드바 handleCourseSelect와 달리
  // learning 화면으로 이동하지 않는다.
  const handleHeroCourseSelect = (courseId: string) => {
    setActiveCourseId(courseId);
  };

  // HomeStatsGrid, StreakWidget의 useEffect가 다시 돌지 않게 useCallback으로 고정한다
  const handleWeeklyReportLoaded = useCallback((report: WeeklyReportResponse) => {
    setWeeklyReport(report);
  }, []);
  const handleStreakLoaded = useCallback((info: StreakInfo) => {
    setStreakInfo(info);
  }, []);

  if (isLoading) {
    return (
      <div className="sh-page">
        <div className="sh-loading" role="status" aria-label="학습 데이터 로딩 중">
          학습 데이터를 불러오는 중...
        </div>
      </div>
    );
  }

  // 로딩 뒤 과외가 없으면 빈 상태 화면을 보인다
  if (!activeCourse) {
    return <EmptyHomeState setRoute={setRoute} />;
  }

  return (
    <div className="sh-page">
      {/* 상단 Hero: 현재 수업, 오늘 미션, 과외 전환 셀렉터 */}
      <HomeHero
        activeCourse={activeCourse}
        currentChapter={currentChapter}
        missions={missions}
        setRoute={setRoute}
        courses={courses}
        activeCourseId={activeCourse.id}
        onCourseSelect={handleHeroCourseSelect}
        onContinueChapterSelect={setSelectedChapterId}
        slideProgressPct={slideProgressPct}
        completedSlides={currentChapterCompletedSlides}
        totalSlides={currentChapterSlideCount}
      />

      {/* 본문 2열: 좌측 메인 콘텐츠 + 우측 사이드바 */}
      <div className="sh-body">
        {/* 좌측: AI 비서 카드와 이번 주 학습 통계 */}
        <main className="sh-body__main">
          {/* AI 비서 하니의 한마디. LLM 없이 규칙으로 문구를 고른다 */}
          <HaniAssistantCard
            stats={{
              averageScorePercent: weeklyReport?.averageScorePercent ?? 0,
              totalQuizCount: weeklyReport?.totalQuizCount ?? 0,
              currentStreak: streakInfo?.currentStreak ?? 0,
              activeCourse,
            }}
            nickname={nickname}
          />

          <span className="sh-body__section-label">
            이번 주 학습 현황
          </span>
          <HomeStatsGrid onLoaded={handleWeeklyReportLoaded} />

          {/* 하단 2분할: 남은 과제 체크리스트, 많이 틀린 챕터 복습(암기노트 이동) */}
          <div className="sh-bottom-split">
            <HomeAssignmentChecklist
              activeCourseId={activeCourse.id}
              setRoute={setRoute}
              onCourseSelect={setActiveCourseId}
            />
            <HomeWrongQuizCard setRoute={setRoute} appState={appState} weakChapter={weakChapter} />
          </div>
        </main>

        {/* 우측 사이드바: 학습 스트릭 */}
        <HomeSidebar
          onStreakLoaded={handleStreakLoaded}
        />
      </div>
    </div>
  );
}

export default StudentHome;
