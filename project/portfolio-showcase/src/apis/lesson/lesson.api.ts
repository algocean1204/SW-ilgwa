import { apiClient } from '../client';
import type { ApiResponse } from '../types';
import type {
  ProgressRequest,
  QuizAnswerRequest,
  QuizListResponse,
  QuizResultResponse,
  SlideListResponse,
} from './lesson.type';

export function getSlides(
  courseId: string,
  chapterId: string,
): Promise<ApiResponse<SlideListResponse>> {
  return apiClient.get(`/tutoring/${courseId}/lessons/${chapterId}/slides`);
}

export function recordProgress(
  courseId: string,
  chapterId: string,
  body: ProgressRequest,
): Promise<ApiResponse<null>> {
  return apiClient.post(`/tutoring/${courseId}/lessons/${chapterId}/progress`, body);
}

export function getQuizzes(
  courseId: string,
  chapterId: string,
): Promise<ApiResponse<QuizListResponse>> {
  return apiClient.get(`/tutoring/${courseId}/lessons/${chapterId}/quizzes`);
}

export function getQuizAnswers(
  courseId: string,
  chapterId: string,
): Promise<ApiResponse<QuizResultResponse>> {
  return apiClient.get(`/tutoring/${courseId}/lessons/${chapterId}/quiz-answers`);
}

export function submitQuizAnswers(
  courseId: string,
  chapterId: string,
  body: QuizAnswerRequest,
): Promise<ApiResponse<QuizResultResponse>> {
  return apiClient.post(`/tutoring/${courseId}/lessons/${chapterId}/quiz-answers`, body);
}
