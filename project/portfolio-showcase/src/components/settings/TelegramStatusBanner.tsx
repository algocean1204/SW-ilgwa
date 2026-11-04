// 텔레그램 연결 현황 배너.
// 연동 여부, 핸들, 마지막 활동 시각을 한 줄로 요약한다.
import type { TelegramStatusResponse } from '@/apis';
import Icon from '@/Icon';
import { displayHandle, formatDateTime } from './telegramHelpers';

interface TelegramStatusBannerProps {
  status: TelegramStatusResponse | null;
  isLoading: boolean;
  isLinked: boolean;
}

export function TelegramStatusBanner({ status, isLoading, isLinked }: TelegramStatusBannerProps) {
  const title = isLoading ? '상태 확인 중...' : displayHandle(status);
  const description = isLinked
    ? `마지막 활동: ${formatDateTime(status?.lastActiveAt)}`
    : '연동 링크를 생성한 뒤 텔레그램 봇에서 연결을 완료해주세요';

  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "12px 16px", marginBottom: 14, borderRadius: "var(--radius-lg)",
      background: "var(--surface-subtle)", border: "1px solid var(--border-soft)",
    }}>
      <div style={{
        width: 8, height: 8, borderRadius: "50%", flexShrink: 0,
        background: isLinked ? "var(--grass-primary)" : "var(--text-muted)",
      }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: "var(--text-primary)", marginBottom: 2 }}>
          {title}
        </div>
        <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>
          {description}
        </div>
      </div>
      <Icon name="telegram" size={16} color="var(--telegram-blue)" />
    </div>
  );
}
