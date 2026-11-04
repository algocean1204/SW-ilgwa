// draft/confirmed 단계별 상태 배너.
// sourceType(pdf|topic)에 따라 분석 중 문구를 고른다.
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import Icon from '@/Icon';
import StatusBadge from '@/components/ui/StatusBadge';

interface PhaseBannerProps {
  phase: "draft" | "confirmed";
  palette: Palette;
  setRoute: RouteSetter;
  readOnly?: boolean;
  /** 과외 소스 유형: 'pdf' | 'topic' | '' */
  sourceType?: string;
}

export function PhaseBanner({ phase, palette, setRoute, readOnly, sourceType }: PhaseBannerProps) {
  if (phase === "draft") {
    // pdf가 아니면(topic 또는 빈 값) 주제 기반 문구를 쓴다
    const draftHeading = sourceType === 'pdf'
      ? 'PDF를 분석해 커리큘럼 '
      : '입력한 주제로 커리큘럼 ';

    return (
      <div style={{
        display: "flex", alignItems: "center", gap: 14,
        padding: "14px 20px", borderRadius: 14, marginBottom: 22,
        background: palette.wash, border: `1px solid ${palette.soft}`,
      }}>
        <div style={{
          /* 분석 아이콘 서클. 표면 배경이라 var(--paper) 토큰을 쓴다 */
          width: 36, height: 36, borderRadius: "50%", background: "var(--paper)",
          border: `1.5px solid ${palette.primary}`, display: "grid", placeItems: "center", fontSize: 18,
        }}>
          <Icon name="wand" size={18} color={palette.primary} />
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13, fontWeight: 800, color: "var(--ink)" }}>
            {draftHeading}<span style={{ color: palette.primary }}>초안</span>을 만들었어요
          </div>
          <div style={{ fontSize: 12, color: "var(--ink-3)", marginTop: 2 }}>
            검토하고 필요한 부분만 확정하세요. 확정하면 음성 강의 생성이 시작돼요 (약 4분 소요).
          </div>
        </div>
        {/* DRAFT 배지는 공용 StatusBadge를 쓴다 */}
        <StatusBadge tone="info" label="DRAFT" size="sm" />
      </div>
    );
  }

  if (readOnly) {
    return (
      <div style={{
        display: "flex", alignItems: "center", gap: 14,
        padding: "14px 20px", borderRadius: 14, marginBottom: 22,
        background: palette.wash, border: `1px solid ${palette.soft}`,
      }}>
        <Icon name="book" size={20} color={palette.primary} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13, fontWeight: 800, color: "var(--ink)" }}>
            확정된 커리큘럼
          </div>
          <div style={{ fontSize: 12, color: "var(--ink-3)", marginTop: 2 }}>
            해제된 챕터를 눌러 강의를 이어서 학습하세요
          </div>
        </div>
      </div>
    );
  }

  return (
    /* 확정 배너는 grass 팔레트 토큰을 쓴다 */
    <div style={{
      display: "flex", alignItems: "center", gap: 14,
      padding: "14px 20px", borderRadius: 14, marginBottom: 22,
      background: "var(--grass-wash)", border: "1px solid var(--grass-soft)",
    }}>
      <Icon name="check" size={22} color="var(--grass-ink)" strokeWidth={2.4} />
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 13, fontWeight: 800, color: "var(--grass-ink)" }}>
          확정 완료 · 음성 강의를 만들고 있어요
        </div>
        <div style={{ fontSize: 12, color: "var(--grass-accent)", marginTop: 2 }}>
          첫 번째 챕터는 약 1분 뒤 바로 학습 시작 가능 · 나머지는 백그라운드 생성
        </div>
      </div>
      <button onClick={() => setRoute("learning")} style={{
        padding: "8px 14px", borderRadius: 9, fontSize: 12, fontWeight: 800,
        background: "var(--grass-ink)", color: "var(--paper)",
      }}>
        바로 학습 시작
      </button>
    </div>
  );
}
