export interface DailyStat {
  date: string;
  studyMinutes: number;
  quizCount: number;
  averageScore: number;
}

export interface WeeklyReportResponse {
  weekLabel: string;
  totalStudyMinutes: number;
  completedLessons: number;
  streakDays: number;
  totalQuizCount: number;
  averageScorePercent: number;
  dailyStats: DailyStat[];
  dailyStudyGoalMinutes: number;
  completedAssignmentCount: number;
  assignmentAvgScore: number;
}

export interface SubjectProgress {
  subject: string;
  totalChapters: number;
  completedChapters: number;
  progressPercent: number;
}

export interface ProgressReportResponse {
  totalCourses: number;
  completedCourses: number;
  subjects: SubjectProgress[];
}

export interface DailyStudyTime {
  date: string;
  minutes: number;
}

export interface StudyTimeReportResponse {
  period: string;
  totalMinutes: number;
  averageMinutesPerDay: number;
  totalQuizCount: number;
  averageScorePercent: number;
  dailyData: DailyStudyTime[];
}

export interface ChapterAccuracy {
  chapterId: string;
  chapterTitle: string;
  totalQuizCount: number;
  correctCount: number;
  accuracyPercent: number;
}

export interface ConceptUnderstandingResponse {
  overallAccuracy: number;
  chapters: ChapterAccuracy[];
}
