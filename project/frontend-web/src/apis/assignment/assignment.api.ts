import { apiClient } from '../client';
import type { ApiResponse } from '../types';
import type {
  AssignmentDetailResponse,
  AssignmentListResponse,
  GradeResponse,
  SubmissionResponse,
  SubmitAnswerRequest,
} from './assignment.type';

export function getAssignments(
  courseId: string,
  status?: string,
): Promise<ApiResponse<AssignmentListResponse[]>> {
  return apiClient.get(
    `/tutoring/${courseId}/assignments`,
    status ? { status } : undefined,
  );
}

export function getAssignment(
  courseId: string,
  assignmentId: string,
): Promise<ApiResponse<AssignmentDetailResponse>> {
  return apiClient.get(`/tutoring/${courseId}/assignments/${assignmentId}`);
}

export function submitAssignment(
  courseId: string,
  assignmentId: string,
  body: SubmitAnswerRequest,
): Promise<ApiResponse<SubmissionResponse>> {
  return apiClient.post(`/tutoring/${courseId}/assignments/${assignmentId}/submit`, body);
}

export function getGrades(
  courseId: string,
  assignmentId: string,
): Promise<ApiResponse<GradeResponse>> {
  return apiClient.get(`/tutoring/${courseId}/assignments/${assignmentId}/grades`);
}
