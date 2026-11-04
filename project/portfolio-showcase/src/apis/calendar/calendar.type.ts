export type CalendarEventType = 'LESSON' | 'ASSIGNMENT_DUE' | 'REVIEW' | 'EXAM' | 'CUSTOM';

// 백엔드 @JsonValue가 소문자 dbValue로 직렬화한다
export type CalendarEventStatus = 'scheduled' | 'completed' | 'cancelled' | 'missed';

export interface CalendarEventResponse {
  id: string;
  title: string;
  type: CalendarEventType;
  date: string;
  status: CalendarEventStatus;
  scheduledTime: string;
  durationMin: number | null;
  courseId: string;
}

export interface CalendarResponse {
  year: number;
  month: number;
  events: CalendarEventResponse[];
  studyDays: number[];
  streakDays: number;
}

export interface StreakResponse {
  currentStreak: number;
  longestStreak: number;
  totalStudyDays: number;
  heatmap: Record<string, number>;
}

export interface MissionItem {
  id: string;
  title: string;
  description: string;
  type: string;
  current: number;
  target: number;
  xpReward: number;
  isCompleted: boolean;
}

export interface MissionResponse {
  date: string;
  missions: MissionItem[];
}

export interface CreateCalendarEventRequest {
  title: string;
  type: CalendarEventType;
  date: string;
  scheduledTime?: string;
  durationMin?: number;
  courseId?: string;
  description?: string;
}

export interface UpdateCalendarEventRequest {
  title?: string;
  date?: string;
  scheduledTime?: string;
  durationMin?: number;
  status?: CalendarEventStatus;
}
