import { apiClient } from '../client';
import type { ApiResponse } from '../types';
import type {
  ConceptUnderstandingResponse,
  ProgressReportResponse,
  StudyTimeReportResponse,
  WeeklyReportResponse,
} from './report.type';

export function getWeeklyReport(
  week?: string,
): Promise<ApiResponse<WeeklyReportResponse>> {
  return apiClient.get('/reports/weekly', week ? { week } : undefined);
}

export function getProgressReport(): Promise<ApiResponse<ProgressReportResponse>> {
  return apiClient.get('/reports/progress');
}

export function getStudyTimeReport(
  period?: string,
): Promise<ApiResponse<StudyTimeReportResponse>> {
  return apiClient.get('/reports/study-time', period ? { period } : undefined);
}

export function getConceptUnderstanding(
  courseId: string,
): Promise<ApiResponse<ConceptUnderstandingResponse>> {
  return apiClient.get('/reports/concept-understanding', { courseId });
}
