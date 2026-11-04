import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

// ISO 날짜를 "M월 D일 (요일) HH:mm"으로 바꾼다. 채점 완료 화면의 제출·채점 시각에 쓴다.
// 잘못된 날짜면 원본 문자열을 그대로 돌려준다.
export function formatDateTime(iso: string): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;

  const month = date.getMonth() + 1; // 0-indexed → 1-indexed
  const day = date.getDate();
  const weekday = new Intl.DateTimeFormat('ko-KR', { weekday: 'short' }).format(date);
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');

  return `${month}월 ${day}일 (${weekday}) ${hh}:${mm}`;
}

// ISO 날짜를 상대 시간 문자열로 바꾼다 (예: "2시간 전"). HomeHero, HomeSidebar, MyCourses가 쓴다.
export function formatRelativeTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '방금';
  if (min < 60) return `${min}분 전`;
  const hours = Math.floor(min / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  return `${days}일 전`;
}
