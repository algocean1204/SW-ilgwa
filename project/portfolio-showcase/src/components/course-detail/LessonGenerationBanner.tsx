// 확정 커리큘럼 뷰에 붙는 강의 생성 진행 배너.
//
// 확정 직후 FastAPI가 백그라운드로 강의를 만드는 동안(LOCKED 챕터가 남은 동안)
// "강의 생성 중 N/M · {단계라벨} · {percent}%"와 예상 소요 시간 문구를 보여준다.
// 폴링은 상위(CourseDetail)가 useConfirmedLessonProgress로 한 번만 하고 결과를 넘긴다.
// 배너와 ChapterList 배지가 같은 결과를 써서 호출이 겹치지 않는다.
import type { Palette } from '@/lib';
import type { CourseStatus } from '@/apis';
import Icon from '@/Icon';
import type { ConfirmedLessonProgressState } from './useConfirmedLessonProgress';
import { formatElapsed, shouldShowGenerationBanner } from './lessonGenerationVisibility';

interface LessonGenerationBannerProps {
  palette: Palette;
  /** 확정(readOnly) 단계인지. false면 배너를 띄우지 않는다 */
  readOnly: boolean;
  /** 코스 상태. 종료/실패 계열이면 배너를 띄우지 않는다 */
  courseStatus?: CourseStatus;
  /** 상위에서 주입한 폴링 진행 상태 */
  progress: ConfirmedLessonProgressState;
}

export function LessonGenerationBanner({
  palette, readOnly, courseStatus, progress,
}: LessonGenerationBannerProps) {
  const {
    fetched, chapters, readyCount, pendingCount,
    progressPct, generatingChapter, currentStage,
    pollError, elapsedMs,
  } = progress;

  const visible = shouldShowGenerationBanner({
    readOnly,
    courseStatus,
    chapters,
    fetched,
  });
  if (!visible) return null;

  const total = chapters.length;
  // N/M 라벨. 챕터 목록을 아직 못 받았으면(M=0) 개수 대신 준비 안내를 띄운다
  const countLabel = total > 0 ? `${readyCount} / ${total} 챕터 완료` : '강의 준비 중';
  // 단계 라벨. generation-status를 받았을 때만 챕터 접두어를 붙여 만든다
  const stageText = currentStage
    ? `${generatingChapter ? `${generatingChapter.order}강 ` : ''}${currentStage.label}`
      + (currentStage.percent > 0 ? ` · ${currentStage.percent}%` : '')
    : null;

  return (
    <div
      style={{
        display: 'flex', alignItems: 'flex-start', gap: 14,
        padding: '16px 20px', borderRadius: 14, marginBottom: 22,
        background: palette.wash, border: `1px solid ${palette.soft}`,
      }}
      role="status"
      aria-live="polite"
    >
      {/* 펄스 아이콘 */}
      <div style={{
        width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
        background: 'var(--paper)', border: `1.5px solid ${palette.primary}`,
        display: 'grid', placeItems: 'center',
        animation: 'lgb-pulse 2s ease-in-out infinite',
      }}>
        <Icon name="sparkle" size={18} color={palette.primary} />
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        {/* 헤드라인: 강의 생성 중 N/M · 단계라벨 · percent% */}
        <div style={{ fontSize: 13, fontWeight: 800, color: 'var(--ink)', display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', gap: 6 }}>
          <span>강의 생성 중 · {countLabel}</span>
          {stageText && (
            <span style={{ fontSize: 12, fontWeight: 700, color: palette.primary }}>
              {stageText}
            </span>
          )}
          {/* status 조회가 실패하면 단계 대신 경과 시간만 보여준다 */}
          {!stageText && pollError && (
            <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-3)' }}>
              {formatElapsed(elapsedMs)}
            </span>
          )}
        </div>

        {/* 첫 강의 소요 시간과, 페이지를 떠나도 계속된다는 안내 */}
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 3, lineHeight: 1.5 }}>
          첫 강의는 보통 2~5분 안에 열려요 · 이 페이지를 떠나도 생성은 계속 진행돼요
        </div>

        {/* 진행 바. 챕터 목록을 받았고(M>0) 미완 챕터가 있을 때만 보인다 */}
        {total > 0 && pendingCount > 0 && (
          <div style={{ marginTop: 10 }}>
            <div style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
              marginBottom: 5, fontSize: 11,
            }}>
              <span style={{ color: 'var(--ink-3)', fontWeight: 600 }}>생성 진행</span>
              <span style={{ color: palette.primary, fontWeight: 800 }}>{progressPct}%</span>
            </div>
            <div style={{ height: 5, borderRadius: 999, background: 'var(--sand-2)', overflow: 'hidden' }}>
              <div style={{
                height: '100%', width: `${progressPct}%`, background: palette.primary,
                borderRadius: 999, transition: 'width .6s ease',
              }} />
            </div>
          </div>
        )}
      </div>

      <style>{`
        @keyframes lgb-pulse {
          0%, 100% { transform: scale(1); opacity: 1; }
          50% { transform: scale(1.08); opacity: 0.85; }
        }
      `}</style>
    </div>
  );
}
