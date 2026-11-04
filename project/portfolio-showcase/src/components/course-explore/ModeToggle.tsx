// PDF 업로드와 주제 입력 중 하나를 고르는 토글
import Icon from '@/Icon';
import type { Palette } from '@/lib';

export type CourseExploreMode = 'pdf' | 'topic';

interface ModeToggleProps {
  mode: CourseExploreMode;
  palette: Palette;
  onModeChange: (m: CourseExploreMode) => void;
}

export function ModeToggle({ mode, palette, onModeChange }: ModeToggleProps) {
  return (
    <div style={{
      display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, padding: 6,
      background: 'var(--paper)', borderRadius: 14, border: '1px solid var(--line)',
    }}>
      {/* PDF 모드 버튼 */}
      <button
        onClick={() => onModeChange('pdf')}
        style={{
          padding: '16px 14px', borderRadius: 11, textAlign: 'left',
          background: mode === 'pdf' ? palette.wash : 'transparent',
          border: mode === 'pdf' ? `1.5px solid ${palette.soft}` : '1.5px solid transparent',
          transition: 'all .2s',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon name="pdf" size={18} color={mode === 'pdf' ? palette.primary : 'var(--ink-3)'} />
          <span style={{ fontSize: 13, fontWeight: 800, color: mode === 'pdf' ? palette.primary : 'var(--ink-2)' }}>
            PDF로 시작하기
          </span>
        </div>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>
          내 교재 · 강의자료 업로드
        </div>
      </button>

      {/* 주제 입력 모드 버튼 */}
      <button
        onClick={() => onModeChange('topic')}
        style={{
          padding: '16px 14px', borderRadius: 11, textAlign: 'left',
          background: mode === 'topic' ? palette.wash : 'transparent',
          border: mode === 'topic' ? `1.5px solid ${palette.soft}` : '1.5px solid transparent',
          transition: 'all .2s',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon name="edit" size={18} color={mode === 'topic' ? palette.primary : 'var(--ink-3)'} />
          <span style={{ fontSize: 13, fontWeight: 800, color: mode === 'topic' ? palette.primary : 'var(--ink-2)' }}>
            주제로 시작하기
          </span>
        </div>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>
          주제만 써도 흐름 구성
        </div>
      </button>
    </div>
  );
}
