// 온보딩 3단계(필수): PDF 업로드나 주제 입력으로 학습 자료를 준비한다.
import type { Palette } from '@/lib';
import type { CourseExploreMode } from '@/components/course-explore/ModeToggle';
import { ModeToggle } from '@/components/course-explore/ModeToggle';
import { PdfUploadPanel } from '@/components/course-explore/PdfUploadPanel';

export interface StepPaletteProps {
  palette: Palette;
}

interface OnboardingStep3Props {
  palette: Palette;
  mode: CourseExploreMode;
  setMode: (m: CourseExploreMode) => void;
  file: File | null;
  onFileSelect: (file: File) => void;
  onFileClear: () => void;
  topic: string;
  onTopicChange: (v: string) => void;
}

export function OnboardingStep3({
  palette, mode, setMode, file, onFileSelect, onFileClear, topic, onTopicChange,
}: OnboardingStep3Props) {
  return (
    <div>
      <h1 style={{ margin: 0, fontSize: 32, fontWeight: 800, letterSpacing: 0, color: 'var(--ink)' }}>
        학습 자료를 준비해주세요.
      </h1>
      <p style={{ marginTop: 10, fontSize: 14, color: 'var(--ink-3)' }}>
        PDF를 올리거나 주제를 입력하면 슬라이드 · 퀴즈 · 노트가 자동 생성됩니다.
      </p>

      <div style={{ marginTop: 24 }}>
        <ModeToggle mode={mode} palette={palette} onModeChange={setMode} />
      </div>

      <div style={{ marginTop: 14 }}>
        {mode === 'pdf' ? (
          <PdfUploadPanel
            file={file}
            palette={palette}
            onFileSelect={onFileSelect}
            onFileClear={onFileClear}
          />
        ) : (
          <div style={{
            padding: '22px 24px', borderRadius: 16, background: 'var(--paper)',
            border: '1px solid var(--line)',
          }}>
            <div style={{ fontSize: 12, fontWeight: 800, color: 'var(--ink)', marginBottom: 10 }}>
              어떤 걸 공부하고 싶어요?
            </div>
            <textarea
              value={topic}
              onChange={e => onTopicChange(e.target.value)}
              placeholder="예) 중학교 3학년 이차함수, 기초부터 심화까지"
              style={{
                width: '100%', minHeight: 100, resize: 'none',
                padding: '14px 16px', borderRadius: 12, border: '1.5px solid var(--line)',
                fontSize: 14, color: 'var(--ink)', background: 'var(--paper)',
                fontFamily: 'inherit', lineHeight: 1.6, outline: 'none',
              }}
              onFocus={e => (e.target.style.borderColor = palette.primary)}
              onBlur={e => (e.target.style.borderColor = 'var(--line)')}
            />
            <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 6 }}>
              한 줄만 써도 단계와 분량을 맞춰 구체화합니다
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
