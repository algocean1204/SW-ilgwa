// 학습 헤더: 툴바 행과 제목·진행 정보 행
import type { Palette } from '@/lib';
import type { Course } from '@/shared/types';
import Icon from '@/Icon';

interface LearningHeaderProps {
  course: Course;
  chapterLabel: string;
  lessonTitle: string;
  completedSlides: number;
  totalSlides: number;
  // 현재 보고 있는 슬라이드 위치(0부터). 진행 바와 슬라이드 번호에 쓴다.
  currentSlideIndex: number;
  palette: Palette;
  slidesCollapsed: boolean;
  setSlidesCollapsed: (v: boolean | ((prev: boolean) => boolean)) => void;
  focusMode: boolean;
  setFocusMode: (v: boolean | ((prev: boolean) => boolean)) => void;
}

export function LearningHeader({
  course, chapterLabel, lessonTitle, completedSlides: _completedSlides, totalSlides, currentSlideIndex, palette,
  slidesCollapsed, setSlidesCollapsed,
  focusMode, setFocusMode,
}: LearningHeaderProps) {
  // 진행률은 completedSlides 대신 currentSlideIndex로 계산해
  // 보고 있는 슬라이드와 진행 표시를 맞춘다
  const progressPct = totalSlides > 0
    ? Math.round(((currentSlideIndex + 1) / totalSlides) * 100)
    : 0;

  return (
    <>
      {/* 툴바 행: 브레드크럼, 슬라이드 접기, 집중 모드 */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        fontSize: 12,
        color: 'var(--ink-3)',
        marginBottom: 6,
        flexWrap: 'wrap',
      }}>
        <span>{course.title}</span>
        <span style={{ opacity: 0.4 }}>/</span>
        <span style={{ color: 'var(--ink-2)', fontWeight: 600 }}>{chapterLabel}</span>
        <div style={{ flex: 1 }} />

        <button
          className="learning-tool-button"
          data-active={slidesCollapsed}
          aria-pressed={slidesCollapsed}
          onClick={() => setSlidesCollapsed(s => !s)}
          title="아키텍처 카드 + 코드 블록 접기"
          style={{
            padding: '5px 10px', borderRadius: 7, fontSize: 12, fontWeight: 700,
            background: slidesCollapsed ? palette.wash : 'var(--bg)',
            color: slidesCollapsed ? palette.primary : 'var(--ink-2)',
            border: `1px solid ${slidesCollapsed ? palette.soft : 'var(--line)'}`,
            display: 'flex', alignItems: 'center', gap: 5,
          }}
        >
          <Icon name="eye" size={11} />
          {slidesCollapsed ? '슬라이드 펼치기' : '슬라이드 접기'}
        </button>

        <button
          className="learning-tool-button"
          data-active={focusMode}
          aria-pressed={focusMode}
          onClick={() => setFocusMode(f => !f)}
          title="우측 패널 숨기고 본문에 집중"
          style={{
            padding: '5px 10px', borderRadius: 7, fontSize: 12, fontWeight: 700,
            background: focusMode ? 'var(--ink)' : 'var(--bg)',
            color: focusMode ? 'var(--paper)' : 'var(--ink-2)',
            border: `1px solid ${focusMode ? 'var(--ink)' : 'var(--line)'}`,
            display: 'flex', alignItems: 'center', gap: 5,
          }}
        >
          <Icon name="target" size={11} />
          {focusMode ? '집중 모드 끄기' : '집중 모드'}
        </button>
      </div>

      {/* 제목 + 진행 정보 행 */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 6, marginTop: 4 }}>
        <div style={{
          width: 4, height: 38, background: palette.primary,
          borderRadius: 2, marginTop: 2, flexShrink: 0,
        }} />

        <div style={{ flex: 1 }}>
          <h1 style={{
            margin: 0, fontSize: 26, fontWeight: 800,
            letterSpacing: '-.03em', color: 'var(--ink)', lineHeight: 1.25,
          }}>
            {lessonTitle}
          </h1>
          <div style={{
            marginTop: 8, fontSize: 12, color: 'var(--ink-3)',
            display: 'flex', gap: 10, alignItems: 'center',
          }}>
            {/* 현재 슬라이드 번호(1부터) */}
            <span>슬라이드 {currentSlideIndex + 1} / {totalSlides}</span>
            <div style={{
              flex: 1, maxWidth: 120, height: 4,
              background: 'var(--sand-2)', borderRadius: 2, overflow: 'hidden',
            }}>
              <div style={{
                width: `${progressPct}%`, height: '100%',
                background: palette.primary, borderRadius: 2,
                transition: 'width .3s ease',
              }} />
            </div>
            <span style={{ color: palette.primary, fontWeight: 600 }}>{progressPct}%</span>
          </div>
        </div>
      </div>
    </>
  );
}
