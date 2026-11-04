// 텔레그램 설정 컴포넌트들이 같이 쓰는 순수 헬퍼.
import { ApiError } from '@/apis';
import type { TelegramStatusResponse } from '@/apis';

/** 날짜 문자열을 한국어 단축 형식(월 일 시 분)으로 포맷한다 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '기록 없음';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

/** API 에러 또는 일반 에러를 한국어 메시지로 변환한다 */
export function formatError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return '로그인 세션이 만료됐어요. 다시 로그인한 뒤 확인해주세요.';
    return error.errorResponse?.message ?? `텔레그램 요청에 실패했어요. (${error.status})`;
  }
  if (error instanceof Error) return error.message;
  return '텔레그램 요청에 실패했어요.';
}

/** 연동 상태에 맞는 텔레그램 계정 핸들 문자열 */
export function displayHandle(status: TelegramStatusResponse | null): string {
  if (!status?.linked) return '연결되지 않음';
  if (status.telegramUsername) return status.telegramUsername;
  if (status.telegramChatId) return `Chat ID ${status.telegramChatId}`;
  return '텔레그램 계정 연결됨';
}
