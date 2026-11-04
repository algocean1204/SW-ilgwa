import { apiClient } from '../client';
import type { ApiResponse, PageResponse } from '../types';
import type {
  BookmarkResponse,
  CreateBookmarkRequest,
  CreateNoteRequest,
  GetAllNotesParams,
  NoteListResponse,
  NoteResponse,
  TelegramSendResponse,
  UpdateNoteRequest,
} from './note.type';

export function getLessonNotes(
  courseId: string,
  chapterId: string,
): Promise<ApiResponse<NoteResponse[]>> {
  return apiClient.get(`/tutoring/${courseId}/lessons/${chapterId}/notes`);
}

export function createLessonNote(
  courseId: string,
  chapterId: string,
  body: CreateNoteRequest,
): Promise<ApiResponse<NoteResponse>> {
  return apiClient.post(`/tutoring/${courseId}/lessons/${chapterId}/notes`, body);
}

export function updateLessonNote(
  courseId: string,
  chapterId: string,
  noteId: string,
  body: UpdateNoteRequest,
): Promise<ApiResponse<NoteResponse>> {
  return apiClient.put(`/tutoring/${courseId}/lessons/${chapterId}/notes/${noteId}`, body);
}

export function deleteLessonNote(
  courseId: string,
  chapterId: string,
  noteId: string,
): Promise<void> {
  return apiClient.del(`/tutoring/${courseId}/lessons/${chapterId}/notes/${noteId}`);
}

export function toggleLessonNoteStar(
  courseId: string,
  chapterId: string,
  noteId: string,
): Promise<ApiResponse<NoteResponse>> {
  return apiClient.put(`/tutoring/${courseId}/lessons/${chapterId}/notes/${noteId}/star`);
}

export function createBookmark(
  courseId: string,
  chapterId: string,
  body: CreateBookmarkRequest,
): Promise<ApiResponse<BookmarkResponse>> {
  return apiClient.post(`/tutoring/${courseId}/lessons/${chapterId}/bookmark`, body);
}

export function getAllNotes(
  params?: GetAllNotesParams,
): Promise<ApiResponse<PageResponse<NoteListResponse>>> {
  return apiClient.get('/notes', params as Record<string, unknown>);
}

export function sendNoteToTelegram(
  noteId: string,
): Promise<ApiResponse<TelegramSendResponse>> {
  return apiClient.post(`/notes/${noteId}/telegram-send`);
}
