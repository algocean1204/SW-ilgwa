// 학습 리포트 페이지. 데이터를 모으고 범위 상태를 두며, 화면은 components/report가 그린다.
// appState.pageSubView에 따라 한 섹션만 보이는 서브뷰와 전체 뷰를 오간다.
import { useState, useEffect } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import type { RouteSetter, AppState } from '@/shared/types';
import PageContentWithAside from '@/components/layout/PageContentWithAside';
import { ReportHeader }   from '@/components/report/ReportHeader';
import { ReportKpiRow }   from '@/components/report/ReportKpiRow'; // week-summary 서브뷰에서 사용
import { ReportBarChart } from '@/components/report/ReportBarChart';
import { ReportCalendar } from '@/components/report/StreakCalendar';
import { ConceptCards }   from '@/components/report/ConceptCards';
import { ReportAside }    from '@/components/report/ReportAside';
import type { WeekDay }   from '@/components/report/types';
import SectionFrame       from '@/components/ui/SectionFrame';
import { getWeeklyReport, getProgressReport, getStudyTimeReport, getConceptUnderstanding } from '@/apis';
import type { SubjectProgress, ChapterAccuracy } from '@/apis';

// Date.getDay() 인덱스 순서(0=일 ~ 6=토)
const DAY_NAMES = ['일', '월', '화', '수', '목', '금', '토'] as const;

// ISO 날짜("2026-05-19")의 한국어 요일. new Date("YYYY-MM-DD")는 UTC로 읽혀
// 타임존에 따라 하루 밀리므로 직접 파싱한다.
function getDayName(dateStr: string): string {
  const [y, m, d] = dateStr.split('-').map(Number) as [number, number, number];
  return DAY_NAMES[new Date(y, m - 1, d).getDay()] ?? '';
}

interface ReportProps {
  setRoute: RouteSetter;
  appState: AppState;
}

function Report({ setRoute: _setRoute, appState }: ReportProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;
  // 주간/월간 범위
  const [range, setRange] = useState('week');

  // 주간/월간 학습 데이터. 오류면 빈 배열로 두어 하위 컴포넌트가 0을 보인다.
  const [weekData, setWeekData] = useState<WeekDay[]>([]);
  // 주간 평균 정답률(API averageScorePercent)
  const [avgScore, setAvgScore] = useState(0);
  const [weekLabel, setWeekLabel] = useState('');
  // 과목별 진행률. 마운트 시 한 번 불러와 ConceptCards에 넘긴다.
  const [progressData, setProgressData] = useState<SubjectProgress[] | undefined>(undefined);
  // 일일 학습 목표 시간(분), WeeklyReportResponse 값
  const [dailyGoalMinutes, setDailyGoalMinutes] = useState(0);
  // 완료한 과제 수와 과제 평균 점수, WeeklyReportResponse 값
  const [completedAssignments, setCompletedAssignments] = useState(0);
  const [assignmentAvgScore, setAssignmentAvgScore] = useState(0);
  // 전체 퀴즈 수. 월간 모드에서도 쓰려고 마운트 시 따로 저장한다.
  const [savedTotalQuiz, setSavedTotalQuiz] = useState(0);
  // 챕터별 퀴즈 정답률(getConceptUnderstanding)
  const [chapterAccuracies, setChapterAccuracies] = useState<ChapterAccuracy[] | undefined>(undefined);

  // 사용자 통계는 범위와 무관하게 마운트 시 한 번 불러온다.
  // dailyGoalMinutes, weekLabel 등은 주간 API에만 있지만 월간 모드에서도 쓴다.
  // 필드가 빠져도 NaN이 되지 않게 ?? 0, ?? '' 기본값을 둔다.
  useEffect(() => {
    void getWeeklyReport().then((res) => {
      if (res.data) {
        setWeekLabel(res.data.weekLabel ?? '');
        setDailyGoalMinutes(res.data.dailyStudyGoalMinutes ?? 0);
        setCompletedAssignments(res.data.completedAssignmentCount ?? 0);
        setAssignmentAvgScore(res.data.assignmentAvgScore ?? 0);
        setSavedTotalQuiz(res.data.totalQuizCount ?? 0);
      }
    }).catch(() => {});
  }, []);

  // range가 'week'일 때 주간 차트 데이터를 불러온다. 빠진 필드는 ?? 0으로 채워 NaN을 막는다.
  useEffect(() => {
    if (range !== 'week') return;

    void getWeeklyReport().then((res) => {
      if (res.data) {
        const stats = Array.isArray(res.data.dailyStats) ? res.data.dailyStats : [];
        const mapped: WeekDay[] = stats.map((stat) => ({
          d: getDayName(stat.date ?? ''),
          m: stat.studyMinutes ?? 0,
          q: stat.quizCount ?? 0,
          r: stat.averageScore ?? 0,
        }));
        setWeekData(mapped);
        setAvgScore(res.data.averageScorePercent ?? 0);
      }
    }).catch(() => {});
  }, [range]);

  // range가 'month'일 때 학습 시간 리포트를 불러온다. dailyData가 배열이 아니어도 throw하지 않는다.
  useEffect(() => {
    if (range !== 'month') return;

    void getStudyTimeReport('30d').then((res) => {
      if (res.data) {
        const dailyData = Array.isArray(res.data.dailyData) ? res.data.dailyData : [];
        const mapped: WeekDay[] = dailyData.map((item) => ({
          d: item.date ?? '',
          m: item.minutes ?? 0,
          q: 0,
          r: 0,
        }));
        setWeekData(mapped);
        setAvgScore(res.data.averageScorePercent ?? 0);
        setSavedTotalQuiz(res.data.totalQuizCount ?? 0);
      }
    }).catch(() => {});
  }, [range]);

  // 과목별 진행률은 범위와 무관하게 항상 보여서 마운트 시 한 번만 불러온다
  useEffect(() => {
    void getProgressReport().then((res) => {
      if (res.data) {
        setProgressData(res.data.subjects);
      }
    }).catch(() => {});
  }, []);

  // 활성 코스가 있으면 챕터별 개념 이해도를 불러온다
  useEffect(() => {
    if (!appState.activeCourseId) return;
    void getConceptUnderstanding(appState.activeCourseId).then((res) => {
      if (res.data) {
        setChapterAccuracies(res.data.chapters);
      }
    }).catch(() => {});
  }, [appState.activeCourseId]);

  // totalMin, totalQ는 KPI, 차트, 사이드 패널에서 함께 써서 여기서 집계한다
  const totalMin = weekData.reduce((a, b) => a + b.m, 0);
  // 월간 모드의 weekData에는 퀴즈 정보가 없어 마운트 때 저장한 값을 쓴다
  const totalQ = range === 'month' ? savedTotalQuiz : weekData.reduce((a, b) => a + b.q, 0);

  // 현재 서브뷰 ID. null이면 전체 뷰다.
  const subView = appState.pageSubView;

  // 서브뷰면 해당 섹션만 메인 영역에 크게 보인다
  const renderMain = () => {
    if (subView === 'chart-view') {
      return (
        <SectionFrame title={range === 'month' ? '월간 학습 현황' : '요일별 학습 시간'} tone="default" padded={false}>
          {range === 'month'
            ? <ReportCalendar palette={palette} days={weekData} dailyGoalMinutes={dailyGoalMinutes || 60} />
            : <ReportBarChart palette={palette} week={weekData} totalMin={totalMin} dailyGoalMinutes={dailyGoalMinutes} />}
        </SectionFrame>
      );
    }
    if (subView === 'week-summary') {
      return <ReportKpiRow palette={palette} totalMin={totalMin} totalQ={totalQ} avgScore={avgScore} completedAssignments={completedAssignments} assignmentAvgScore={assignmentAvgScore} />;
    }
    if (subView === 'weak-concepts') {
      return (
        <SectionFrame title="개념 이해도" tone="default" padded={false}>
          <ConceptCards palette={palette} subjects={progressData} chapterAccuracies={chapterAccuracies} />
        </SectionFrame>
      );
    }
    if (subView === 'range-change') {
      // range-change 서브뷰는 범위 선택 버튼을 크게 보인다
      return (
        <SectionFrame title="학습 범위 설정" tone="default">
          <div style={{ display: 'flex', gap: 12 }}>
            {(['week', 'month'] as const).map(r => (
              <button
                key={r}
                onClick={() => setRange(r)}
                style={{
                  flex: 1, padding: '16px 0', borderRadius: 10,
                  fontSize: 14, fontWeight: 700,
                  background: range === r ? palette.primary : 'var(--surface-subtle)',
                  color: range === r ? 'var(--paper)' : 'var(--text-primary)',
                  border: 'none', cursor: 'pointer',
                  transition: 'background .15s',
                }}
              >
                {r === 'week' ? '이번 주' : '이번 달'}
              </button>
            ))}
          </div>
        </SectionFrame>
      );
    }

    // 전체 뷰. 범위에 따라 차트나 캘린더를 보이고 개념 이해도를 붙인다.
    // 학습 시간과 활동(ReportKpiRow)은 우측 컬럼에 있다.
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--section-gap)' }}>
        <SectionFrame title={range === 'month' ? '월간 학습 현황' : '요일별 학습 시간'} tone="default" padded={false}>
          {range === 'month'
            ? <ReportCalendar palette={palette} days={weekData} dailyGoalMinutes={dailyGoalMinutes || 60} />
            : <ReportBarChart palette={palette} week={weekData} totalMin={totalMin} dailyGoalMinutes={dailyGoalMinutes} />}
        </SectionFrame>
        <SectionFrame title="개념 이해도" tone="default" padded={false}>
          <ConceptCards palette={palette} subjects={progressData} chapterAccuracies={chapterAccuracies} />
        </SectionFrame>
      </div>
    );
  };

  // aside에는 학습 코멘트와 한눈에 요약(ReportAside)만 둔다.
  // 요약이 ReportKpiRow 수치와 겹쳐서 KpiRow는 넣지 않는다.
  const asideContent = (
    <ReportAside palette={palette} week={weekData} totalMin={totalMin} totalQ={totalQ} avgScore={avgScore} dailyGoalMinutes={dailyGoalMinutes} completedAssignmentCount={completedAssignments} assignmentAvgScore={assignmentAvgScore} range={range as 'week' | 'month'} />
  );

  return (
    <div style={{ maxWidth: 'var(--page-max-w)', margin: '0 auto', padding: 'var(--page-py) var(--page-px) 48px' }}>
      <ReportHeader palette={palette} range={range} setRange={setRange} weekLabel={weekLabel} totalMin={totalMin} />
      {/* aside 폭은 주간, 월간 모두 400px다. ReportBarChart는 flex:1 컬럼이라 깨지지 않는다 */}
      <PageContentWithAside main={renderMain()} aside={asideContent} asideWidth={400} />
    </div>
  );
}

export default Report;
