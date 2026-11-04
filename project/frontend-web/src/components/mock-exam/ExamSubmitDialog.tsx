// 제출 확인 모달. 안 푼 문항을 경고하고 ConfirmDialog로 제출을 확정한다.
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import type { Palette } from '@/lib';

interface ExamSubmitDialogProps {
  open: boolean;
  unansweredCount: number;
  submitting: boolean;
  palette: Palette;
  onConfirm: () => void;
  onClose: () => void;
}

export default function ExamSubmitDialog({
  open, unansweredCount, submitting, palette, onConfirm, onClose,
}: ExamSubmitDialogProps) {
  // 채점과 AI 분석이 동기 호출이라 10~60초 걸릴 수 있어 제출 중에는 대기 안내를 보인다.
  const description = submitting
    ? 'AI가 채점하고 약점을 분석하는 중이에요 · 최대 1분 정도 걸려요'
    : unansweredCount > 0
      ? `아직 답하지 않은 문항이 ${unansweredCount}개 있어요. 제출하면 더 이상 수정할 수 없어요.`
      : '제출하면 더 이상 수정할 수 없어요. 바로 채점 결과를 확인할 수 있어요.';

  return (
    <ConfirmDialog
      open={open}
      onClose={onClose}
      title="모의고사를 제출할까요?"
      description={description}
      confirmLabel={submitting ? '제출 중...' : '제출하기'}
      onConfirm={onConfirm}
      cancelLabel="더 풀기"
      // palette.primary는 고른 팔레트마다 달라 override로 넘긴다
      confirmColorOverride={palette.primary}
      submitting={submitting}
    />
  );
}
