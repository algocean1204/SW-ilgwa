// 과제 화면에서 쓰는 UI 타입

/** 과제 상태 */
export type AssignmentStatus = 'pending' | 'grading' | 'done';

/** 루브릭 평가 항목 */
export interface RubricItem {
  /** 항목명 (예: "핵심 개념 파악") */
  k: string;
  /** 획득 점수 */
  score: number;
  /** 최고 점수 */
  max: number;
}

/** 과제 */
export interface Assignment {
  id: string;
  /** 과외 id (Course.id 참조) */
  courseId: string;
  title: string;
  status: AssignmentStatus;
  /** 점수 (채점 완료 시에만 존재) */
  score?: number;
  /** 제출 시각 (예: "어제 21:14") */
  submittedAt?: string;
  /** 채점 완료 시각 */
  gradedAt?: string;
  /** 마감 시각 (pending 상태일 때) */
  due?: string;
  /** 항목별 루브릭 점수 (done 상태) */
  rubric?: RubricItem[];
  /** 잘한 점 목록 */
  strengths?: string[];
  /** 개선할 점 목록 */
  improvements?: string[];
  /** AI 총평 */
  overall?: string;
}
