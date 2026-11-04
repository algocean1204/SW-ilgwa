// 하단 sticky 확정 바. draft 단계에서만 보인다.
// 커리큘럼 요약, 확정 버튼, 다시 고르기 이동을 둔다.
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
// 되돌릴 수 없는 확정 액션이라 호버 빛줄기 버튼으로 강조한다
import { ShimmerButton } from '@/components/animated';

interface ConfirmBarProps {
  palette: Palette;
  chapterCount: number;
  totalMin: number;
  totalSlides: number;
  onConfirm: () => void;
  setRoute: RouteSetter;
}

export function ConfirmBar({
  palette, chapterCount, totalMin, totalSlides, onConfirm, setRoute,
}: ConfirmBarProps) {
  return (
    <div style={{
      position: "sticky", bottom: 16, zIndex: 5,
      padding: "16px 22px", borderRadius: 16,
      /* var(--ink) 다크 배경 위에 페이퍼 색 글자 */
      background: "var(--ink)", color: "var(--paper)",
      display: "flex", alignItems: "center", gap: 18,
      boxShadow: "0 20px 40px -18px rgba(0,0,0,.4)",
    }}>
      {/* 커리큘럼 집계 요약 */}
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 13, fontWeight: 800 }}>
          {chapterCount}개 챕터 · 약 {Math.round(totalMin / 60 * 10) / 10}시간 · {totalSlides}개 슬라이드
        </div>
        <div style={{ fontSize: 12, opacity: .7, marginTop: 2 }}>
          확정 후에는 음성 강의 생성이 시작되며, 추가/삭제는 편집 모드에서만 가능합니다.
        </div>
      </div>

      {/* 다시 고르기: 탐색 페이지로 이동 */}
      <button onClick={() => setRoute("explore")} style={{
        padding: "10px 16px", borderRadius: 9, fontSize: 12, fontWeight: 700,
        background: "var(--white-overlay-sm)", color: "var(--paper)",
        border: "1px solid var(--telegram-overlay-soft)",
      }}>
        다시 고르기
      </button>

      {/* 커리큘럼 확정과 강의 생성 시작 */}
      <ShimmerButton
        onClick={onConfirm}
        shimmerColor="rgba(255,255,255,0.18)"
        style={{
          padding: "12px 20px", borderRadius: 9, fontSize: 13, fontWeight: 800,
          background: palette.primary, color: "var(--paper)",
          boxShadow: `0 10px 22px -10px ${palette.primary}`,
        }}
      >
        이대로 확정하고 강의 생성 시작
      </ShimmerButton>
    </div>
  );
}
