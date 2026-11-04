// 모의고사 페이지. overview, generating, taking, result 단계에 맞는 화면을 고른다.
import { useTweaks, PALETTES } from '@/lib';
import type { AppState, RouteSetter } from '@/shared/types';
import PageIntro from '@/components/ui/PageIntro';
import PageContentWithAside from '@/components/layout/PageContentWithAside';
import MockExamAside from '@/components/mock-exam/MockExamAside';
import ExamOverview from './ExamOverview';
import ExamGeneratingView from './ExamGeneratingView';
import ExamTakingStage from './ExamTakingStage';
import ExamResultPanel from './ExamResultPanel';
import { useExamCourses } from './useExamCourses';
import { useMockExam } from './useMockExam';
import { EXAM_COIN_COST } from './examConstants';
import { resolveGenerationDisplay } from './generationOutcome';

interface MockExamViewProps {
  setRoute: RouteSetter;
  appState: AppState;
}

export default function MockExamView({ setRoute, appState }: MockExamViewProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;

  const { courses, selectedCourseId, setSelectedCourseId } = useExamCourses(appState);
  const exam = useMockExam({
    courseId: selectedCourseId,
    appState,
    onNeedCoins: () => setRoute('pricing'),
  });

  // 생성 결과 메타에서 표시 상태를 정한다. 메타가 없으면 'ok'다.
  const genDisplay = resolveGenerationDisplay(exam.generationMeta);

  return (
    <div style={{ padding: 'var(--page-py) var(--page-px)', maxWidth: 'var(--page-max-w)', margin: '0 auto' }}>
      <div style={{ marginBottom: 'var(--section-gap)' }}>
        <PageIntro
          eyebrow="PRACTICAL MOCK EXAM"
          title={<><span className="script" style={{ color: palette.primary, marginRight: 6 }}>실전</span>모의고사</>}
          description="AI가 커리큘럼 전 범위를 분석해 실전 문제를 생성합니다. 실제 시험처럼 풀고 즉시 채점 · 해설까지 확인하세요."
        />
      </div>

      {/* 오류 배너는 단계와 관계없이 맨 위에 보인다 */}
      {exam.error && (
        <div
          role="alert"
          aria-live="assertive"
          style={{
            marginBottom: 'var(--section-gap)', padding: '12px 16px', borderRadius: 10,
            background: 'var(--state-danger-bg)', border: '1px solid var(--state-danger)',
            fontSize: 13, fontWeight: 600, color: 'var(--coral-ink-deep)',
          }}
        >
          {exam.error}
        </div>
      )}

      {/* overview: 과외 선택, 목록, 생성 옵션. aside는 응시 기록과 팁 */}
      {exam.phase === 'overview' && (
        <PageContentWithAside
          main={
            <ExamOverview
              courses={courses}
              selectedCourseId={selectedCourseId}
              examList={exam.examList}
              listLoading={exam.listLoading}
              coins={appState.coins}
              cost={EXAM_COIN_COST}
              palette={palette}
              courseNotCompleted={exam.courseNotCompleted}
              onSelectCourse={setSelectedCourseId}
              onGenerate={(req) => { void exam.startGenerate(req); }}
              onOpenExam={(id) => { void exam.openAttempt(id); }}
              onViewResult={(id) => { void exam.viewResult(id); }}
              onNeedCoins={() => setRoute('pricing')}
            />
          }
          aside={<MockExamAside palette={palette} history={[]} />}
          asideWidth={300}
        />
      )}

      {/* generating: isFailed면 5분 타임아웃을 기다리지 않고 바로 오류 화면을 보인다 */}
      {exam.phase === 'generating' && (
        <ExamGeneratingView
          palette={palette}
          isFailed={exam.isFailed}
          // 자료 부족이면 빨간 FAILED 화면 대신 행동 제안 화면을 보인다
          needsMoreSource={genDisplay.kind === 'needs_more_source'}
          onRetry={exam.reset}
          onLater={exam.reset}
        />
      )}

      {/* taking: detail을 받은 뒤 응시. partial이면 위에 "요청 N문항 중 M문항 생성됨"을 보인다 */}
      {exam.phase === 'taking' && exam.detail && (
        <ExamTakingStage
          detail={exam.detail}
          palette={palette}
          generationDisplay={genDisplay}
          onFinish={(answers, timeTaken) => exam.finishExam(answers, timeTaken)}
        />
      )}

      {exam.phase === 'result' && exam.result && (
        <ExamResultPanel
          result={exam.result}
          palette={palette}
          generationDisplay={genDisplay}
          onRetry={exam.reset}
          onBackToList={exam.reset}
        />
      )}
    </div>
  );
}
