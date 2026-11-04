// 과외 상태 상수. GlobalSidebar, StudentHome 등이 같은 집합을 쓴다.

/**
 * 미확정(draft) 상태 집합.
 * 이 상태의 과외를 클릭하면 학습실 대신 커리큘럼 확인 화면으로 라우팅한다.
 */
export const DRAFT_STATUSES = new Set([
  'CURRICULUM_GENERATING',
  'CURRICULUM_READY',
  'CURRICULUM_FAILED',
]);
