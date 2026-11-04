// 튜터 선택 UI와 생성 요청이 함께 쓰는 course-explore 타입
export interface TeacherItem {
  id: string;
  n: string;
  d: string;
  photo?: string;
  isDefault: boolean;
}
