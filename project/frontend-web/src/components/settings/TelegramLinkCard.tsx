// 텔레그램 연동 링크 카드.
// 상태 표시, 링크 생성 버튼, 발급된 링크, 상태 새로고침을 둔다.
// 로직 없이 props로 받은 상태와 핸들러만 그린다.
import type { TelegramLinkResponse, TelegramStatusResponse } from '@/apis';
import type { Palette } from '@/lib';
import Icon from '@/Icon';
import { Card } from './SettingsPrimitives';
import { displayHandle, formatDateTime } from './telegramHelpers';

interface TelegramLinkCardProps {
  palette: Palette;
  status: TelegramStatusResponse | null;
  linkInfo: TelegramLinkResponse | null;
  isLoading: boolean;
  isLinking: boolean;
  isLinked: boolean;
  onCreateLink: () => void;
  onRefresh: () => void;
}

/** 레이블-값 쌍을 2컬럼 그리드로 나열하는 상태 행 */
function StatusLine({ label, value }: { label: string; value: string }) {
  return (
    <div style={{
      display: "grid",
      gridTemplateColumns: "120px 1fr",
      gap: 12,
      padding: "10px 0",
      borderTop: "1px solid var(--line-2)",
      fontSize: 12,
    }}>
      <span style={{ color: "var(--ink-3)", fontWeight: 700 }}>{label}</span>
      <span style={{ color: "var(--ink)", fontWeight: 700, wordBreak: "break-word" }}>{value}</span>
    </div>
  );
}

export function TelegramLinkCard({
  palette,
  status,
  linkInfo,
  isLoading,
  isLinking,
  isLinked,
  onCreateLink,
  onRefresh,
}: TelegramLinkCardProps) {
  return (
    <Card title="텔레그램 연동" subtitle="암기노트와 퀴즈를 텔레그램 채팅창에서 받을 수 있습니다">
      {/* 아바타 + 계정명 + 연동/연결됨 버튼 */}
      <div style={{
        padding: "18px 20px", borderRadius: "var(--radius-lg)",
        background: `linear-gradient(155deg, ${palette.wash}, var(--paper) 80%)`,
        border: `1px solid ${palette.soft}`, marginBottom: 16,
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{
            width: 44, height: 44, borderRadius: "50%",
            background: "var(--telegram-blue)", display: "grid", placeItems: "center",
            flexShrink: 0,
          }}>
            <Icon name="telegram" size={20} color="var(--paper)" />
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 13, fontWeight: 800, color: "var(--ink)" }}>
              {isLoading ? '텔레그램 상태 확인 중' : displayHandle(status)}
            </div>
            <div style={{ fontSize: 11, color: "var(--ink-3)", marginTop: 2, lineHeight: 1.5 }}>
              {isLinked
                ? `연동일: ${formatDateTime(status?.linkedAt)}`
                : '봇 링크를 생성하면 5분 동안 사용할 수 있는 일회성 토큰이 발급됩니다'}
            </div>
          </div>
          {isLinked ? (
            <button
              disabled
              style={{
                padding: "8px 14px", borderRadius: "var(--radius-md)", fontSize: 11.5,
                fontWeight: 700, background: "var(--paper)", color: "var(--ink-3)",
                border: "1px solid var(--line)", cursor: "not-allowed",
              }}
            >
              연결됨
            </button>
          ) : (
            <button
              onClick={onCreateLink}
              disabled={isLoading || isLinking}
              style={{
                padding: "8px 14px", borderRadius: "var(--radius-md)", fontSize: 11.5,
                fontWeight: 800, background: "var(--telegram-blue)", color: "var(--paper)",
                border: "1px solid var(--telegram-blue)",
                cursor: isLoading || isLinking ? "wait" : "pointer",
                opacity: isLoading || isLinking ? .65 : 1,
              }}
              className="lift"
            >
              {isLinking ? '생성 중...' : '연동 링크 생성'}
            </button>
          )}
        </div>
      </div>

      {/* 상태 요약 행 목록 */}
      <StatusLine label="연동 상태" value={isLoading ? '확인 중' : isLinked ? '연동됨' : '미연동'} />
      <StatusLine label="텔레그램 계정" value={displayHandle(status)} />
      <StatusLine label="Chat ID" value={status?.telegramChatId ? String(status.telegramChatId) : '기록 없음'} />
      <StatusLine label="마지막 활동" value={formatDateTime(status?.lastActiveAt)} />

      {/* 발급된 연동 링크. 미연동이고 linkInfo가 있을 때만 보인다 */}
      {linkInfo && !isLinked && (
        <div style={{
          marginTop: 16, padding: "14px 16px", borderRadius: "var(--radius-lg)",
          background: "var(--telegram-settings-bg)", border: "1px solid var(--telegram-settings-border)",
        }}>
          <div style={{ fontSize: 12.5, fontWeight: 800, color: "var(--ink)", marginBottom: 8 }}>
            발급된 연동 링크
          </div>
          <div style={{ display: "grid", gap: 8, fontSize: 11.5, color: "var(--ink-2)" }}>
            <div><b>토큰</b> {linkInfo.linkToken}</div>
            <div><b>만료</b> {formatDateTime(linkInfo.expiresAt)}</div>
            <a
              href={linkInfo.telegramBotUrl}
              target="_blank"
              rel="noreferrer"
              style={{
                display: "inline-flex", alignItems: "center", justifyContent: "center",
                gap: 6, width: "fit-content", marginTop: 4, padding: "8px 12px",
                borderRadius: "var(--radius-md)", background: "var(--telegram-blue)",
                color: "var(--paper)", fontSize: 11.5, fontWeight: 800,
              }}
            >
              <Icon name="telegram" size={13} color="var(--paper)" />
              텔레그램 봇 열기
            </a>
          </div>
        </div>
      )}

      {/* 상태 새로고침 버튼 */}
      <div style={{ marginTop: 14, display: "flex", justifyContent: "flex-end" }}>
        <button
          onClick={onRefresh}
          disabled={isLoading}
          style={{
            padding: "8px 12px", borderRadius: "var(--radius-md)", border: "1px solid var(--line)",
            background: "var(--paper)", color: "var(--ink-2)", fontSize: 11.5,
            fontWeight: 800, cursor: isLoading ? "wait" : "pointer",
          }}
        >
          상태 새로고침
        </button>
      </div>
    </Card>
  );
}
