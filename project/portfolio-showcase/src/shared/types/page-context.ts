// 페이지별 컨텍스트 사이드바 기능 목록 타입.
// 글로벌 사이드바와 번갈아 열리는 컨텍스트 사이드바에 각 페이지 기능을 앵커처럼 보인다.

import type { RouteKey } from './route'

/**
 * 기능 항목 종류.
 * - 'anchor': DOM 스크롤 앵커 (기본값)
 * - 'filter': 페이지 내 필터 상태 전환 (예: 내 과제 과외 필터)
 * - 'subview': 해당 섹션만 전체 화면으로 보이는 서브뷰 (정보가 많은 페이지용)
 * - 'modal': AppState.activeModal 키를 활성화해 모달/시트를 여는 항목
 */
export type PageContextFeatureType = 'anchor' | 'filter' | 'subview' | 'modal'

// 기능 항목. 아이콘·라벨·설명·클릭 핸들러로 이뤄진다.
export interface PageContextFeature {
  /** 기능 고유 ID. DOM 앵커나 상태 참조에 쓴다. */
  id: string
  /** Icon 컴포넌트에 전달할 name 문자열 */
  icon: string
  /** 사이드바에 표시할 기능 이름 */
  label: string
  /** 부연 설명 (선택) */
  description?: string
  /**
   * 기능 항목 종류. 생략하면 'anchor'로 동작한다.
   */
  type?: PageContextFeatureType
  /**
   * type === 'filter'일 때 쓰는 필터 식별자.
   * AppState.activeHomeworkFilter와 비교해 활성 상태를 정한다.
   */
  filterValue?: string
  /**
   * 오른쪽에 작게 표시할 숫자 배지 (예: 과제 개수).
   * 주로 type === 'filter' 항목에서 쓴다.
   */
  count?: number
  /**
   * 클릭 핸들러. 없으면 navigateTo, scrollIntoView 순으로 폴백한다.
   * 별도 동작이 필요할 때만 넣는다.
   */
  onClick?: () => void
  /**
   * anchor 타입에서 다른 페이지로 이동할 대상 라우트.
   * 있으면 scrollIntoView 대신 setRoute를 호출한다.
   */
  navigateTo?: RouteKey
  /** type === 'modal'일 때 열 모달 키 */
  modalKey?: string
  /** 모달 크기/위치 힌트: 'sheet-right-480' | 'sheet-right-560' | 'sheet-bottom' | 'dialog-sm' */
  modalSize?: string
}

// 페이지 컨텍스트 매니페스트
export interface PageContextEntry {
  /** 컨텍스트 사이드바 상단의 페이지 제목 */
  title: string
  /** 페이지 기능 목록 (비어 있으면 빈 상태 UI를 보인다) */
  features: PageContextFeature[]
}

// 전체 라우트의 컨텍스트 매니페스트. onClick은 뷰에서 채운다.
export const PAGE_CONTEXT_MANIFEST: Record<RouteKey, PageContextEntry> = {
  // 마케팅/인증 (기능 없음)
  landing: {
    title: "랜딩",
    features: [],
  },
  auth: {
    title: "로그인",
    features: [],
  },
  kakaoCallback: {
    title: "카카오 로그인",
    features: [],
  },
  onboarding: {
    title: "온보딩",
    features: [],
  },
  apiTest: {
    title: "API 테스트",
    features: [],
  },

  // 홈
  home: {
    title: "홈",
    features: [
      { id: "continue-study", icon: "play", label: "이어서 학습" },
      { id: "today-review", icon: "cards", label: "오늘 복습" },
      { id: "active-course", icon: "book", label: "진행 중 과외" },
      { id: "weekly-rhythm", icon: "chart", label: "이번 주 리듬" },
    ],
  },

  // 새 과외
  explore: {
    title: "새 과외 만들기",
    features: [
      { id: "pdf-start", icon: "file", label: "PDF로 시작" },
      { id: "topic-start", icon: "brain", label: "주제로 시작" },
      { id: "tutor-choose", icon: "owl", label: "튜터 고르기", type: 'modal' as const, modalKey: 'explore-tutor-choose', modalSize: 'sheet-right-480' },
      { id: "duration-adjust", icon: "clock", label: "기간 조정" },
    ],
  },

  // 커리큘럼 확인
  courseDetail: {
    title: "커리큘럼 확인",
    features: [
      { id: "curriculum-review", icon: "book", label: "커리큘럼 검토" },
      { id: "title-edit", icon: "settings", label: "제목 수정" },
      { id: "tutor-check", icon: "owl", label: "튜터 확인" },
      { id: "confirm", icon: "sparkle", label: "확정하기" },
    ],
  },

  // 내 과외
  myCourses: {
    title: "내 과외",
    features: [
      { id: "active-courses", icon: "play", label: "진행 중 과외" },
      { id: "completed-courses", icon: "sparkle", label: "완료한 과외" },
      { id: "course-compare", icon: "chart", label: "수강 현황 비교" },
    ],
  },

  // 과외 학습
  learning: {
    title: "과외 학습",
    features: [
      { id: "current-slide", icon: "cards", label: "현재 슬라이드" },
      { id: "learning-chat", icon: "brain", label: "질문 열기", type: 'subview' as const },
      { id: "learning-curriculum", icon: "book", label: "커리큘럼 보기", type: 'subview' as const },
      { id: "learning-homework", icon: "file", label: "과제 보기", type: 'subview' as const },
      { id: "learning-focus", icon: "sparkle", label: "집중 모드", type: 'subview' as const },
    ],
  },

  // 암기노트
  notes: {
    title: "암기노트",
    features: [
      { id: "topic-filter", icon: "settings", label: "주제별 보기" },
      { id: "search", icon: "brain", label: "노트 검색" },
      { id: "note-detail", icon: "file", label: "상세 보기", type: 'modal' as const, modalKey: 'notes-detail', modalSize: 'sheet-right-560' },
      { id: "telegram-send", icon: "sparkle", label: "텔레그램 전송" },
    ],
  },

  // 학습 캘린더
  calendar: {
    title: "학습 캘린더",
    features: [
      { id: "today-schedule", icon: "calendar", label: "오늘 일정" },
      { id: "weekly-rate", icon: "chart", label: "이번 주 달성률" },
      { id: "deadline-schedule", icon: "clock", label: "마감 일정" },
    ],
  },

  // 학습 리포트. 정보가 많아 서브뷰 전환을 쓴다.
  report: {
    title: "학습 리포트",
    features: [
      { id: "week-summary",  icon: "chart",    label: "이번 주 요약", type: 'subview' as const },
      { id: "chart-view",    icon: "brain",    label: "차트 보기",   type: 'subview' as const },
      { id: "weak-concepts", icon: "cards",    label: "취약 개념",   type: 'subview' as const },
      { id: "range-change",  icon: "settings", label: "범위 바꾸기", type: 'subview' as const },
    ],
  },

  // 내 과제. 과외 필터는 본문 탭이 아니라 컨텍스트 사이드바에 있다.
  // type: 'filter' 항목은 PageContextSidebar에서 activeHomeworkFilter와 연결된다.
  myHomework: {
    title: "내 과제",
    features: [
      {
        id: "filter-all",
        icon: "file",
        label: "전체",
        type: "filter" as const,
        filterValue: "all",
        count: 5,
      },
      {
        id: "filter-sdlc",
        icon: "book",
        label: "정보처리기사 실기",
        type: "filter" as const,
        filterValue: "sdlc",
        count: 2,
      },
      {
        id: "filter-rag",
        icon: "brain",
        label: "RAG와 벡터 검색",
        type: "filter" as const,
        filterValue: "rag",
        count: 2,
      },
      {
        id: "filter-sec",
        icon: "sparkle",
        label: "웹 보안 입문",
        type: "filter" as const,
        filterValue: "sec",
        count: 1,
      },
    ],
  },

  // 모의고사 (서브뷰 전환)
  mockExam: {
    title: "모의고사",
    features: [
      { id: "course-choose", icon: "book",    label: "과외 선택", type: 'subview' as const },
      { id: "exam-start",    icon: "play",    label: "시험 시작", type: 'subview' as const },
      { id: "history",       icon: "clock",   label: "응시 이력", type: 'subview' as const },
      { id: "coin-guide",    icon: "sparkle", label: "코인 안내", type: 'modal' as const, modalKey: 'mockexam-coin-guide', modalSize: 'dialog-sm' },
    ],
  },

  // 실전 모의고사. AI 맞춤 문제 세트이고 서브뷰 전환을 쓴다.
  aiMockExam: {
    title: "실전 모의고사",
    features: [
      { id: "course-choose", icon: "book",    label: "과외 선택", type: 'subview' as const },
      { id: "exam-start",    icon: "play",    label: "시험 시작", type: 'subview' as const },
      { id: "history",       icon: "clock",   label: "응시 이력", type: 'subview' as const },
      { id: "coin-guide",    icon: "sparkle", label: "코인 안내", type: 'modal' as const, modalKey: 'ai-mockexam-coin-guide', modalSize: 'dialog-sm' },
    ],
  },

  // AI 튜터
  tutor: {
    title: "튜터 설정",
    features: [
      { id: "tutor-choose", icon: "owl", label: "튜터 선택" },
      { id: "persona-edit", icon: "settings", label: "성격 편집" },
      { id: "live-preview", icon: "message", label: "라이브 미리보기" },
      { id: "add-tutor", icon: "plus", label: "새 튜터 추가" },
    ],
  },

  // 구독 · 코인. 스크롤 앵커가 필요 없어 빈 배열이다.
  pricing: {
    title: "구독 · 코인",
    features: [],
  },
  paymentCallback: {
    title: "결제 처리",
    features: [],
  },

  // 설정
  settings: {
    title: "설정",
    features: [
      { id: "account", icon: "settings", label: "계정" },
      { id: "notification", icon: "message", label: "알림" },
      { id: "billing", icon: "chart", label: "결제" },
      { id: "privacy", icon: "file", label: "개인정보" },
    ],
  },

  // 강사 에디터 (chrome: none)
  editor: {
    title: "강의 에디터",
    features: [
      { id: "structure-tree", icon: "book", label: "구조 트리" },
      { id: "slide-edit", icon: "cards", label: "슬라이드 편집" },
      { id: "ai-suggest", icon: "sparkle", label: "추천 제안" },
    ],
  },

  // 관리자 (chrome: none)
  admin: {
    title: "관리자 콘솔",
    features: [
      { id: "submission-status", icon: "file", label: "제출 현황" },
      { id: "student-list", icon: "settings", label: "학생 목록" },
      { id: "recent-activity", icon: "chart", label: "최근 활동" },
      { id: "notice-send", icon: "message", label: "공지 발송" },
    ],
  },
}
