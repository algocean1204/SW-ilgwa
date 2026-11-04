// FastAPI 생성 노드를 사용자용 한국어 단계 라벨로 바꾼다.
// currentNode는 마지막으로 완료된 노드라 지금 진행 중인 다음 단계 라벨을 보여 준다.
// 매핑 근거: backend-fastapi ChapterStudio_V1 lessons_generate._NODE_PROGRESS
//   prepare_context 10% → generate_lesson 55% → content_verify 70%
//   → synthesize_audio 85% → postprocess_slides 95% → done 100%
import type { LessonGenerationStatusResponse } from '@/apis';

/** 완료된 노드명 → 지금 진행 중인 단계 라벨 */
const NEXT_STAGE_BY_COMPLETED_NODE: Record<string, string> = {
  // running 시작 마커(완료 노드 없음)면 자료 분석부터 보여 준다
  generate_chapter_state: '자료 분석 중',
  prepare_context: '슬라이드 생성 중',
  generate_lesson: '검증 중',
  content_verify: '음성 합성 중',
  synthesize_audio: '마무리 중',
  postprocess_slides: '마무리 중',
  audio_backfill: '마무리 중',
};

export interface LessonGenerationStage {
  /** 지금 진행 중인 단계 라벨. 표시할 수 없으면 null이고 챕터 단위 표시로 돌아간다 */
  label: string;
  /** 0~100 노드 단위 진행률 */
  percent: number;
}

/**
 * 생성 상태 응답에서 표시용 단계 정보를 추출한다.
 * unknown/done/failed 등 표시할 진행 단계가 없으면 null을 돌려줘
 * 호출 측이 챕터 단위 표시로 돌아가게 한다.
 */
export function resolveLessonGenerationStage(
  status: LessonGenerationStatusResponse | null,
): LessonGenerationStage | null {
  if (!status) return null;
  if (status.status === 'pending') {
    return { label: '생성 준비 중', percent: 0 };
  }
  if (status.status !== 'running') return null;
  const label = NEXT_STAGE_BY_COMPLETED_NODE[status.currentNode ?? 'generate_chapter_state']
    ?? '슬라이드 생성 중';
  // progressPercent가 NaN/Infinity면 0으로 두고 0~100으로 클램프해 NaN%가 보이지 않게 한다
  const percent = Number.isFinite(status.progressPercent)
    ? Math.min(100, Math.max(0, status.progressPercent))
    : 0;
  return { label, percent };
}
