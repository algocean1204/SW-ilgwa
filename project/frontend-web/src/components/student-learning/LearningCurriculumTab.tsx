// 커리큘럼 탭: 챕터 목록과 잠금 안내
import { useState } from 'react';
import type { Palette } from '@/lib';
import type { Course } from '@/shared/types';
import type { ChapterSummaryResponse } from '@/apis';
import { useCoinForSlide as spendCoinForSlide } from '@/apis';
import Icon from '@/Icon';
import AICoinIcon from '@/components/AICoinIcon';

type UIChapterStatus = 'done' | 'active' | 'locked';

function mapChapterStatus(status: ChapterSummaryResponse['status']): UIChapterStatus {
  if (status === 'COMPLETED') return 'done';
  if (status === 'IN_PROGRESS' || status === 'AVAILABLE') return 'active';
  return 'locked';
}

interface LearningCurriculumTabProps {
  course: Course;
  courseId: string;
  chapters: ChapterSummaryResponse[];
  activeChapterId: string;
  palette: Palette;
  onCoinsChanged?: (balance: number) => void;
  /** 잠금 해제된 챕터 클릭 시 해당 챕터로 전환 */
  onChapterSelect?: (chapterId: string) => void;
}

/** 슬라이드 진행 막대 아이템 */
interface SlideBarItemProps {
  isDone: boolean;
  isActive: boolean;
  isLocked: boolean;
  palette: Palette;
}

function SlideBarItem({ isDone, isActive, isLocked, palette }: SlideBarItemProps) {
  const title = isLocked ? '생성권 1개로 해제' : isActive ? '진행 중' : '완료';
  return (
    <div
      title={title}
      style={{
        flex: 1,
        height: 6,
        borderRadius: 2,
        background: isDone ? palette.primary : isActive ? palette.primary2 : 'var(--sand-2)',
        border: isLocked ? '1px dashed var(--line)' : 'none',
        opacity: isLocked ? 0.5 : 1,
      }}
    />
  );
}

export function LearningCurriculumTab({ course, chapters, activeChapterId, palette, onCoinsChanged, onChapterSelect }: LearningCurriculumTabProps) {
  const [unlocking, setUnlocking] = useState<string | null>(null);

  const handleUnlockSlide = (slideId: string) => {
    if (unlocking) return;
    setUnlocking(slideId);
    void spendCoinForSlide({ slideId })
      .then((res) => {
        if (res.data?.coinBalance != null) onCoinsChanged?.(res.data.coinBalance);
      })
      .catch(() => {})
      .finally(() => setUnlocking(null));
  };

  return (
    /* 패널 배경은 --bg를 그대로 쓴다 */
    <div className="scroll" style={{ flex: 1, overflowY: 'auto', padding: '14px' }}>
      {/* 과외 제목과 진행 요약. 눈에 덜 띄게 fontWeight를 낮춘다 */}
      <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-2)' }}>{course.title}</div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2, marginBottom: 12 }}>
        전체 {course.slidesTotal}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {chapters.map(c => {
          const uiStatus = mapChapterStatus(c.status);
          const isSelected = c.id === activeChapterId;
          return (
          <div
            key={c.id}
            aria-current={isSelected ? 'step' : undefined}
            data-active={isSelected ? 'true' : undefined}
            onClick={uiStatus !== 'locked' && onChapterSelect ? () => onChapterSelect(c.id) : undefined}
            style={{
            padding: '10px 12px',
            borderRadius: 10,
            background: isSelected ? palette.wash : 'var(--bg)',
            border: `1px solid ${isSelected ? palette.primary : 'var(--line-2)'}`,
            opacity: uiStatus === 'locked' ? 0.75 : 1,
            cursor: uiStatus !== 'locked' && onChapterSelect ? 'pointer' : 'default',
          }}>
            {/* 챕터 헤더: 상태 뱃지, 제목, 슬라이드 수 */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
              <div style={{
                width: 22,
                height: 22,
                borderRadius: 11,
                display: 'grid',
                placeItems: 'center',
                background: isSelected ? palette.primary : uiStatus === 'done' ? palette.soft : uiStatus === 'active' ? 'var(--paper)' : 'var(--sand-2)',
                color: isSelected ? 'var(--paper)' : uiStatus === 'done' ? palette.primary : uiStatus === 'active' ? palette.primary : 'var(--ink-3)',
                fontSize: 10,
                fontWeight: 800,
                border: isSelected || uiStatus === 'active' ? `1.5px solid ${palette.primary}` : 'none',
              }}>
                {uiStatus === 'done' ? <Icon name="check" size={10} /> : uiStatus === 'locked' ? <Icon name="lock" size={10} /> : c.order}
              </div>
              <span style={{ flex: 1, fontSize: 12, fontWeight: 700, color: 'var(--ink)' }}>{c.title}</span>
              <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>{c.slideCount}</span>
            </div>

            {/* 슬라이드 진행 막대 */}
            <div style={{ display: 'flex', gap: 3, paddingLeft: 30 }}>
              {Array.from({ length: c.slideCount }).map((_, si) => {
                const isDone = uiStatus === 'done' || (uiStatus === 'active' && si < (c.completedSlides ?? 0));
                const isActive = uiStatus === 'active' && si === (c.completedSlides ?? 0);
                const isBarLocked = !isDone && !isActive;
                return (
                  <SlideBarItem key={si} isDone={isDone} isActive={isActive} isLocked={isBarLocked} palette={palette} />
                );
              })}
            </div>

            {/* 잠금 챕터: 코인 해제 안내와 해제 버튼 */}
            {uiStatus === 'locked' && (
              <div style={{
                marginTop: 6,
                paddingLeft: 30,
                fontSize: 12,
                color: 'var(--ink-3)',
                display: 'flex',
                gap: 5,
                alignItems: 'center',
              }}>
                <Icon name="lock" size={9} /> 슬라이드당 <AICoinIcon size={10} decorative />1 · 챕터 전체 <AICoinIcon size={10} decorative />{c.slideCount}
                <button
                  onClick={() => handleUnlockSlide(c.id)}
                  disabled={unlocking === c.id}
                  style={{
                    marginLeft: 'auto', padding: '4px 10px', borderRadius: 6,
                    fontSize: 11, fontWeight: 700,
                    background: palette.wash, color: palette.primary,
                    border: `1px solid ${palette.soft}`,
                    opacity: unlocking === c.id ? 0.5 : 1,
                  }}
                >
                  {unlocking === c.id ? '해제 중...' : '해제'}
                </button>
              </div>
            )}
          </div>
          );
        })}
      </div>

      {/* 생성권 해제 안내 배너 */}
      <div style={{
        marginTop: 16,
        padding: '12px 14px',
        borderRadius: 10,
        background: 'var(--honey-tint)',
        border: '1px solid var(--honey-mid)',
        fontSize: 12,
        lineHeight: 1.55,
        color: 'var(--honey-ink)',
      }}>
        <b>안내</b> · 생성권으로 슬라이드를 해제하면 강의를 구성하는 데 시간이 걸려요 (1슬라이드당 약 30초).
        미리 <b>여러 슬라이드를 미리 잠금 해제</b>해두거나, <b>구독 요금제</b>로 전체 커리큘럼을 한번에 열어두는 것을 권장해요.
      </div>
    </div>
  );
}
