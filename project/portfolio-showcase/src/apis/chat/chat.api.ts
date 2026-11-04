import { apiClient } from '../client';
import type { ApiResponse } from '../types';
import type {
  ChatHistoryResponse,
  ChatRequest,
  ChatResponse,
} from './chat.type';

export function sendChat(
  courseId: string,
  body: ChatRequest,
): Promise<ApiResponse<ChatResponse>> {
  return apiClient.post(`/tutoring/${courseId}/chat`, body);
}

export function getChatHistory(
  courseId: string,
): Promise<ApiResponse<ChatHistoryResponse>> {
  return apiClient.get(`/tutoring/${courseId}/chat/history`);
}
