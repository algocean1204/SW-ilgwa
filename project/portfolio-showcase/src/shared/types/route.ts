export const ROUTE_KEYS = [
  "landing",
  "auth",
  "kakaoCallback",
  "onboarding",
  "home",
  "explore",
  "courseDetail",
  "myCourses",
  "learning",
  "notes",
  "calendar",
  "report",
  "myHomework",
  "mockExam",
  "aiMockExam",
  "tutor",
  "pricing",
  "settings",
  "apiTest",
  "editor",
  "admin",
  "paymentCallback",
] as const

export type RouteKey = (typeof ROUTE_KEYS)[number]
export type RouteSetter = (route: RouteKey) => void
