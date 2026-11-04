// 커리큘럼 생성 단계 스테퍼와 resolveStepFromElapsed, formatElapsed 유틸.
import type { Palette } from '@/lib';
import Icon from '@/Icon';

/** 커리큘럼 생성 단계 정의 */
export const CURRICULUM_STEPS = [
  { key: 'analyzing', label: '주제 분석' },
  { key: 'designing', label: '커리큘럼 설계' },
  { key: 'building', label: '챕터 생성' },
  { key: 'done', label: '완료' },
] as const;

export type CurriculumStepKey = typeof CURRICULUM_STEPS[number]['key'];

/** 백엔드에 상세 진행 API가 없어 경과 시간으로 현재 단계를 추정한다 */
export function resolveStepFromElapsed(elapsedMs: number, isNearComplete: boolean): CurriculumStepKey {
  if (isNearComplete) return 'building';
  // 15초 미만: 주제 분석 / 30초 미만: 커리큘럼 설계 / 이후: 챕터 생성
  if (elapsedMs < 15_000) return 'analyzing';
  if (elapsedMs < 30_000) return 'designing';
  return 'building';
}

/** 경과 시간을 "X분 Y초 경과" 형식으로 반환 */
export function formatElapsed(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  if (min === 0) return `${sec}초 경과`;
  return `${min}분 ${sec}초 경과`;
}

interface CurriculumStepperProps {
  currentStep: CurriculumStepKey;
  palette: Palette;
}

/** 완료/현재/대기 상태를 원과 연결선으로 표시한다 */
export function CurriculumStepper({ currentStep, palette }: CurriculumStepperProps) {
  const stepKeys: CurriculumStepKey[] = ['analyzing', 'designing', 'building', 'done'];
  const currentIdx = stepKeys.indexOf(currentStep);

  return (
    <div style={{ display: 'flex', gap: 0, marginBottom: 28, alignItems: 'flex-start' }}>
      {CURRICULUM_STEPS.map((step, idx) => {
        const stepIdx = stepKeys.indexOf(step.key);
        const isDone = stepIdx < currentIdx;
        const isActive = stepIdx === currentIdx;
        const isFuture = stepIdx > currentIdx;

        return (
          <div key={step.key} style={{ display: 'flex', alignItems: 'flex-start' }}>
            {/* 단계 원 + 라벨 */}
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
            {/* 연결선. 마지막 단계에는 없다 */}
            {idx < CURRICULUM_STEPS.length - 1 && (
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
