// 내 과제 화면. 과외별로 과제를 묶고 상세 조회와 답안 제출을 한다.
import { useState, useEffect } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import type { AppState, RouteSetter } from '@/shared/types';
import PageIntro from '@/components/ui/PageIntro';
import CompletedDetail from '@/components/homework/CompletedDetail';
import AssignmentSubmitPanel from '@/components/homework/AssignmentSubmitPanel';
import HomeworkCourseSelect from '@/components/homework/HomeworkCourseSelect';
import AssignmentListAside from '@/components/homework/AssignmentListAside';
import { getMyCourses, getAssignments, getAssignment, getGrades } from '@/apis';
import type {
  CourseSummaryResponse,
  AssignmentListResponse,
  AssignmentDetailResponse,
  GradeResponse,
} from '@/apis';

interface MyHomeworkProps {
  setRoute: RouteSetter;
  appState: AppState;
}

function MyHomework({ setRoute, appState }: MyHomeworkProps) {
  const {
    authReady,
    activeCourseId: storedActiveCourseId,
    setActiveCourseId,
  } = appState;
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;

  const [courses, setCourses] = useState<CourseSummaryResponse[]>([]);
  const [coursesLoaded, setCoursesLoaded] = useState(false);
  const [assignments, setAssignments] = useState<AssignmentListResponse[]>([]);
  const [selectedHwId, setSelectedHwId] = useState<string>('');
  // active 과제 상세(문항 포함). submitAssignment에 필요하다.
  const [detailData, setDetailData] = useState<AssignmentDetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  // completed 과제 채점 결과
  const [gradeData, setGradeData] = useState<GradeResponse | null>(null);
  const [gradeLoading, setGradeLoading] = useState(false);

  // 과외 목록을 최대 50개 불러온다. 인증 부트스트랩 전(authReady=false)에는 호출하지 않는다.
  useEffect(() => {
    if (!authReady) return;
    void Promise.resolve().then(() => setCoursesLoaded(false));
    void getMyCourses({ size: 50 }).then(res => {
      setCourses(res.data?.content ?? []);
    }).catch((err) => {
      console.warn('내 과외 목록 조회 실패:', err);
      setCourses([]);
    }).finally(() => {
      setCoursesLoaded(true);
    });
  }, [authReady]);

  // appState 값이 과외 목록에 있을 때만 쓰고, 없으면 첫 번째 과외로 폴백한다
  const activeCourseId = courses.find(c => c.id === storedActiveCourseId)?.id || courses[0]?.id || '';

  useEffect(() => {
    if (!coursesLoaded) return;
    if (activeCourseId && activeCourseId !== storedActiveCourseId) {
      setActiveCourseId(activeCourseId);
    }
  }, [activeCourseId, coursesLoaded, setActiveCourseId, storedActiveCourseId]);

  // 과외가 바뀌면 과제 목록을 다시 조회한다
  useEffect(() => {
    if (!coursesLoaded) return;
    if (!activeCourseId) {
      void Promise.resolve().then(() => {
        setAssignments([]);
        setSelectedHwId('');
        setDetailData(null);
        setGradeData(null);
      });
      return;
    }
    void getAssignments(activeCourseId).then(res => {
      const list = res.data ?? [];
      setAssignments(list);
      setSelectedHwId(list[0]?.id ?? '');
      setDetailData(null);
      setGradeData(null);
    }).catch((err) => {
      console.warn('과제 목록 조회 실패:', err);
      setAssignments([]);
      setSelectedHwId('');
      setDetailData(null);
      setGradeData(null);
    });
  }, [activeCourseId, coursesLoaded]);

  const sel = assignments.find(h => h.id === selectedHwId) ?? assignments[0];
  const selectedAssignmentId = sel?.id;
  const selectedAssignmentStatus = sel?.status;

  // 선택 과제가 completed면 채점 결과를, active면 문항 상세를 조회한다
  useEffect(() => {
    if (!selectedAssignmentId || !activeCourseId) {
      void Promise.resolve().then(() => {
        setDetailData(null);
        setGradeData(null);
      });
      return;
    }
    if (selectedAssignmentStatus === 'completed') {
      void Promise.resolve().then(() => {
        setDetailData(null);
        setGradeLoading(true);
        setGradeData(null);
        return getGrades(activeCourseId, selectedAssignmentId);
      }).then(res => {
        if (res.data) setGradeData(res.data);
      }).catch((err) => { console.warn('채점 결과 조회 실패:', err); }).finally(() => setGradeLoading(false));
    } else {
      // submitAssignment에 필요한 questions가 상세에 들어 있다
      void Promise.resolve().then(() => {
        setGradeData(null);
        setDetailLoading(true);
        setDetailData(null);
        return getAssignment(activeCourseId, selectedAssignmentId);
      }).then(res => {
        if (res.data) setDetailData(res.data);
      }).catch((err) => { console.warn('과제 상세 조회 실패:', err); }).finally(() => setDetailLoading(false));
    }
  }, [selectedAssignmentId, selectedAssignmentStatus, activeCourseId]);

  // 채점 중(active이고 제출함)인 과제를 볼 때만 목록을 폴링한다.
  // completed가 되면 위 useEffect가 getGrades로 결과를 띄우고 폴링 조건이 풀려 멈춘다.
  useEffect(() => {
    const isGrading = selectedAssignmentStatus === 'active' && !!detailData?.previousAnswer;
    if (!isGrading || !activeCourseId) return;

    const startedAt = Date.now();
    const timer = setInterval(() => {
      // 채점이 5분을 넘기면 폴링을 멈춘다
      if (Date.now() - startedAt > 5 * 60_000) {
        clearInterval(timer);
        return;
      }
      void getAssignments(activeCourseId)
        .then(res => {
          if (res.data) setAssignments(res.data);
        })
        .catch((err) => { console.warn('과제 상태 폴링 실패:', err); });
    }, 5_000);

    return () => clearInterval(timer);
  }, [selectedAssignmentStatus, detailData?.previousAnswer, activeCourseId]);

  const handleCourseSelect = (cid: string) => setActiveCourseId(cid);
  // 제출 후 목록을 다시 불러와 active→completed 변화를 반영한다
  const handleSubmitSuccess = () => {
    if (!activeCourseId) return;
    void getAssignments(activeCourseId).then(res => {
      if (res.data) setAssignments(res.data);
    }).catch((err) => { console.warn('과제 목록 갱신 실패:', err); });
  };
  return (
    <div style={{ maxWidth: 'var(--page-max-w)', margin: '0 auto', padding: 'var(--page-py) var(--page-px) 48px' }}>
      {/* 페이지 헤더. 학습실 탭과 같은 PageIntro를 쓴다 */}
      <PageIntro
        eyebrow="MY HOMEWORK"
        title="내 과제"
        description="챕터마다 과제를 확인하고 제출하세요"
      />

      {/* 과외 선택 드롭다운 */}
      <div style={{ marginBottom: 16 }}>
        <HomeworkCourseSelect
          courses={courses}
          activeCourseId={activeCourseId}
          assignmentCount={assignments.length}
          onCourseSelect={handleCourseSelect}
        />
      </div>

      <div className="myhw-grid">{/* 2열: 좁은 과제 목록, 넓은 상세 */}
        {/* 좌측: 과제 목록 */}
        <AssignmentListAside
          assignments={assignments}
          selectedId={sel?.id}
          activeCourseId={activeCourseId}
          courses={courses}
          palette={palette}
          onSelect={setSelectedHwId}
        />
        {/* 오른쪽 상세 패널. 좁은 화면에서는 전체 폭이 된다 */}
        <section className="myhw-detail" style={{ padding: '24px 28px', borderRadius: 16, background: 'var(--surface)', border: '1px solid var(--line)' }}>
          {!sel ? (
            <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>과제를 선택하세요</div>
          ) : sel.status === 'completed' ? (
            gradeLoading ? (
              <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>채점 결과를 불러오는 중...</div>
            ) : gradeData ? (
              <CompletedDetail
                title={sel.title}
                maxScore={gradeData.maxScore ?? sel.maxScore ?? 100}
                score={gradeData.score}
                feedback={gradeData.feedback}
                submittedAt={gradeData.submittedAt}
                gradedAt={gradeData.gradedAt}
                palette={palette}
                setRoute={setRoute}
              />
            ) : (
              <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>채점 결과를 불러올 수 없어요</div>
            )
          ) : (
            // active 상태: 과제 상세와 답안 제출 폼
            detailLoading ? (
              <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>과제를 불러오는 중...</div>
            ) : detailData ? (
              <AssignmentSubmitPanel
                courseId={activeCourseId}
                detail={detailData}
                palette={palette}
                onSubmitSuccess={handleSubmitSuccess}
              />
            ) : (
              <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>과제를 불러올 수 없어요</div>
            )
          )}
        </section>
      </div>
    </div>
  );
}

export default MyHomework;
