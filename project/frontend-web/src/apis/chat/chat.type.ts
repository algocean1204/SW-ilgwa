export interface ChatRequest {
  lessonId?: string;
  slideId?: string;
  slideIdx?: number;
  /** 현재 슬라이드 제목. AI가 어느 슬라이드를 보고 있는지 알게 한다. */
  slideTitle?: string;
  /** 현재 슬라이드 본문 텍스트. AI 프롬프트에 그대로 넣는다. */
  slideContent?: string;
  message: string;
  selectedText?: string;
  playbackSec?: number;
}

export interface ChatResponse {
  responseText: string;
  citations: string[];
  sessionId: string;
}

export interface ChatHistoryItem {
  id: string;
  role: string;
  message: string;
  citations: string[];
  lessonId: string;
  slideIdx: number | null;
  createdAt: string;
}

export interface ChatHistoryResponse {
  messages: ChatHistoryItem[];
}
