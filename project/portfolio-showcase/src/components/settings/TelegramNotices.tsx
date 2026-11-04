// 텔레그램 설정의 에러/성공 알림 배너.
// 에러 배너: role="alert", 다시 시도 버튼 포함. 성공 배너: role="status".
// 메시지가 null이면 해당 배너를 렌더링하지 않는다.
interface TelegramNoticesProps {
  errorMessage: string | null;
  successMessage: string | null;
  onRetry: () => void;
}

export function TelegramNotices({ errorMessage, successMessage, onRetry }: TelegramNoticesProps) {
  return (
    <>
      {errorMessage && (
        <div
          role="alert"
          style={{
            marginBottom: 14, padding: "12px 14px", borderRadius: "var(--radius-md)",
            background: "var(--state-danger-bg)", border: "1px solid var(--coral)",
            color: "var(--danger)", fontSize: 12.5, fontWeight: 700,
            display: "flex", alignItems: "center", gap: 10,
          }}
        >
          <span style={{ flex: 1 }}>{errorMessage}</span>
          <button
            onClick={onRetry}
            style={{ fontSize: 11.5, fontWeight: 800, color: "var(--danger)" }}
          >
            다시 시도
          </button>
        </div>
      )}
      {successMessage && (
        <div
          role="status"
          style={{
            marginBottom: 14, padding: "12px 14px", borderRadius: "var(--radius-md)",
            background: "var(--telegram-settings-bg)", border: "1px solid var(--telegram-settings-border)",
            color: "var(--telegram-blue)", fontSize: 12.5, fontWeight: 800,
          }}
        >
          {successMessage}
        </div>
      )}
    </>
  );
}
