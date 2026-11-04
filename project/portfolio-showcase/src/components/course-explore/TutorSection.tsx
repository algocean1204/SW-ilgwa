// 기본 또는 커스텀 튜터 목록 한 섹션을 그린다
import type { Palette } from '@/lib';
import type { TeacherItem } from './types';

const DEFAULT_TUTOR_IMAGE = import.meta.env.BASE_URL + 'default-tutor.svg';

interface TutorSectionProps {
  title: string;
  tutors: TeacherItem[];
  teacher: string;
  palette: Palette;
  emptyText?: string;
  onTeacherChange: (id: string) => void;
}

export function TutorSection({ title, tutors, teacher, palette, emptyText, onTeacherChange }: TutorSectionProps) {
  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontSize: 12, fontWeight: 800, color: 'var(--ink)' }}>{title}</span>
        <span style={{
          padding: '2px 7px', borderRadius: 999, background: palette.wash,
          color: palette.primary, fontSize: 11, fontWeight: 800,
        }}>
          {tutors.length}명
        </span>
      </div>
      {tutors.length ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(132px, 1fr))', gap: 8 }}>
          {tutors.map(te => {
            const active = teacher === te.id;
            return (
              <button
                key={te.id}
                aria-pressed={active}
                onClick={() => onTeacherChange(te.id)}
                className="lift"
                style={{
                  padding: '10px 8px', borderRadius: 10, textAlign: 'center',
                  background: active ? palette.wash : 'var(--bg)',
                  border: active ? `1.5px solid ${palette.primary}` : '1px solid var(--line)',
                }}
              >
                <img
                  src={te.photo || DEFAULT_TUTOR_IMAGE}
                  alt={te.n}
                  style={{
                    display: 'block', margin: '0 auto 4px',
                    width: 36, height: 36, borderRadius: '50%',
                    objectFit: 'cover', border: '1px solid var(--line)',
                  }}
                />
                <div style={{ fontSize: 12, fontWeight: 800, color: 'var(--ink)' }}>{te.n}</div>
                <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 1 }}>{te.d}</div>
              </button>
            );
          })}
        </div>
      ) : (
        <div style={{
          padding: '10px 12px', borderRadius: 10, background: 'var(--bg)',
          border: '1px solid var(--line)', color: 'var(--ink-3)', fontSize: 12, fontWeight: 700,
        }}>
          {emptyText}
        </div>
      )}
    </section>
  );
}
