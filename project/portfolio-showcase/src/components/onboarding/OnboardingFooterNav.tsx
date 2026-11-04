// 온보딩 하단의 이전, 건너뛰기, 다음(시작하기) 버튼. step 전환은 Onboarding.tsx가 한다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';

interface OnboardingFooterNavProps {
  step: number;
  totalSteps: number;
  palette: Palette;
  disabled?: boolean;
  showSkip?: boolean;
  onPrev: () => void;
  onSkip: () => void;
  onNext: () => void;
}

export function OnboardingFooterNav({ step, totalSteps, palette, disabled = false, showSkip = true, onPrev, onSkip, onNext }: OnboardingFooterNavProps) {
  return (
    <div style={{ display: "flex", gap: 10, marginTop: 28, paddingTop: 20, borderTop: "1px solid var(--line)" }}>
      {/* 이전 버튼. step 1에서는 '취소'로 표시하고 landing으로 간다 */}
      <button
        onClick={onPrev}
        disabled={disabled}
        style={{
          padding: "12px 18px", borderRadius: 11, fontSize: 13, fontWeight: 600,
          color: "var(--ink-2)", border: "1px solid var(--line)", background: "var(--paper)",
          opacity: disabled ? .58 : 1, cursor: disabled ? "not-allowed" : "pointer",
        }}
        className="lift"
      >
        {step > 1 ? <><Icon name="chevron-left" size={10} style={{ marginRight: 3 }} /> 이전</> : "취소"}
      </button>

      <div style={{ flex: 1 }} />

      {/* 건너뛰기. 마지막 step이거나 showSkip=false이면 숨긴다 */}
      {showSkip && step < totalSteps && (
        <button
          style={{ padding: "12px 16px", fontSize: 13, fontWeight: 600, color: "var(--ink-3)" }}
          onClick={onSkip}
          disabled={disabled}
        >
          건너뛰기
        </button>
      )}

      {/* 다음 버튼. 마지막 step에서는 '시작하기'가 된다 */}
      <button
        onClick={onNext}
        disabled={disabled}
        className="lift"
        style={{
          padding: "12px 22px", borderRadius: 11, fontSize: 13, fontWeight: 700,
          color: "var(--paper)", background: palette.primary,
          boxShadow: `0 8px 16px -8px ${palette.primary}`,
          opacity: disabled ? .72 : 1, cursor: disabled ? "not-allowed" : "pointer",
        }}
      >
        {step < totalSteps ? "다음" : disabled ? "저장 중..." : "시작하기"}
      </button>
    </div>
  );
}
