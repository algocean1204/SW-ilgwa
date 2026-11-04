import type { RouteKey } from '@/shared/types';

export interface WorkspaceZone {
  id: string;
  label: string;
  icon: string;
  primaryRoute: RouteKey;
  routes: RouteKey[];
}

export interface WorkspaceRouteMeta {
  label: string;
  note: string;
  icon: string;
}

export const WORKSPACE_ZONES: WorkspaceZone[] = [
  {
    // 학습실. 과제(myHomework)와 모의고사(aiMockExam)도 여기에 둔다.
    id: 'study',
    label: '학습실',
    icon: 'book',
    primaryRoute: 'home',
    routes: ['home', 'myCourses', 'myHomework', 'aiMockExam', 'explore', 'courseDetail'],
  },
  {
    // 자료실: 암기노트, 캘린더
    id: 'archive',
    label: '자료실',
    icon: 'note',
    primaryRoute: 'notes',
    routes: ['notes', 'calendar'],
  },
  {
    // 리포트: 학습량·약점 추적
    id: 'progress',
    label: '리포트',
    icon: 'chart',
    primaryRoute: 'report',
    routes: ['report'],
  },
  {
    id: 'system',
    label: '관리',
    icon: 'settings',
    primaryRoute: 'tutor',
    routes: ['tutor', 'pricing', 'settings'],
  },
];

export const WORKSPACE_ROUTE_META: Partial<Record<RouteKey, WorkspaceRouteMeta>> = {
  home: { label: '오늘', note: '이어갈 수업과 복습 흐름', icon: 'home' },
  learning: { label: '수업', note: '음성 수업과 질의응답', icon: 'play' },
  explore: { label: '새 과외', note: 'PDF 또는 주제로 시작', icon: 'plus' },
  myCourses: { label: '내 과외', note: '진행 중인 과정 관리', icon: 'cards' },
  notes: { label: '암기노트', note: '복습 카드와 텔레그램 전송', icon: 'book' },
  calendar: { label: '캘린더', note: '학습 일정과 마감일', icon: 'calendar' },
  myHomework: { label: '과제', note: '제출과 피드백 확인', icon: 'file' },
  report: { label: '리포트', note: '학습량과 약점 추적', icon: 'chart' },
  mockExam: { label: '모의고사', note: '실전 문제 세트 생성', icon: 'target' },
  aiMockExam: { label: '실전 모의고사', note: 'AI 맞춤 문제 세트 생성', icon: 'brain' },
  tutor: { label: '튜터', note: '말투와 수업 방식 조정', icon: 'owl' },
  pricing: { label: '구독', note: '생성권과 플랜 관리', icon: 'credit' },
  settings: { label: '설정', note: '계정과 알림 환경', icon: 'settings' },
};

export function findWorkspaceZone(route: RouteKey): WorkspaceZone {
  return WORKSPACE_ZONES.find((zone) => zone.routes.includes(route)) ?? WORKSPACE_ZONES[0]!;
}
