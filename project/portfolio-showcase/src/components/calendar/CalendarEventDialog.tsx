// 캘린더 이벤트 생성·수정 폼
import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import type { Palette } from '@/lib';
import type {
  CalendarEventResponse,
  CalendarEventStatus,
  CalendarEventType,
  CreateCalendarEventRequest,
  UpdateCalendarEventRequest,
} from '@/apis';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

const EVENT_TYPES: { value: CalendarEventType; label: string }[] = [
  { value: 'LESSON', label: '강의' },
  { value: 'ASSIGNMENT_DUE', label: '과제 마감' },
  { value: 'REVIEW', label: '복습' },
  { value: 'EXAM', label: '시험' },
  { value: 'CUSTOM', label: '사용자 정의' },
];

const EVENT_STATUSES: { value: CalendarEventStatus; label: string }[] = [
  { value: 'scheduled', label: '예정' },
  { value: 'completed', label: '완료' },
  { value: 'cancelled', label: '취소' },
  { value: 'missed', label: '미완료' },
];

interface EventFormState {
  title: string;
  type: CalendarEventType;
  date: string;
  scheduledTime: string;
  durationMin: string;
  status: CalendarEventStatus;
  description: string;
}

interface CalendarEventDialogProps {
  open: boolean;
  mode: 'create' | 'edit';
  event: CalendarEventResponse | null;
  defaultDate: string;
  activeCourseId: string;
  palette: Palette;
  onOpenChange: (open: boolean) => void;
  onCreate: (body: CreateCalendarEventRequest) => Promise<void>;
  onUpdate: (eventId: string, body: UpdateCalendarEventRequest) => Promise<void>;
}

function initialForm(event: CalendarEventResponse | null, defaultDate: string): EventFormState {
  return {
    title: event?.title ?? '',
    type: event?.type ?? 'CUSTOM',
    date: event?.date.slice(0, 10) ?? defaultDate,
    scheduledTime: event?.scheduledTime ?? '',
    durationMin: event?.durationMin ? String(event.durationMin) : '',
    status: event?.status ?? 'scheduled',
    description: '',
  };
}

function optionalNumber(value: string): number | undefined {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

const fieldStyle: CSSProperties = {
  width: '100%',
  height: 34,
  borderRadius: 8,
  border: '1px solid var(--line)',
  background: 'var(--paper)',
  color: 'var(--ink)',
  fontSize: 13,
  padding: '0 10px',
  boxSizing: 'border-box',
};

const labelStyle: CSSProperties = {
  display: 'grid',
  gap: 5,
  fontSize: 11,
  fontWeight: 800,
  color: 'var(--ink-3)',
};

export function CalendarEventDialog({
  open,
  mode,
  event,
  defaultDate,
  activeCourseId,
  palette,
  onOpenChange,
  onCreate,
  onUpdate,
}: CalendarEventDialogProps) {
  const [form, setForm] = useState<EventFormState>(() => initialForm(event, defaultDate));
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setForm(initialForm(event, defaultDate));
    setFormError(null);
  }, [open, event, defaultDate]);

  const submit = async () => {
    const title = form.title.trim();
    if (!title) {
      setFormError('제목을 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    setFormError(null);
    try {
      const durationMin = optionalNumber(form.durationMin);
      const scheduledTime = form.scheduledTime || undefined;
      if (mode === 'create') {
        await onCreate({
          title,
          type: form.type,
          date: form.date,
          scheduledTime,
          durationMin,
          courseId: activeCourseId || undefined,
          description: form.description.trim() || undefined,
        });
      } else if (event) {
        await onUpdate(event.id, {
          title,
          date: form.date,
          scheduledTime,
          durationMin,
          status: form.status,
        });
      }
    } catch {
      setFormError(mode === 'create' ? '일정을 추가하지 못했습니다.' : '일정을 수정하지 못했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const setField = <K extends keyof EventFormState>(key: K, value: EventFormState[K]) => {
    setForm(prev => ({ ...prev, [key]: value }));
  };

  const toggleStatus = () => {
    setField('status', form.status === 'completed' ? 'scheduled' : 'completed');
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg" showCloseButton={!submitting}>
        <DialogHeader>
          <DialogTitle>{mode === 'create' ? '일정 추가' : '일정 수정'}</DialogTitle>
          <DialogDescription>
            날짜와 시간을 저장하면 캘린더 목록을 다시 불러옵니다.
          </DialogDescription>
        </DialogHeader>

        <div style={{ display: 'grid', gap: 12 }}>
          <label style={labelStyle}>
            제목
            <input value={form.title} onChange={e => setField('title', e.target.value)} maxLength={100} style={fieldStyle} />
          </label>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <label style={labelStyle}>
              날짜
              <input type="date" value={form.date} onChange={e => setField('date', e.target.value)} style={fieldStyle} />
            </label>
            <label style={labelStyle}>
              시간
              <input type="time" value={form.scheduledTime} onChange={e => setField('scheduledTime', e.target.value)} style={fieldStyle} />
            </label>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <label style={labelStyle}>
              유형
              <select value={form.type} onChange={e => setField('type', e.target.value as CalendarEventType)} disabled={mode === 'edit'} style={fieldStyle}>
                {EVENT_TYPES.map(type => <option key={type.value} value={type.value}>{type.label}</option>)}
              </select>
            </label>
            <label style={labelStyle}>
              소요 시간
              <input type="number" min={1} value={form.durationMin} onChange={e => setField('durationMin', e.target.value)} placeholder="60" style={fieldStyle} />
            </label>
          </div>
          {mode === 'edit' ? (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 10, alignItems: 'end' }}>
              <label style={labelStyle}>
                상태
                <select value={form.status} onChange={e => setField('status', e.target.value as CalendarEventStatus)} style={fieldStyle}>
                  {EVENT_STATUSES.map(status => <option key={status.value} value={status.value}>{status.label}</option>)}
                </select>
              </label>
              <button type="button" onClick={toggleStatus} style={{
                height: 34, padding: '0 12px', borderRadius: 8,
                background: palette.wash, color: palette.primary,
                border: `1px solid ${palette.soft}`,
                fontSize: 12, fontWeight: 800,
              }}>
                {form.status === 'completed' ? '예정으로' : '완료로'}
              </button>
            </div>
          ) : (
            <label style={labelStyle}>
              설명
              <textarea value={form.description} onChange={e => setField('description', e.target.value.slice(0, 500))} rows={3} style={{ ...fieldStyle, height: 72, padding: '8px 10px', resize: 'none' }} />
            </label>
          )}
          {formError && <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--danger)' }}>{formError}</div>}
        </div>

        <DialogFooter>
          <button type="button" onClick={() => onOpenChange(false)} disabled={submitting} style={{
            height: 34, padding: '0 13px', borderRadius: 8,
            background: 'var(--paper)', color: 'var(--ink-3)',
            border: '1px solid var(--line)', fontSize: 12, fontWeight: 800,
          }}>취소</button>
          <button type="button" onClick={() => void submit()} disabled={submitting} style={{
            height: 34, padding: '0 14px', borderRadius: 8,
            background: palette.primary, color: 'var(--paper)',
            border: 'none', fontSize: 12, fontWeight: 800,
            opacity: submitting ? 0.7 : 1,
          }}>{submitting ? '저장 중...' : '저장'}</button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
