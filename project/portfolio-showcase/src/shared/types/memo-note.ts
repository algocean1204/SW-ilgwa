// 암기노트 타입 (MemoryNotes의 NOTES 배열 구조)

/** 노트 종류 */
export type NoteKind = 'qa' | 'bullet' | 'code' | 'compare';

/** 비교 표 행: [항목명, A값, B값] */
export type CompareRow = [string, string, string];

/** 암기노트 기본 구조 */
export interface MemoNote {
  id: string;
  topic: string;
  topicLabel: string;
  kind: NoteKind;
  title: string;
  body: string;
  /** 태그 (예: "핵심", "개념") */
  tag: string;
  /** 즐겨찾기 여부 */
  starred?: boolean;
  /** 생성 날짜 (예: "오늘", "어제") */
  date: string;
  /** 타임스탬프 (예: "18'12\"", "—") */
  stamp: string;
  /** 출처 강의 (예: "3강 · 요구공학") */
  lesson: string;
  /** 노트가 속한 챕터 ID. 별표 토글 API에 필요하다. */
  chapterId?: string;
  /** kind=qa 또는 bullet 상세 설명 */
  detail?: string;
  /** kind=bullet 목록 항목 */
  bullets?: string[];
  /** kind=code 코드 문자열 */
  code?: string;
  /** kind=compare 비교 표 데이터 */
  compare?: CompareRow[];
}

/** 주제 필터 항목 */
export interface NoteTopic {
  id: string;
  label: string;
  count: number;
}
