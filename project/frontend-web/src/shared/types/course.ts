// 과외(코스) 카드 UI 타입

/** AI 뷰어 챕터 단건 구조 */
export interface Chapter {
  id: number;
  title: string;
  sub: string;
  /** 챕터 진행 상태 */
  state: 'done' | 'active' | 'wait';
}

/** AI 뷰어 북마크 단건 구조 */
export interface Bookmark {
  id: string;
  label: string;
}

/** 과제 현황 */
export interface HomeworkStatus {
  pending: number;
  grading: number;
  done: number;
}

/** 과외 카드 기본 구조 */
export interface Course {
  id: string;
  title: string;
  /** 부제목 (예: "2026 · 30일 합격반") */
  sub: string;
  tutor: string;
  tutorIcon: string;
  /** 카드 배경 색상 (hex) */
  color: string;
  /** 강조 색상 (hex) */
  accent: string;
  /** 학습 진도율 (0~100) */
  progress: number;
  /** D-Day (남은 일수) */
  dDay: number;
  chapters: number;
  slidesDone: number;
  slidesTotal: number;
  homework: HomeworkStatus;
  /** 마지막 학습 시각 (예: "어제 18:42") */
  lastAt: string;
}
