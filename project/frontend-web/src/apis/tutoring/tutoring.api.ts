import { apiClient } from '../client';
import type { ApiResponse, OffsetPageResponse } from '../types';
import type {
  ChapterDetailResponse,
  ChapterSummaryResponse,
  CourseDetailResponse,
  CourseSummaryResponse,
  CreateTutoringRequest,
  CurriculumResponse,
  GetMyCoursesParams,
  LessonGenerationStatusResponse,
  RecentTutoringResponse,
  SourcePdfUploadResponse,
  UpdateTutoringRequest,
} from './tutoring.type';

export function getMyCourses(
  params?: GetMyCoursesParams,
): Promise<ApiResponse<OffsetPageResponse<CourseSummaryResponse>>> {
  return apiClient.get('/tutoring', params as Record<string, unknown>);
}

export function getCourse(
  courseId: string,
): Promise<ApiResponse<CourseDetailResponse>> {
  return apiClient.get(`/tutoring/${courseId}`);
}

export function createTutoring(
  body: CreateTutoringRequest,
): Promise<ApiResponse<CourseDetailResponse>> {
  return apiClient.post('/tutoring', body);
}

export function uploadTutoringSourcePdf(
  file: File,
): Promise<ApiResponse<SourcePdfUploadResponse>> {
  const formData = new FormData();
  formData.append('file', file);
  return apiClient.upload('/tutoring/source-pdfs', formData);
}

export function updateTutoring(
  courseId: string,
  body: UpdateTutoringRequest,
): Promise<ApiResponse<CourseDetailResponse>> {
  return apiClient.put(`/tutoring/${courseId}`, body);
}

export function deleteTutoring(courseId: string): Promise<void> {
  return apiClient.del(`/tutoring/${courseId}`);
}

export function getChapters(
  courseId: string,
): Promise<ApiResponse<ChapterSummaryResponse[]>> {
  return apiClient.get(`/tutoring/${courseId}/lessons`);
}

export function getChapter(
  courseId: string,
  chapterId: string,
): Promise<ApiResponse<ChapterDetailResponse>> {
  return apiClient.get(`/tutoring/${courseId}/lessons/${chapterId}`);
}

/** 강의 생성 노드 단위 진행 상태. 조회할 수 없으면 서버가 status=unknown을 돌려준다. */
export function getLessonGenerationStatus(
  courseId: string,
  chapterId: string,
): Promise<ApiResponse<LessonGenerationStatusResponse>> {
  return apiClient.get(`/tutoring/${courseId}/lessons/${chapterId}/generation-status`);
}

export function getCurriculum(
  courseId: string,
): Promise<ApiResponse<CurriculumResponse>> {
  return apiClient.get(`/tutoring/${courseId}/curriculum`);
}

export function confirmCurriculum(
  courseId: string,
): Promise<ApiResponse<CurriculumResponse>> {
  return apiClient.put(`/tutoring/${courseId}/curriculum/confirm`);
}

export function getRecentTutoring(): Promise<ApiResponse<RecentTutoringResponse>> {
  return apiClient.get('/tutoring/recent');
}
