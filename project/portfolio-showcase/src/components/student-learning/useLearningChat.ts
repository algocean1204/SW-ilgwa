// 학습 튜터 채팅 메시지 상태 훅
import { useState, useEffect, useRef, useCallback, type RefObject } from 'react';
import { getChatHistory, sendChat } from '@/apis';
import type { ChatHistoryItem } from '@/apis';

/** UI 채팅 메시지 타입. API ChatHistoryItem을 변환해 쓴다 */
export interface ChatMessage {
  who: 'ai' | 'me';
  text: string;
  time?: string;
  chips?: string[];
  cite?: string;
  /** 전송 실패 여부. true면 에러 말풍선과 재시도 버튼을 보여 준다 */
  failed?: boolean;
  /** 재시도할 때 다시 보낼 원문 텍스트 */
  retryText?: string;
}

// AI가 지금 보고 있는 슬라이드를 알 수 있게 넘기는 컨텍스트
export interface SlideContext {
  lessonId?: string;
  slideId?: string;
  slideIdx?: number;
  /** 현재 슬라이드 제목 */
  slideTitle?: string;
  /** 현재 슬라이드 본문. HTML을 파싱한 텍스트를 2000자로 잘라 보낸다 */
  slideContent?: string;
}

interface LearningChatState {
  chat: ChatMessage[];
  typing: boolean;
  input: string;
  setInput: (v: string) => void;
  send: () => void;
  /** failed 메시지 인덱스를 받아 그 메시지를 다시 보낸다 */
  retry: (failedIdx: number) => void;
  scrollRef: RefObject<HTMLDivElement>;
  // rightTab은 훅 입력이라 반환값에 넣지 않는다. 호출부가 가진 값을 쓴다.
}

/** API ChatHistoryItem → UI ChatMessage 변환 */
function toMessage(item: ChatHistoryItem): ChatMessage {
  const time = item.createdAt
    ? new Date(item.createdAt).toTimeString().slice(0, 5)
    : undefined;
  return {
    who: item.role === 'assistant' ? 'ai' : 'me',
    text: item.message,
    time,
    cite: item.citations.length > 0 ? item.citations.join(', ') : undefined,
  };
}

/** 현재 시각을 HH:MM 형식으로 반환 */
function nowHHMM(): string {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
}

/**
 * 학습 튜터 채팅 상태를 관리하는 훅.
 * - courseId로 히스토리 로드 + 메시지 전송
 * - rightTab 변경 시에도 스크롤을 최하단으로 이동
 * - slideContext를 받아 AI가 현재 슬라이드 위치를 알게 한다
 *
 * backend-fastapi가 ChatRequest의 lessonId/slideId/slideIdx를 실제로 받아 써야
 * 컨텍스트 인식이 완전히 동작한다. spring_adapter의 가짜 컨텍스트 합성도 빼야 한다.
 */
export function useLearningChat(courseId: string, rightTab: string, slideContext?: SlideContext): LearningChatState {
  const [chat, setChat] = useState<ChatMessage[]>([]);
  const [typing, setTyping] = useState(false);
  const [input, setInput] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);

  // 채팅 히스토리 로드
  useEffect(() => {
    if (!courseId) return;
    void getChatHistory(courseId).then((res) => {
      if (res.data?.messages) {
        // 백엔드가 createdAt 내림차순(최신순)으로 반환하므로,
        // 화면에 오래된 메시지가 위·최신 메시지가 아래에 오도록 오름차순 정렬한다.
        const sorted = [...res.data.messages].sort(
          (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        );
        setChat(sorted.map(toMessage));
      }
    }).catch(() => {});
  }, [courseId]);

  // 채팅 목록이나 탭이 바뀌면 맨 아래로 스크롤한다
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [chat.length, typing, rightTab]);

  /**
   * 원문 텍스트를 받아 API를 호출하는 실제 전송 로직.
   * 성공하면 AI 응답을 붙이고, 실패하면 사용자 메시지를 failed로 표시한다.
   * replaceIdx가 주어지면 해당 인덱스의 메시지를 덮어쓴다(재시도 경로).
   */
  const sendMessage = useCallback((text: string, replaceIdx?: number) => {
    if (!text.trim() || !courseId) return;
    const time = nowHHMM();

    if (replaceIdx !== undefined) {
      // 재시도: failed 메시지를 loading 상태 없는 버전으로 교체한다
      setChat(c => c.map((m, i) => i === replaceIdx ? { who: 'me', text, time } : m));
    } else {
      // 신규 전송: 사용자 메시지를 목록 끝에 추가한다
      setChat(c => [...c, { who: 'me', text, time }]);
    }
    setTyping(true);

    // ChatRequest의 컨텍스트 필드(lessonId/slideId/slideIdx, slideTitle/slideContent)를
    // 모두 보내 AI가 현재 슬라이드 위치와 내용을 알게 한다
    void sendChat(courseId, {
      message: text,
      lessonId: slideContext?.lessonId,
      slideId: slideContext?.slideId,
      slideIdx: slideContext?.slideIdx,
      slideTitle: slideContext?.slideTitle,
      slideContent: slideContext?.slideContent,
    }).then((res) => {
      if (res.data) {
        const aiReply: ChatMessage = {
          who: 'ai',
          text: res.data.responseText,
          time: nowHHMM(),
          cite: res.data.citations.length > 0 ? res.data.citations.join(', ') : undefined,
        };
        setChat(c => [...c, aiReply]);
      }
    }).catch((err: unknown) => {
      // 전송 실패면 사용자 메시지를 failed로 바꿔 재시도 버튼을 보여 준다.
      // 에러는 삼키지 않고 콘솔에 남긴다.
      console.warn('[useLearningChat] 메시지 전송 실패:', err);
      setChat(c => {
        // replaceIdx가 있으면 그 위치를, 없으면 마지막 'me' 메시지를 실패로 바꾼다
        if (replaceIdx !== undefined) {
          return c.map((m, i) =>
            i === replaceIdx ? { ...m, failed: true, retryText: text } : m
          );
        }
        // 방금 추가된 사용자 메시지(마지막 'me')를 찾아 failed로 표시한다
        const lastMeIdx = [...c].reverse().findIndex(m => m.who === 'me' && !m.failed);
        if (lastMeIdx < 0) return c;
        const realIdx = c.length - 1 - lastMeIdx;
        return c.map((m, i) =>
          i === realIdx ? { ...m, failed: true, retryText: text } : m
        );
      });
    }).finally(() => setTyping(false));
  }, [courseId, slideContext]);

  /** 현재 슬라이드 컨텍스트와 함께 메시지를 보낸다 */
  const send = useCallback(() => {
    if (!input.trim()) return;
    sendMessage(input);
    setInput('');
  }, [input, sendMessage]);

  /** 실패한 메시지를 해당 인덱스로 다시 보낸다 */
  const retry = useCallback((failedIdx: number) => {
    const msg = chat[failedIdx];
    if (!msg?.retryText) return;
    sendMessage(msg.retryText, failedIdx);
  }, [chat, sendMessage]);

  return { chat, typing, input, setInput, send, retry, scrollRef };
}
