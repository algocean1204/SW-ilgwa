// 개인정보 탭.
// 보안 현황 카드, 학습 데이터 저장 옵션, 데이터 내보내기·삭제 버튼을 둔다.
// TODO: 실제 API 연결 시 다운로드·삭제 핸들러 구현
import type { Palette } from '@/lib';
import { Card, ToggleRow } from './SettingsPrimitives';
import StatusBadge from '@/components/ui/StatusBadge';
import Icon from '@/Icon';

interface SettingsPrivacyProps {
  palette: Palette;
}

export function SettingsPrivacy({ palette }: SettingsPrivacyProps) {
  return (
    <>
      {/* 개인정보·보안 현황 카드. 데이터 보관 기간과 보안 상태를 보여준다 */}
      <div style={{
        display: "flex", alignItems: "center", gap: 14,
        padding: "12px 16px", marginBottom: 14, borderRadius: "var(--radius-lg)",
        background: "var(--surface-subtle)", border: "1px solid var(--border-soft)",
      }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: "var(--text-primary)" }}>
              데이터 보관 기간: 무기한
            </span>
            {/* 보안 상태 배지 */}
            <StatusBadge tone="success" label="정상" size="sm" />
          </div>
          <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>
            학습 기록 84일 저장 중 · 품질 개선 기여 비활성 · PDF 보관 활성
          </div>
        </div>
        {/* 보안 항목임을 자물쇠 아이콘으로 표시한다 */}
        <Icon name="lock" size={16} color="var(--grass-primary)" />
      </div>

      <Card title="학습 데이터">
        <ToggleRow palette={palette} val={true}  title="학습 기록 저장"   desc="내 학습 흐름을 이어가기 위해 학습 데이터를 저장" />
        <ToggleRow palette={palette} val={false} title="품질 개선에 기여" desc="익명 처리된 대화를 서비스 품질 개선에 사용" />
        <ToggleRow palette={palette} val={true}  title="업로드한 PDF 보관" desc="내가 올린 교재를 재학습에 사용 가능하게 보관" />
      </Card>

      <Card title="데이터 내보내기 · 삭제">
        <div style={{ display: "flex", flexDirection: "column", gap: 10, padding: "6px 0" }}>
          <button className="lift" style={{
            padding: "12px 14px", borderRadius: "var(--radius-md)", textAlign: "left",
            background: "var(--bg)", border: "1px solid var(--line-2)",
          }}>
            <div style={{ fontSize: 12.5, fontWeight: 700, color: "var(--ink)" }}>
              <><Icon name="download" size={14} /> 학습 데이터 다운로드</>
            </div>
            <div style={{ fontSize: 11, color: "var(--ink-3)", marginTop: 2 }}>
              JSON으로 모든 노트, 퀴즈, 대화 내역을 받습니다
            </div>
          </button>
          <button className="lift" style={{
            padding: "12px 14px", borderRadius: "var(--radius-md)", textAlign: "left",
            background: "var(--bg)", border: "1px solid var(--line-2)",
          }}>
            <div style={{ fontSize: 12.5, fontWeight: 700, color: "var(--ink)" }}>
              <><Icon name="trash" size={14} /> 대화 기록 전체 삭제</>
            </div>
            <div style={{ fontSize: 11, color: "var(--ink-3)", marginTop: 2 }}>
              튜터와의 대화만 삭제 (학습 진도는 유지)
            </div>
          </button>
        </div>
      </Card>
    </>
  );
}
