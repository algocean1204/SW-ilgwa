// examforge API 호출 함수.
// 흐름: generate → attemptId 폴링(status) → detail → submit → result
import { apiClient } from '../client';
import type { ApiResponse } from '../types';
import type {
  AttemptDetailResponse,
  AttemptStatusResponse,
  ExamResultResponse,
  GenerateExamRequest,
  GenerateExamResponse,
  MockExamDetailResponse,
  MockExamListResponse,
  MockExamResultResponse,
  SubmitAttemptRequest,
  SubmitExamRequest,
} from './mock-exam.type';

// 레거시 엔드포인트 (기존 코드 호환)

export function getExamList(
  courseId: string,
): Promise<ApiResponse<MockExamListResponse[]>> {
  return apiClient.get(`/tutoring/${courseId}/mock-exams`);
}

/** @deprecated generateExamForge를 사용한다 */
export function generateExam(
  courseId: string,
  body: GenerateExamRequest,
): Promise<ApiResponse<MockExamListResponse>> {
  return apiClient.post(`/tutoring/${courseId}/mock-exams/generate`, body);
}

/** @deprecated getAttemptDetail을 사용한다 */
export function getExamDetail(
  courseId: string,
  examId: string,
): Promise<ApiResponse<MockExamDetailResponse>> {
  return apiClient.get(`/tutoring/${courseId}/mock-exams/${examId}`);
}

/** @deprecated submitAttempt를 사용한다 */
export function submitExam(
  courseId: string,
  examId: string,
  body: SubmitExamRequest,
): Promise<ApiResponse<MockExamResultResponse>> {
  return apiClient.post(`/tutoring/${courseId}/mock-exams/${examId}/submit`, body);
}

/** @deprecated getAttemptResult를 사용한다 */
export function getExamResult(
  courseId: string,
  examId: string,
): Promise<ApiResponse<MockExamResultResponse>> {
  return apiClient.get(`/tutoring/${courseId}/mock-exams/${examId}/results`);
}

// examforge 엔드포인트

/**
 * 모의고사 생성. attemptId가 non-null이면 examforge 응시 경로로 넘어간다.
 * POST /api/exam-forge/generate
 */
export function generateExamForge(
  body: GenerateExamRequest & { mockExamId?: string; coin?: number },
): Promise<ApiResponse<GenerateExamResponse>> {
  return apiClient.post('/exam-forge/generate', body);
}

/**
 * 응시 상태 폴링. GENERATING에서 READY로 바뀌는지 본다.
 * GET /api/exam-forge/attempts/{attemptId}/status
 */
export function getAttemptStatus(
  attemptId: string,
): Promise<ApiResponse<AttemptStatusResponse>> {
  return apiClient.get(`/exam-forge/attempts/${attemptId}/status`);
}

/**
 * 응시용 문항 목록. 정답과 해설은 없다.
 * GET /api/exam-forge/submissions/attempts/{attemptId}
 */
export function getAttemptDetail(
  attemptId: string,
): Promise<ApiResponse<AttemptDetailResponse>> {
  return apiClient.get(`/exam-forge/submissions/attempts/${attemptId}`);
}

/**
 * 답안 제출
 * POST /api/exam-forge/submissions/attempts/{attemptId}/submit
 */
export function submitAttempt(
  attemptId: string,
  body: SubmitAttemptRequest,
): Promise<ApiResponse<void>> {
  return apiClient.post(
    `/exam-forge/submissions/attempts/${attemptId}/submit`,
    body,
  );
}

/**
 * 채점 결과 조회 (examgrading 엔드포인트)
 * GET /api/exam-forge/submissions/attempts/{attemptId}/result
 */
export function getAttemptResult(
  attemptId: string,
): Promise<ApiResponse<ExamResultResponse>> {
  return apiClient.get(`/exam-forge/submissions/attempts/${attemptId}/result`);
}
