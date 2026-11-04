// lesson-editor 컴포넌트들이 같이 쓰는 타입

/** 강의 구조 아이템. 챕터 또는 레슨이다. */
export interface LessonItem {
  id: string;
  kind: "chapter" | "lesson";
  label: string;
  state?: string;
}

/** 레슨 설정 행 컴포넌트 props */
export interface LE_RowProps {
  label: string;
  children: React.ReactNode;
}

/** 레슨 난이도/선택 버튼 컴포넌트 props */
export interface LE_PillProps {
  children: React.ReactNode;
  active?: boolean;
  palette: import('@/lib').Palette;
}
