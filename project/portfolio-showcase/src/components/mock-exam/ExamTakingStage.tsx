// 응시 단계 컨테이너. 응시 중 로컬 상태를 들고 제출 요청을 만든다.
// AnswerMap 값은 유형별 ExamAnswer 판별 유니온이다.
import { useRef, useState } from 'react';
import PageContentWithAside from '@/components/layout/PageContentWithAside';
import type { Palette } from '@/lib';
import type { AttemptDetailResponse } from '@/apis';
import ExamTakingPanel from './ExamTakingPanel';
import ExamOmrAside from './ExamOmrAside';
import ExamSubmitDialog from './ExamSubmitDialog';
import PartialSourceNotice from './PartialSourceNotice';
import type { AnswerMap, ExamAnswer } from './mockExam.types';
import type { GenerationDisplayState } from './generationOutcome';

interface ExamTakingStageProps {
  detail: AttemptDetailResponse;
  palette: Palette;
  /**
   * 자료가 좁아 일부만 생성된 경우 partial. 응시 화면 위에 안내 배너를 띄운다.
   * 없으면 'ok'로 보고 배너를 띄우지 않는다.
   */
  generationDisplay?: GenerationDisplayState;
  onFinish: (answers: AnswerMap, timeTaken: number) => Promise<void>;
}

export default function ExamTakingStage({ detail, palette, generationDisplay, onFinish }: ExamTakingStageProps) {
  const [answers, setAnswers] = useState<AnswerMap>({});
  const [currentIdx, setCurrentIdx] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  // 제출 때 경과 시간(초)을 계산하려고 응시 시작 시각을 둔다.
  const startedAt = useRef<number>(Date.now());

  const total = detail.questions.length;

  // 유형과 관계없이 AnswerMap에 값이 없으면 미답으로 센다.
  const unansweredCount = detail.questions.filter(
    (q) => answers[q.questionId] == null,
  ).length;

  const handleAnswer = (questionId: string, answer: ExamAnswer) => {
    setAnswers((prev) => ({ ...prev, [questionId]: answer }));
  };

  const handleConfirmSubmit = async () => {
    setSubmitting(true);
    const timeTaken = Math.round((Date.now() - startedAt.current) / 1000);
    try {
      await onFinish(answers, timeTaken);
    } finally {
      setSubmitting(false);
      setDialogOpen(false);
    }
  };

  return (
    <>
      {/* "요청 N문항 중 M문항 생성됨" 안내. 응시는 그대로 할 수 있다 */}
      {generationDisplay && generationDisplay.kind === 'partial' && (
        <div style={{ marginBottom: 'var(--section-gap)' }}>
          <PartialSourceNotice state={generationDisplay} variant="banner" />
        </div>
      )}
      <PageContentWithAside
        main={
          <ExamTakingPanel
            detail={detail}
            currentIdx={currentIdx}
            answers={answers}
            palette={palette}
            onAnswer={handleAnswer}
            onPrev={() => setCurrentIdx((i) => Math.max(0, i - 1))}
            onNext={() => setCurrentIdx((i) => Math.min(total - 1, i + 1))}
          />
        }
        aside={
          <ExamOmrAside
            questions={detail.questions}
            answers={answers}
            currentIdx={currentIdx}
            limitMinutes={null}
            palette={palette}
            onJump={setCurrentIdx}
            onSubmit={() => setDialogOpen(true)}
            // 시간이 끝나면 지금 답안 그대로 바로 제출한다
            onExpire={() => { void handleConfirmSubmit(); }}
          />
        }
        asideWidth={280}
      />

      <ExamSubmitDialog
        open={dialogOpen}
        unansweredCount={unansweredCount}
        submitting={submitting}
        palette={palette}
        onConfirm={() => { void handleConfirmSubmit(); }}
        onClose={() => setDialogOpen(false)}
      />
    </>
  );
}
