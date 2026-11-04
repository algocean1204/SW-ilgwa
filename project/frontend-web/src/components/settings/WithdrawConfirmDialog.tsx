// 회원 탈퇴 확인 모달.
// 공용 ConfirmDialog에 danger 톤과 warning 아이콘을 넘긴다.
import ConfirmDialog from '@/components/ui/ConfirmDialog';

interface WithdrawConfirmDialogProps {
  open: boolean;
  submitting: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export default function WithdrawConfirmDialog({
  open, submitting, onConfirm, onClose,
}: WithdrawConfirmDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      onClose={onClose}
      // 되돌릴 수 없는 작업이라 warning 아이콘을 쓴다
      icon={{ name: 'warning', color: 'var(--danger)', bgColor: 'var(--coral-wash)' }}
      title="정말 탈퇴하시겠어요?"
      description={
        submitting
          ? '탈퇴를 처리하는 중이에요...'
          : '탈퇴하면 즉시 로그아웃되며, 같은 계정으로 다시 로그인할 수 없어요. 학습 기록은 보관되지만 서비스 이용은 중단됩니다.'
      }
      confirmLabel={submitting ? '탈퇴 처리 중...' : '회원 탈퇴'}
      onConfirm={onConfirm}
      cancelLabel="취소"
      confirmTone="danger"
      submitting={submitting}
    />
  );
}
