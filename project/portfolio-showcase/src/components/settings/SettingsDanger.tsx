// 계정 삭제 탭. 경고 배너, 상세 경고, 삭제 요청 버튼을 둔다.
// TODO: 실제 API 연결 시 계정 삭제 확인 모달과 요청 핸들러 구현
import { Card } from './SettingsPrimitives';
import Icon from '@/Icon';

export function SettingsDanger() {
  return (
    <>
      {/* 상단 경고 배너. 되돌릴 수 없는 작업임을 먼저 알린다 */}
      <div style={{
        display: "flex", alignItems: "center", gap: 12,
        padding: "12px 16px", marginBottom: 14, borderRadius: "var(--radius-lg)",
        background: "var(--warn-bg)", border: "1px solid var(--warn-border)",
      }}>
        <Icon name="warning" size={16} color="var(--warn-primary)" />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "var(--warn-primary)", marginBottom: 2 }}>
            계정 삭제 — 되돌릴 수 없는 작업입니다
          </div>
          <div style={{ fontSize: 11, color: "var(--warn-ink)" }}>
            모든 학습 기록, 퀴즈, 암기노트가 영구 삭제되며 복구할 수 없습니다
          </div>
        </div>
      </div>

      <Card title="계정 삭제" subtitle="삭제하면 모든 학습 기록이 영구 삭제됩니다. 복구할 수 없어요.">
        {/* 소프트 경고 배너. 복숭아 계열 --warn-* 토큰을 쓴다 */}
        <div style={{
          padding: "16px 18px", borderRadius: "var(--radius-lg)",
          background: "var(--warn-bg)", border: "1px solid var(--warn-border)", marginBottom: 14,
        }}>
          <div style={{ display: "flex", gap: 12 }}>
            <div><Icon name="warning" size={18} /></div>
            <div style={{ fontSize: 12, color: "var(--warn-ink)", lineHeight: 1.6 }}>
              <b>삭제되는 데이터:</b><br />
              · 학습 기록 84일치, 퀴즈 312개, 암기노트 128장<br />
              · 업로드한 PDF 4개, 튜터 대화 1,204개<br />
              · 구독 정보 (결제는 즉시 중단됩니다)
            </div>
          </div>
        </div>
        <button className="lift" style={{
          padding: "11px 18px", borderRadius: "var(--radius-md)", fontSize: 12.5, fontWeight: 800,
          // 카드 표면 버튼이라 var(--paper)를 쓴다
          background: "var(--paper)",
          color: "var(--warn-primary)", border: `1.5px solid var(--warn-primary)`,
        }}>계정 삭제 요청</button>
      </Card>
    </>
  );
}
