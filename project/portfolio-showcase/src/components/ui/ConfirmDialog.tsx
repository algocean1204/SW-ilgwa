// 안내·확인용 공용 모달. 테두리 없는 흰 카드에 아이콘, 제목, 본문, 버튼 두 개를
// 세로로 가운데 정렬해 쌓는다(max-w 380px).
import { type ReactNode } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import Icon from '@/Icon';

// 주 버튼 색 톤: primary=accent-green, telegram=blue, danger=red/coral
type ConfirmTone = 'primary' | 'telegram' | 'danger';

interface IconConfig {
  name: string;   // Icon 컴포넌트 name 키
  color: string;  // stroke 색 (CSS 토큰 또는 hex)
  bgColor: string; // 원형 배경 색
}

interface ConfirmDialogProps {
  open: boolean;
  onClose: () => void;
  icon?: IconConfig;          // 없으면 아이콘 영역 생략
  title: string;
  description?: ReactNode;    // <br /> 등 ReactNode 허용
  confirmLabel: string;
  onConfirm: () => void;
  cancelLabel?: string;       // 기본 "취소"
  onCancel?: () => void;      // 없으면 onClose
  confirmTone?: ConfirmTone;  // 기본 'primary'
  submitting?: boolean;       // 처리 중이면 버튼을 막는다
  disabled?: boolean;         // 추가 비활성 조건
  confirmColorOverride?: string; // palette.primary 등 동적 색 오버라이드 (tone 무시)
  children?: ReactNode;       // 본문 아래 추가 슬롯 (textarea 등)
}

// 톤별 주 버튼 배경 토큰
const TONE_BG: Record<ConfirmTone, string> = {
  primary:  'var(--accent-primary)',
  telegram: 'var(--telegram-blue)',
  danger:   'var(--danger)',
};

export default function ConfirmDialog({
  open, onClose, icon, title, description,
  confirmLabel, onConfirm,
  cancelLabel = '취소', onCancel,
  confirmTone = 'primary',
  submitting = false, disabled = false,
  confirmColorOverride, children,
}: ConfirmDialogProps) {
  // submitting 중에는 오버레이·ESC 닫기도 막는다
  const handleOpenChange = (v: boolean) => { if (!v && !submitting) onClose(); };
  const handleCancel = () => { if (!submitting) (onCancel ?? onClose)(); };

  const isDisabled = submitting || disabled;
  // confirmColorOverride가 있으면 tone 대신 그 색(palette.primary 등)을 쓴다
  const confirmBg = submitting
    ? 'var(--text-tertiary)'
    : (confirmColorOverride ?? TONE_BG[confirmTone]);

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      {/*
        카드 스타일:
        - !bg-[var(--paper)]: --popover 토큰 대신 불투명 흰 배경을 쓴다
        - ring-0 ring-transparent border-0: dialog.tsx 기본 ring-1 ring-foreground/10을 없애 그림자만 남긴다
        - shadow-[var(--shadow-lg)], p-7(28px), sm:max-w-[380px]
      */}
      <DialogContent
        showCloseButton={false}
        className="sm:max-w-[380px] !bg-[var(--paper)] p-7 ring-0 ring-transparent border-0 shadow-[var(--shadow-lg)]"
      >
        {/* 아이콘, 제목, 설명을 세로로 가운데 정렬한다 */}
        <DialogHeader className="items-center text-center gap-3">
          {icon && (
            <div style={{
              width: 56, height: 56, borderRadius: '50%',
              background: icon.bgColor,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              flexShrink: 0,
            }}>
              <Icon name={icon.name} size={24} color={icon.color} />
            </div>
          )}
          {/* 제목: 18px/800 */}
          <DialogTitle className="leading-snug" style={{ fontSize: 18, fontWeight: 800 }}>
            {title}
          </DialogTitle>
          {description && (
            <DialogDescription className="text-sm leading-relaxed text-center">
              {description}
            </DialogDescription>
          )}
        </DialogHeader>

        {/* 추가 콘텐츠 슬롯(사유 입력 textarea 등) */}
        {children && <div style={{ marginTop: 8 }}>{children}</div>}

        {/*
          DialogFooter 기본 클래스의 -mx-4 -mb-4, bg-muted/50, border-t, rounded-b-xl,
          sm:flex-row sm:justify-end를 모두 덮어쓰고 버튼 2개를 flex:1로 나눠 카드 폭을 채운다.
        */}
        <DialogFooter
          className="flex-row justify-center gap-3 mt-4 border-t-0 bg-transparent p-0 mx-0 mb-0 rounded-none"
        >
          {/* 취소 버튼(아웃라인) */}
          <button
            onClick={handleCancel}
            disabled={isDisabled}
            style={{
              flex: 1,
              padding: '14px 16px',
              borderRadius: 12,
              fontSize: 15,
              fontWeight: 700,
              background: 'var(--surface-card)',
              border: '1px solid var(--border-default)',
              color: 'var(--text-secondary)',
              cursor: isDisabled ? 'not-allowed' : 'pointer',
            }}
          >
            {cancelLabel}
          </button>
          {/* 주 버튼. 색은 tone에 따라 정한다 */}
          <button
            onClick={isDisabled ? undefined : onConfirm}
            disabled={isDisabled}
            className={isDisabled ? undefined : 'lift'}
            style={{
              flex: 1,
              padding: '14px 16px',
              borderRadius: 12,
              fontSize: 15,
              fontWeight: 800,
              background: confirmBg,
              color: 'var(--paper)',
              border: 'none',
              cursor: isDisabled ? 'not-allowed' : 'pointer',
              boxShadow: isDisabled ? 'none' : 'var(--shadow-md)',
              transition: 'background .15s',
            }}
          >
            {confirmLabel}
          </button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
