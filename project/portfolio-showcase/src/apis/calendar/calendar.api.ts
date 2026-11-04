import { apiClient } from '../client';
import type { ApiResponse } from '../types';
import type {
  CalendarEventResponse,
  CalendarResponse,
  CreateCalendarEventRequest,
  MissionResponse,
  StreakResponse,
  UpdateCalendarEventRequest,
} from './calendar.type';

export function getCalendar(
  year: number,
  month: number,
): Promise<ApiResponse<CalendarResponse>> {
  return apiClient.get('/calendar', { year, month });
}

export function createCalendarEvent(
  body: CreateCalendarEventRequest,
): Promise<ApiResponse<CalendarEventResponse>> {
  return apiClient.post('/calendar/events', body);
}

export function updateCalendarEvent(
  eventId: string,
  body: UpdateCalendarEventRequest,
): Promise<ApiResponse<CalendarEventResponse>> {
  return apiClient.put(`/calendar/events/${eventId}`, body);
}

export function getStreak(
  months?: number,
): Promise<ApiResponse<StreakResponse>> {
  return apiClient.get('/calendar/streak', months ? { months } : undefined);
}

export function getMissionsToday(): Promise<ApiResponse<MissionResponse>> {
  return apiClient.get('/calendar/missions/today');
}
