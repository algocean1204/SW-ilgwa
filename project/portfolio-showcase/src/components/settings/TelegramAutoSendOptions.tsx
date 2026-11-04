// 텔레그램 자동 전송 옵션 카드. 연동되면 서버에 저장되는 ToggleRow를,
// 미연동이면 '연동 필요' 배지가 붙은 ReadonlyOptionRow를 보여준다.
// 낙관적 토글, 롤백, linked 확인은 부모(SettingsTelegram)가 하고 여기서는 그리기만 한다.
import type { TelegramPreferencesResponse } from '@/apis';
import type { Palette } from '@/lib';
import { Card, ToggleRow } from './SettingsPrimitives';

interface TelegramAutoSendOptionsProps {
  palette: Palette;
  isLinked: boolean;
  prefs: TelegramPreferencesResponse | null;
  onToggleNote: (v: boolean) => void;
  onToggleQuiz: (v: boolean) => void;
  onToggleFriend: (v: boolean) => void;
}

/** 미연동 상태에서 변경 불가임을 '연동 필요' 배지로 표시하는 읽기 전용 행 */
function ReadonlyOptionRow({
  title,
  desc,
  enabled,
}: {
  title: string;
  desc: string;
  enabled: boolean;
}) {
  return (
    <div style={{
      display: "flex",
      alignItems: "center",
      gap: 14,
      padding: "14px 0",
      borderTop: "1px solid var(--line-2)",
    }}>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 12.5, fontWeight: 700, color: "var(--ink)" }}>{title}</div>
        <div style={{ fontSize: 11, color: "var(--ink-3)", marginTop: 3, lineHeight: 1.5 }}>{desc}</div>
      </div>
      <span style={{
        padding: "5px 8px",
        borderRadius: "var(--radius-sm)",
        background: enabled ? "var(--telegram-settings-bg)" : "var(--sand)",
        border: `1px solid ${enabled ? "var(--telegram-settings-border)" : "var(--line)"}`,
        color: enabled ? "var(--telegram-blue)" : "var(--ink-3)",
        fontSize: 10.5,
        fontWeight: 800,
        whiteSpace: "nowrap",
      }}>
        {enabled ? '사용 가능' : '연동 필요'}
      </span>
    </div>
  );
}

export function TelegramAutoSendOptions({
  palette,
  isLinked,
  prefs,
  onToggleNote,
  onToggleQuiz,
  onToggleFriend,
}: TelegramAutoSendOptionsProps) {
  const subtitle = isLinked
    ? '자동 전송 옵션은 변경 즉시 서버에 저장됩니다'
    : '텔레그램을 먼저 연동하면 자동 전송 옵션을 설정할 수 있습니다';

  return (
    <Card title="텔레그램 메시지 옵션" subtitle={subtitle}>
      {isLinked ? (
        // 연동 완료. 서버에 저장되는 토글이며 prefs 로드 전에는 기본값(false)을 보여준다.
        <>
          <ToggleRow
            palette={palette}
            val={prefs?.noteEnabled ?? false}
            title="암기노트 전송"
            desc="노트 화면에서 생성한 암기노트를 텔레그램으로 전송합니다"
            onToggle={onToggleNote}
          />
          <ToggleRow
            palette={palette}
            val={prefs?.quizEnabled ?? false}
            title="퀴즈 알림"
            desc="복습 퀴즈를 텔레그램 알림으로 받습니다"
            onToggle={onToggleQuiz}
          />
          <ToggleRow
            palette={palette}
            val={prefs?.friendEnabled ?? false}
            title="친구 공유"
            desc="친구가 공유한 학습 자료를 텔레그램으로 받습니다"
            onToggle={onToggleFriend}
          />
        </>
      ) : (
        // 미연동이면 바꿀 수 없음을 '연동 필요' 배지로 알린다
        <>
          <ReadonlyOptionRow
            enabled={false}
            title="암기노트 전송"
            desc="노트 화면에서 생성한 암기노트를 텔레그램으로 전송합니다"
          />
          <ReadonlyOptionRow
            enabled={false}
            title="퀴즈 알림"
            desc="복습 퀴즈를 텔레그램 알림으로 받습니다"
          />
          <ReadonlyOptionRow
            enabled={false}
            title="친구 공유"
            desc="친구가 공유한 학습 자료를 텔레그램으로 받습니다"
          />
        </>
      )}
    </Card>
  );
}
