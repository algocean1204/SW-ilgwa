import { apiClient } from '../client';
import type { ApiResponse } from '../types';
import type {
  CreateTutorRequest,
  CreateTutorResponse,
  PreviewTutorRequest,
  PreviewTutorResponse,
  ProfileImageResponse,
  TutorListResponse,
  TutorResponse,
  UpdateTutorRequest,
  VoiceSampleResponse,
} from './tutor.type';

export function getMyTutors(): Promise<ApiResponse<TutorListResponse>> {
  return apiClient.get('/tutors');
}

export function createTutor(
  body: CreateTutorRequest,
): Promise<ApiResponse<CreateTutorResponse>> {
  return apiClient.post('/tutors', body);
}

export function getTutor(
  tutorId: string,
): Promise<ApiResponse<TutorResponse>> {
  return apiClient.get(`/tutors/${tutorId}`);
}

export function updateTutor(
  tutorId: string,
  body: UpdateTutorRequest,
): Promise<ApiResponse<TutorResponse>> {
  return apiClient.patch(`/tutors/${tutorId}`, body);
}

export function deleteTutor(tutorId: string): Promise<void> {
  return apiClient.del(`/tutors/${tutorId}`);
}

export function resetTutor(
  tutorId: string,
): Promise<ApiResponse<TutorResponse>> {
  return apiClient.post(`/tutors/${tutorId}/reset`);
}

export function previewTutor(
  tutorId: string,
  body: PreviewTutorRequest,
): Promise<ApiResponse<PreviewTutorResponse>> {
  return apiClient.post(`/tutors/${tutorId}/preview`, body);
}

export function deleteAllCustomTutors(): Promise<void> {
  return apiClient.del('/tutors/custom');
}

export function uploadVoiceSample(
  tutorId: string,
  file: FormData,
): Promise<ApiResponse<VoiceSampleResponse>> {
  return apiClient.upload(`/tutors/${tutorId}/voice-sample`, file);
}

// 튜터 프로필 이미지 업로드. multipart/form-data, 필드명 "file"
export function uploadTutorProfileImage(
  tutorId: string,
  file: FormData,
): Promise<ApiResponse<ProfileImageResponse>> {
  return apiClient.upload(`/tutors/${tutorId}/profile-image`, file);
}

// 튜터 프로필 이미지 삭제. 응답 본문은 없다.
export function deleteTutorProfileImage(tutorId: string): Promise<void> {
  return apiClient.del(`/tutors/${tutorId}/profile-image`);
}
