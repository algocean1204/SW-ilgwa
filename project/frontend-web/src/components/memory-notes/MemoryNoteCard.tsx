// 암기노트 그리드 카드 하나. 종류별 좌측 액센트 바와 내용 미리보기를 보여준다.
// 클릭 핸들러는 부모가 넘긴다.
import type { Palette } from '@/lib';
import type { MemoNote } from '@/shared/types';
import Icon from '@/Icon';

interface MemoryNoteCardProps {
  note: MemoNote;
  palette: Palette;
  active: boolean;
  onClick: () => void;
  delay: number;
  onToggleStar: () => void;
}

// 노트 종류의 한글 레이블
const KIND_LABEL: Record<string, string> = {
  qa: 'Q&A',
  bullet: '정리',
  code: '코드',
  compare: '비교',
};

// 종류별 좌측 액센트 바 색상. 팔레트와 무관하게 CSS 토큰을 쓴다.
const KIND_ACCENT: Record<string, string> = {
  qa: 'var(--note-qa-fg)',
  code: 'var(--syn-default)',
  compare: 'var(--honey-ink)',
};

export function MemoryNoteCard({ note, palette, active, onClick, delay, onToggleStar }: MemoryNoteCardProps) {
  // bullet만 현재 팔레트 primary 색을 액센트로 쓴다
  const accentColor = note.kind === 'bullet'
    ? palette.primary
    : (KIND_ACCENT[note.kind] ?? palette.primary);

  return (
    <button
      onClick={onClick}
      className="lift"
      style={{
        textAlign: 'left', padding: '12px 14px 12px 16px', borderRadius: 12,
        background: active ? palette.wash : 'var(--surface-card)',
        border: active ? `1.5px solid ${palette.primary}` : '1px solid var(--border-default)',
        // 좌측 액센트 바 색으로 노트 종류를 구분한다
        borderLeft: `3px solid ${active ? palette.primary : accentColor}`,
        display: 'flex', flexDirection: 'column', gap: 7, position: 'relative',
        animationDelay: delay + 'ms',
        minHeight: 120,
        boxShadow: active ? `0 4px 16px -8px ${palette.primary}` : 'var(--shadow-xs)',
        transition: 'all .18s ease',
      }}
    >
      {/* 상단: 종류 칩, 날짜 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
        <span style={{
          fontSize: 10, fontWeight: 800, padding: '2px 7px', borderRadius: 5,
          background: active ? 'rgba(255,255,255,0.6)' : 'var(--surface-subtle)',
          color: active ? palette.primary : 'var(--text-secondary)',
          border: `1px solid ${active ? palette.soft : 'var(--border-soft)'}`,
          letterSpacing: '.04em', flexShrink: 0,
        }}>
          {KIND_LABEL[note.kind] ?? note.kind}
        </span>
        <button
          onClick={e => { e.stopPropagation(); onToggleStar(); }}
          aria-label={note.starred ? '즐겨찾기 해제' : '즐겨찾기'}
          aria-pressed={!!note.starred}
          style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, lineHeight: 0, flexShrink: 0 }}
        >
          <Icon name="star" size={11}
            color={note.starred ? palette.primary : 'var(--text-muted)'}
            style={note.starred ? { fill: palette.primary } : undefined} />
        </button>
      </div>

      {/* 제목 */}
      <div style={{
        fontSize: 13, fontWeight: 800, color: active ? palette.primary : 'var(--text-primary)',
        letterSpacing: '-.015em', lineHeight: 1.35,
      }}>
        {note.title}
      </div>

      {/* 본문 미리보기. 종류마다 그리는 방식이 다르다 */}
      <div style={{ flex: 1 }}>
        {(note.kind === 'qa' || note.kind === 'bullet') && (
          <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', lineHeight: 1.55 }}>
            {note.body}
          </div>
        )}
        {note.kind === 'code' && note.code && (
          // 코드는 첫 3줄만 보여주고 maxHeight로 카드 높이를 묶는다
          <pre className="mono" style={{
            margin: 0, padding: '5px 8px', borderRadius: 6,
            background: 'var(--code-bg)', fontSize: 10, lineHeight: 1.5,
            color: 'var(--syn-string)', overflow: 'hidden', maxHeight: 48,
            whiteSpace: 'pre',
          }}>
            {note.code.split('\n').slice(0, 3).join('\n')}
          </pre>
        )}
        {note.kind === 'compare' && note.compare && (
          // 비교표는 첫 2행만 보여준다
          <div style={{ borderRadius: 6, overflow: 'hidden', border: '1px solid var(--border-soft)', fontSize: 11 }}>
            {note.compare.slice(0, 2).map((row, i) => (
              <div key={i} style={{
                display: 'grid', gridTemplateColumns: '52px 1fr 1fr',
                padding: '3px 7px',
                background: i % 2 ? 'var(--surface-page)' : 'var(--surface-card)',
                color: 'var(--text-secondary)',
              }}>
                <span style={{ fontWeight: 700 }}>{row[0]}</span>
                <span>{row[1]}</span>
                <span style={{ color: palette.primary, fontWeight: 700 }}>{row[2]}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 하단: 강의 출처 */}
      <div style={{ paddingTop: 6, borderTop: '1px solid var(--border-soft)' }}>
        <span style={{ fontSize: 10, color: 'var(--text-tertiary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {note.lesson}
        </span>
      </div>
    </button>
  );
}
