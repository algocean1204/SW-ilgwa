// 텔레그램 메시지 미리보기 카드. 실제 텔레그램 클라이언트 화면을 본떴다.
// 색은 telegram-blue 토큰의 투명도 변형만 쓰고, 상태나 핸들러는 받지 않는다.
import { Card } from './SettingsPrimitives';

export function TelegramMessagePreview() {
  return (
    <Card title="메시지 미리보기">
      <div style={{
        padding: "14px 16px",
        borderRadius: "var(--radius-lg)",
        background: "var(--telegram-settings-bg)",
        border: "1px solid var(--telegram-settings-border)",
        maxWidth: 320,
      }}>
        <div style={{ fontSize: 10.5, fontWeight: 700, color: "var(--telegram-blue)", marginBottom: 6 }}>
          tutorflow_bot
        </div>
        <div style={{ fontSize: 12.5, color: "var(--ink)", lineHeight: 1.6 }}>
          <span role="img" aria-label="오늘의 암기노트">🌅</span> <b>오늘의 암기노트</b><br />
          ⟶ 응집도 vs 결합도<br />
          ⟶ V-모델 4단계<br /><br />
          <span role="img" aria-label="팁">💡</span> <i>오늘의 한 줄:</i> "모듈 안은 강하게, 바깥은 느슨하게"<br /><br />
          <span style={{ color: "var(--telegram-blue)", fontWeight: 700 }}>[학습하러 가기]</span>
        </div>
      </div>
    </Card>
  );
}
