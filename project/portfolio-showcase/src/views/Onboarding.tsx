// 온보딩 흐름. 한 화면에 한 질문씩 단계를 넘긴다.
import { useState } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import type { Palette } from '@/lib';
import type { AppState, RouteSetter } from '@/shared/types';
import { ApiError, completeOnboarding, createTutoring, uploadTutoringSourcePdf } from '@/apis';
import type { CourseExploreMode } from '@/components/course-explore/ModeToggle';
import { OnboardingProgress }  from '@/components/onboarding/OnboardingProgress';
import { OnboardingFooterNav } from '@/components/onboarding/OnboardingFooterNav';
import { OnboardingStep1, SUBJECT_LIST } from '@/components/onboarding/OnboardingStep1';
import { OnboardingStep2, GOAL_LIST } from '@/components/onboarding/OnboardingStep2';
import { OnboardingStep2b }    from '@/components/onboarding/OnboardingStep2b';
import { OnboardingStep3 }     from '@/components/onboarding/OnboardingStep3';
import { OnboardingStep4 }     from '@/components/onboarding/OnboardingStep4';
import { OnboardingStep5 }     from '@/components/onboarding/OnboardingStep5';
import { useCourseExploreTutors } from '@/components/course-explore/useCourseExploreTutors';

interface OnboardingProps {
  setRoute: RouteSetter;
  appState: AppState;
}

// 6단계: 과목, 목표, 학습 시간, PDF/주제, 세부 설정, 텔레그램
const TOTAL_STEPS = 6;
const DEFAULT_GRADE = "일반";
const DEFAULT_PREFERRED_TONE = "존댓말";

const SUBJECT_LABEL_BY_ID = new Map(SUBJECT_LIST.map(subject => [subject.id, subject.label]));
const GOAL_LABEL_BY_ID = new Map(GOAL_LIST.map(goal => [goal.id, goal.label]));

function formatOnboardingError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "로그인 세션이 만료됐어요. 다시 로그인한 뒤 온보딩을 완료해주세요.";
    if (error.status === 409) return "이미 온보딩이 완료된 계정입니다.";
    return error.errorResponse?.message ?? `온보딩 저장에 실패했어요. (${error.status})`;
  }

  if (error instanceof Error) return error.message;
  return "온보딩 저장에 실패했어요.";
}

function formatTutoringCreationError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return "서버에 연결하지 못했습니다. 백엔드 실행 상태를 확인한 뒤 다시 시도해 주세요.";
    if (error.status === 502) return "커리큘럼 생성 서버 연결에 실패해 과외를 저장하지 못했습니다. 잠시 뒤 다시 시도해 주세요.";
    return error.errorResponse?.message ?? `과외 생성에 실패했어요. (${error.status})`;
  }

  if (error instanceof Error) return error.message;
  return "과외 생성에 실패했어요.";
}

function Onboarding({ setRoute, appState }: OnboardingProps) {
  const t = useTweaks();
  const palette: Palette = PALETTES[t.palette] ?? PALETTES.grass!;

  const [step, setStep] = useState(1);
  const [subjects, setSubjects] = useState<Set<string>>(new Set(["sdlc", "toeic"]));
  const [goal, setGoal] = useState("exam");
  const [when, setWhen] = useState(15);
  const [sourceMode, setSourceMode] = useState<CourseExploreMode>('pdf');
  const [pdfFile, setPdfFile] = useState<File | null>(null);
  const [topic, setTopic] = useState('');
  const [duration, setDuration] = useState(30);
  const [depth, setDepth] = useState('normal');
  const [lessonCount, setLessonCount] = useState(6);
  const { teacher, setTeacher, defaultTutors, customTutors } = useCourseExploreTutors();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const toggle = (id: string) => {
    setSubjects(prev => {
      const n = new Set(prev);
      if (n.has(id)) n.delete(id); else n.add(id);
      return n;
    });
  };

  const handlePrev = () => {
    if (isSubmitting) return;
    setErrorMessage(null);
    if (step > 1) setStep(s => s - 1);
    else setRoute("landing");
  };

  const handleSkip = () => {
    if (isSubmitting) return;
    setErrorMessage(null);
    setStep(s => Math.min(s + 1, TOTAL_STEPS));
  };

  const buildOnboardingBody = () => ({
    interests: Array.from(subjects).map(id => SUBJECT_LABEL_BY_ID.get(id) ?? id),
    grade: DEFAULT_GRADE,
    learningGoal: GOAL_LABEL_BY_ID.get(goal) ?? goal,
    preferredTone: DEFAULT_PREFERRED_TONE,
    dailyStudyGoalMinutes: when,
  });

  const handleNext = async () => {
    if (isSubmitting) return;

    if (subjects.size === 0) {
      setStep(1);
      setErrorMessage("관심 과목을 최소 1개 선택해주세요.");
      return;
    }

    setErrorMessage(null);

    // step 4는 PDF나 주제 중 하나가 있어야 넘어간다
    if (step === 4) {
      const hasSource = sourceMode === 'pdf' ? pdfFile : topic.trim();
      if (!hasSource) {
        setErrorMessage(sourceMode === 'pdf'
          ? 'PDF 파일을 업로드해주세요.'
          : '학습 주제를 입력해주세요.');
        return;
      }
    }

    if (step < TOTAL_STEPS) {
      setStep(s => s + 1);
      return;
    }

    setIsSubmitting(true);
    try {
      const trimmedTopic = topic.trim();
      const firstSubject = Array.from(subjects)[0];
      const subjectLabel = SUBJECT_LABEL_BY_ID.get(firstSubject ?? '') ?? '일반';

      const pdfUpload = sourceMode === 'pdf' && pdfFile
        ? await uploadTutoringSourcePdf(pdfFile)
        : null;
      const pdfUrl = pdfUpload?.data?.url;
      if (sourceMode === 'pdf' && !pdfUrl) {
        setErrorMessage('PDF 업로드에 실패했습니다. 다시 시도해 주세요.');
        return;
      }
      let courseRes: Awaited<ReturnType<typeof createTutoring>>;
      try {
        courseRes = await createTutoring({
          title: trimmedTopic || pdfFile?.name || '새 과외',
          subject: subjectLabel,
          sourceType: sourceMode,
          sourceRef: sourceMode === 'pdf' ? pdfUrl : undefined,
          topic: sourceMode === 'topic' ? trimmedTopic : undefined,
          lessonCount,
          tutorId: teacher || undefined,
          durationDays: duration,
          difficulty: depth,
        });
      } catch (error) {
        setErrorMessage(formatTutoringCreationError(error));
        return;
      }
      if (courseRes.data) {
        // 과외 생성이 성공해야 온보딩을 완료로 표시한다. 실패하면 다음 로그인 때 온보딩부터 다시 한다.
        try {
          await completeOnboarding(buildOnboardingBody());
        } catch (error) {
          if (!(error instanceof ApiError && error.status === 409)) {
            throw error;
          }
        }
        appState.setActiveCourseId(courseRes.data.id);
        appState.setHasCourses(true);
        setRoute("courseDetail");
      } else {
        setErrorMessage('과외 생성에 실패했어요. 다시 시도해 주세요.');
      }
    } catch (error) {
      const message = formatOnboardingError(error);
      setErrorMessage(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{
      maxWidth: 920, margin: "24px auto", padding: "44px 48px 36px",
      background: "var(--paper)", borderRadius: 22, border: "1px solid var(--line)",
      boxShadow: "var(--shadow-lg)", minHeight: 640, position: "relative",
      display: "flex", flexDirection: "column",
    }}>
      <OnboardingProgress step={step} totalSteps={TOTAL_STEPS} palette={palette} />

      <div style={{ flex: 1 }}>
        {step === 1 && <OnboardingStep1 subjects={subjects} toggle={toggle} palette={palette} />}
        {step === 2 && <OnboardingStep2 goal={goal} setGoal={setGoal} palette={palette} />}
        {step === 3 && <OnboardingStep2b when={when} setWhen={setWhen} palette={palette} />}
        {step === 4 && (
          <OnboardingStep3
            palette={palette}
            mode={sourceMode}
            setMode={setSourceMode}
            file={pdfFile}
            onFileSelect={setPdfFile}
            onFileClear={() => setPdfFile(null)}
            topic={topic}
            onTopicChange={setTopic}
          />
        )}
        {step === 5 && (
          <OnboardingStep5
            palette={palette} setRoute={setRoute}
            duration={duration} depth={depth} teacher={teacher} lessonCount={lessonCount}
            defaultTutors={defaultTutors} customTutors={customTutors}
            onDurationChange={setDuration} onDepthChange={setDepth}
            onTeacherChange={setTeacher} onLessonCountChange={setLessonCount}
          />
        )}
        {step === 6 && <OnboardingStep4 palette={palette} />}
      </div>

      {errorMessage && (
        <div
          role="alert"
          style={{
            marginTop: 18,
            padding: "12px 14px",
            borderRadius: 10,
            background: "var(--state-danger-bg)",
            border: "1px solid var(--coral)",
            color: "var(--danger)",
            fontSize: 13,
            fontWeight: 700,
          }}
        >
          {errorMessage}
        </div>
      )}

      <OnboardingFooterNav
        step={step}
        totalSteps={TOTAL_STEPS}
        palette={palette}
        disabled={isSubmitting}
        showSkip={step !== 4}
        onPrev={handlePrev}
        onSkip={handleSkip}
        onNext={handleNext}
      />
    </div>
  );
}

export default Onboarding;
