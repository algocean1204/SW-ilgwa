export interface NoteResponse {
  id: string;
  title: string;
  content: string;
  type: string;
  slideIndex: number | null;
  timestamp: number | null;
  starred: boolean;
  createdAt: string;
}

export interface NoteListResponse {
  id: string;
  title: string;
  content: string;
  type: string;
  starred: boolean;
  courseId: string;
  courseName: string;
  subject: string;
  chapterId: string;
  chapterTitle: string;
  createdAt: string;
  updatedAt: string;
}

export interface BookmarkResponse {
  id: string;
  slideIndex: number;
  memo: string;
  createdAt: string;
}

export interface TelegramSendResponse {
  noteId: string;
  sent: boolean;
  message: string;
}

export interface CreateNoteRequest {
  content: string;
  slideIndex?: number;
  timestamp?: number;
}

export interface UpdateNoteRequest {
  title?: string;
  content?: string;
}

export interface CreateBookmarkRequest {
  slideIndex: number;
  memo?: string;
}

export interface GetAllNotesParams {
  subject?: string;
  search?: string;
  starred?: boolean;
  page?: number;
  size?: number;
}
