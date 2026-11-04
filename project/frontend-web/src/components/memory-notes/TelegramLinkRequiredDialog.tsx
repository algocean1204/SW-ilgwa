// 텔레그램을 연동하지 않고 암기노트를 보내려 할 때 뜨는 안내 모달.
// ConfirmDialog에 아이콘, 톤, 레이블만 넘긴다.
import ConfirmDialog from '@/components/ui/ConfirmDialog';

interface TelegramLinkRequiredDialogProps {
  open: boolean;
  /** 취소. 모달을 닫는다. */
  onClose: () => void;
  /** 연동하러 가기. 설정 > 텔레그램 연동 탭으로 이동한다. */
  onGoToLink: () => void;
}

export default function TelegramLinkRequiredDialog({
  open, onClose, onGoToLink,
}: TelegramLinkRequiredDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      onClose={onClose}
      icon={{ name: 'send', color: 'var(--telegram-blue)', bgColor: 'var(--telegram-ui-bg)' }}
      title="텔레그램 연동이 필요한 기능입니다"
      description={<>암기노트를 텔레그램으로 전송하려면<br />먼저 텔레그램 계정을 연동해 주세요.</>}
      confirmLabel="연동하러 가기"
      onConfirm={onGoToLink}
      cancelLabel="취소"
      confirmTone="telegram"
    />
  );
}
