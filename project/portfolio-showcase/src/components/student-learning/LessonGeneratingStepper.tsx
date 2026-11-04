// 강의 생성 단계 스테퍼. LessonGeneratingProgress에서 분리한 표시 전용 컴포넌트다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';

/** 진행 스테퍼에 표시할 단계 목록 */
export const GENERATION_STEPS = [
  { key: 'analyze', label: '주제 분석 완료' },
  { key: 'curriculum', label: '커리큘럼 확정' },
  { key: 'generating', label: 'AI 강의 슬라이드 생성 중' },
  { key: 'done', label: '학습 시작 가능' },
] as const;

export type StepKey = typeof GENERATION_STEPS[number]['key'];

/** 챕터 수 기반으로 현재 단계 키 결정 */
export function resolveCurrentStep(readyChapters: number, total: number): StepKey {
  if (total > 0 && readyChapters >= total) return 'done';
  if (readyChapters > 0) return 'generating';
  return 'curriculum';
}

interface LessonGeneratingStepperProps {
  currentStep: StepKey;
  palette: Palette;
}

/** 완료·현재·대기 상태를 원과 연결선으로 표시한다 */
export function LessonGeneratingStepper({ currentStep, palette }: LessonGeneratingStepperProps) {
  const stepKeys: StepKey[] = ['analyze', 'curriculum', 'generating', 'done'];
  const currentIdx = stepKeys.indexOf(currentStep);

  return (
    <div style={{ display: 'flex', gap: 0, marginBottom: 28, alignItems: 'flex-start' }}>
      {GENERATION_STEPS.map((step, idx) => {
        const stepIdx = stepKeys.indexOf(step.key);
        const isDone = stepIdx < currentIdx;
        const isActive = stepIdx === currentIdx;
        const isFuture = stepIdx > currentIdx;

        return (
          <div key={step.key} style={{ display: 'flex', alignItems: 'flex-start' }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 72 }}>
              <div style={{
                width: 28, height: 28, borderRadius: '50%',
                background: isDone ? palette.primary : isActive ? palette.wash : 'var(--sand-2)',
                border: isActive ? `2px solid ${palette.primary}` : isDone ? 'none' : '2px solid var(--line)',
                display: 'grid', placeItems: 'center', flexShrink: 0,
              }}>
                {isDone ? (
                  <Icon name="check" size={12} color="var(--paper)" />
                ) : (
                  <span style={{
                    fontSize: 11, fontWeight: 800,
                    color: isActive ? palette.primary : 'var(--ink-3)',
                  }}>{idx + 1}</span>
                )}
              </div>
              <div style={{
                marginTop: 6, fontSize: 10, fontWeight: isActive ? 800 : 600,
                color: isFuture ? 'var(--ink-3)' : isActive ? palette.primary : 'var(--ink-2)',
                lineHeight: 1.3, textAlign: 'center', maxWidth: 65,
              }}>
                {step.label}
              </div>
            </div>

            {/* 마지막 단계에는 연결선이 없다 */}
            {idx < GENERATION_STEPS.length - 1 && (
              <div style={{
                height: 2, width: 24, marginTop: 13, flexShrink: 0,
                background: isDone ? palette.primary : 'var(--sand-2)',
                transition: 'background .4s ease',
              }} />
            )}
          </div>
        );
      })}
    </div>
  );
}
