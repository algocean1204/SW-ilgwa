import rawStore from './data/showcase-store.json';
import type { GradedResult } from './grading';

export type { GradedResult } from './grading';

export interface ShowcaseQuestion {
  questionId: string;
  templateId: string;
  displayOrder: number;
  stem: string;
  points: number;
  options: { label: string; text: string }[];
  blankCount: number;
  orderingItems: string[];
  matchingLeftItems: string[];
  matchingRightItems: string[];
  _correctAnswer?: string | number | boolean | null;
  _explanation?: string;
  _gradingMetadata?: Record<string, unknown>;
  _submitted?: Record<string, unknown>;
  _isCorrect?: boolean;
  _score?: number;
  _gradingMode?: string;
  _feedback?: string;
  _rubric?: { criterion: string; score: number; max_score: number; reason: string }[];
}

export interface ShowcaseExamBundle {
  mockExam: {
    id: string;
    title: string;
    examType: string;
    questionCount: number;
    difficulty: string;
    timeLimit: number;
    status: string;
    totalPoints: number;
    createdAt: string;
    attemptId: string;
  };
  attempt: {
    attemptId: string;
    mockExamId: string;
    courseId: string;
    status: string;
    questions: ShowcaseQuestion[];
    publicQuestions: Omit<ShowcaseQuestion, '_correctAnswer' | '_explanation' | '_gradingMetadata' | '_submitted' | '_isCorrect' | '_score' | '_gradingMode' | '_feedback' | '_rubric'>[];
  };
  gradedResult?: GradedResult;
  listItem?: ShowcaseExamBundle['mockExam'];
}

export interface ShowcaseChapter {
  id: string;
  order: number;
  title: string;
  description: string;
  estimatedMinutes: number | null;
  status: string;
  slideCount: number;
  completedSlides: number;
  completedAt: string | null;
  keyTopics: string[];
  bookmarks: { slideIndex: number; memo: string; createdAt: string }[];
  xpEarned: number;
  slides: { slideId: string; slideIdx: number; title: string; content: string; audioUrl: string | null; durationSec: number }[];
  quizzes: { quizId: string; quizIdx: number; slideId: string; questionText: string; options: string[] }[];
  quizResult: { totalQuestions: number; correctCount: number; score: number; results: { quizId: string; selectedOption: number; correctOption: number; isCorrect: boolean; explanation: string }[] };
  notes: { id: string; title: string; content: string; type: string; slideIndex: number | null; timestamp: number | null; starred: boolean; createdAt: string }[];
}

export interface ShowcaseCourse {
  id: string;
  title: string;
  subject: string;
  tutorId: string;
  tutorName: string;
  tutorAvatarUrl: string;
  status: string;
  sourceType: string;
  mode: string;
  progress: number;
  totalLessons: number;
  completedLessons: number;
  lastStudiedAt: string;
  createdAt: string;
  chapters: ShowcaseChapter[];
  exams: ShowcaseExamBundle[];
  notes: { id: string; title: string; content: string; type: string; starred: boolean; courseId: string; courseName: string; subject: string; chapterId: string; chapterTitle: string; createdAt: string; updatedAt: string }[];
  assignments: {
    id: string;
    title: string;
    description: string;
    status: string;
    dueDate: string;
    totalQuestions: number;
    maxScore: number | null;
    questions: { questionId: number; content: string }[];
    previousAnswer: string | null;
    createdAt: string;
    chapterId: string;
    listItem: { id: string; title: string; status: string; dueDate: string; totalQuestions: number; maxScore: number | null; createdAt: string };
  }[];
}

export interface ShowcaseStore {
  generatedAt: string;
  user: {
    id: string;
    email: string;
    nickname: string;
    onboardingCompleted: boolean;
    subscriptionStatus: string;
  };
  tutor: {
    tutorId: string;
    name: string;
    tagline: string;
    profileImageUrl: string;
    voiceSampleUrl: string;
  };
  accessToken: string;
  courses: ShowcaseCourse[];
  coins: number;
  subscription: { status: string; planCode: string };
}

export const store = structuredClone(rawStore) as unknown as ShowcaseStore;

export function findCourse(courseId: string): ShowcaseCourse | undefined {
  return store.courses.find((c) => c.id === courseId);
}

export function findChapter(courseId: string, chapterId: string): ShowcaseChapter | undefined {
  return findCourse(courseId)?.chapters.find((ch) => ch.id === chapterId);
}

export function findAttempt(attemptId: string): (ShowcaseExamBundle & { course: ShowcaseCourse }) | undefined {
  for (const course of store.courses) {
    const exam = course.exams.find((e) => e.attempt.attemptId === attemptId);
    if (exam) return { ...exam, course };
  }
  return undefined;
}

export function apiWrap<T>(payload: T) {
  return {
    success: true as const,
    data: payload,
    message: null,
    timestamp: new Date().toISOString(),
  };
}