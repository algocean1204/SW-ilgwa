// 강의 슬라이드 AI 생성 진행률 화면.
// 폴링은 useLessonGenerationPolling, 스테퍼 UI는 LessonGeneratingStepper에 있다.
import { useEffect, useState } from 'react';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import type { ChapterSummaryResponse } from '@/apis';
import Icon from '@/Icon';
import { LessonGeneratingStepper, resolveCurrentStep } from './LessonGeneratingStepper';
import { useLessonGenerationPolling, countReadyChapters } from './useLessonGenerationPolling';

const TIPS = [
  '잘 배운 내용은 설명할 수 있어야 해요. 강의 후 혼자 설명해보세요.',
  '궁금한 것이 생기면 튜터에게 바로 물어보세요. Q&A는 무료예요.',
  '짧은 휴식이 집중력을 높여줘요. 25분 집중, 5분 휴식을 추천해요.',
  '강의마다 핵심 키워드를 하나씩 메모해두면 복습이 쉬워요.',
  'AI 튜터는 당신의 학습 속도에 맞춰 설명을 조절해줘요.',
  '어려운 개념은 넘어가지 말고 튜터에게 다시 물어보세요.',
  '학습 목표를 미리 설정하면 집중도가 높아져요.',
];

/**
 * 챕터 단위 진행에 생성 중 챕터의 노드 단위 진행률을 병합한 0~100 정수 진행률.
 * total=0이면 0, stage가 null이면 챕터 단위 값을 그대로 쓴다.
 * 입력이 NaN/Infinity여도 결과가 NaN%가 되지 않게 한다.
 */
export function blendLessonProgressPct(
  readyCount: number,
  totalChapters: number,
  stagePercent: number | null,
): number {
  if (!Number.isFinite(totalChapters) || totalChapters <= 0) return 0;
  const safeReady = Number.isFinite(readyCount) ? Math.max(0, readyCount) : 0;
  const stageFraction = stagePercent !== null && Number.isFinite(stagePercent)
    ? Math.min(100, Math.max(0, stagePercent)) / 100
    : 0;
  return Math.min(100, Math.round(((safeReady + stageFraction) / totalChapters) * 100));
}

/** 경과 시간을 "X분 Y초 경과" 형식으로 반환 */
function formatElapsed(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  if (min === 0) return `${sec}초 경과`;
  return `${min}분 ${sec}초 경과`;
}

// 강의 생성 예상 상한(분). 비례식 추정이 커져도 화면에는 이 값까지만 보여 준다.
// 백엔드 안내(보통 2~5분, 최대 10분 내외)를 기준으로 잡았다.
const MAX_ESTIMATE_MIN = 10;

/**
 * 진행률(pct, 0~100)과 경과 시간(elapsedMs)을 기반으로 남은 시간 문구를 반환한다.
 * - pct < 15: 표본이 적어 비례식이 불안정하므로(분모가 작음) 고정 안내를 돌려준다
 * - pct >= 98 또는 elapsedMs가 예상 완료 시간을 초과한 경우: "마무리 중…" 반환
 * - 남은 시간 < 60초: "곧 완료돼요" 반환
 * - 그 외: "약 N분 남음" 반환 (N = 반올림된 분, 상한 MAX_ESTIMATE_MIN으로 클램프)
 */
function formatRemaining(pct: number, elapsedMs: number): string {
  // 초기 구간은 분모(pct)가 작아 비례식이 과대 추정되므로 고정 안내로 대신한다.
  // 예: 경과 162초·진행 3%면 162*(97/3)≈88분이 나온다.
  if (pct < 15) return '약 10분 예상, 잠시만요';
  // 완료 근접 구간
  if (pct >= 98) return '마무리 중…';
  // 예상 남은 밀리초 = 경과ms * (100 - pct) / pct
  const remainMs = elapsedMs * (100 - pct) / pct;
  // 경과 시간 자체가 예상 전체 시간을 초과한 경우
  if (!Number.isFinite(remainMs) || remainMs <= 0) return '마무리 중…';
  const remainSec = remainMs / 1000;
  // 1분 미만 남음
  if (remainSec < 60) return '곧 완료돼요';
  // 추정 분은 상한으로 클램프한다
  const remainMin = Math.min(Math.round(remainSec / 60), MAX_ESTIMATE_MIN);
  return `약 ${remainMin}분 남음`;
}

interface TipsCarouselProps {
  palette: Palette;
}

function TipsCarousel({ palette }: TipsCarouselProps) {
  const [idx, setIdx] = useState(0);
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    // 언마운트 뒤 누수가 없게 페이드 아웃 후 교체용 타임아웃까지 cleanup에서 정리한다
    let fadeTimer: ReturnType<typeof setTimeout> | undefined;
    const timer = setInterval(() => {
      setVisible(false);
      fadeTimer = setTimeout(() => {
        setIdx(prev => (prev + 1) % TIPS.length);
        setVisible(true);
      }, 300);
    }, 5000);
    return () => {
      clearInterval(timer);
      if (fadeTimer) clearTimeout(fadeTimer);
    };
  }, []);

  return (
    <div style={{
      width: '100%', maxWidth: 340, marginBottom: 24,
      padding: '12px 16px', borderRadius: 12,
      background: palette.wash, border: `1px solid ${palette.soft}`,
      display: 'flex', gap: 10, alignItems: 'flex-start',
    }}>
      <Icon name="sparkle" size={14} color={palette.primary} />
      <p style={{
        margin: 0, fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.6,
        opacity: visible ? 1 : 0,
        transition: 'opacity 0.3s ease',
        minHeight: 36,
      }}>
        {TIPS[idx]}
      </p>
    </div>
  );
}

interface ChapterListPreviewProps {
  chapters: ChapterSummaryResponse[];
  palette: Palette;
}

function ChapterListPreview({ chapters, palette }: ChapterListPreviewProps) {
  if (chapters.length === 0) return null;
  return (
    <div style={{ width: '100%', maxWidth: 340, marginBottom: 24 }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', marginBottom: 8, textAlign: 'left', letterSpacing: 0.5 }}>
        이런 내용을 배우게 돼요
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {chapters.map((ch) => {
          const isReady = countReadyChapters([ch]) > 0;
          return (
            <div key={ch.id} style={{
              display: 'flex', alignItems: 'center', gap: 10,
              padding: '8px 12px', borderRadius: 9,
              background: isReady ? palette.wash : 'var(--sand)',
              border: `1px solid ${isReady ? palette.soft : 'var(--line)'}`,
              transition: 'background .5s ease, border-color .5s ease',
            }}>
              <div style={{
                width: 20, height: 20, borderRadius: '50%', flexShrink: 0,
                background: isReady ? palette.primary : 'var(--sand-2)',
                display: 'grid', placeItems: 'center',
                transition: 'background .5s ease',
              }}>
                {isReady
                  ? <Icon name="check" size={10} color="var(--paper)" />
                  : <Icon name="lock" size={9} color="var(--ink-3)" />
                }
              </div>
              <span style={{
                fontSize: 12, fontWeight: isReady ? 700 : 500,
                color: isReady ? palette.primary : 'var(--ink-3)',
                flex: 1, textAlign: 'left', lineHeight: 1.3,
                transition: 'color .5s ease',
              }}>
                {ch.order}강 {ch.title}
              </span>
              {isReady && ch.slideCount > 0 && (
                <span style={{ fontSize: 10, color: palette.primary, fontWeight: 700, flexShrink: 0 }}>
                  {ch.slideCount}슬라이드
                </span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

interface LessonGeneratingProgressProps {
  courseId: string;
  palette: Palette;
  setRoute: RouteSetter;
  onFirstChapterReady: () => void;
}

export function LessonGeneratingProgress({
  courseId, palette, setRoute, onFirstChapterReady,
}: LessonGeneratingProgressProps) {
  const { chapters, readyCount, currentStage, timedOut, pollError, elapsedMs } =
    useLessonGenerationPolling({ courseId, onFirstChapterReady });

  const totalChapters = chapters.length;
  // 생성 중인 챕터의 내부 진행률(0~100%)을 챕터 단위 진행에 더해
  // 챕터 하나가 끝날 때까지 바가 멈춰 보이지 않게 한다. stage가 없으면 챕터 단위 그대로.
  const rawPct = blendLessonProgressPct(readyCount, totalChapters, currentStage?.percent ?? null);
  // 진행률은 줄지 않게 최고값으로 클램프한다(courseId가 바뀌면 리셋).
  // done 전환이나 일시 실패로 stage가 null이 되는 폴링 사이에도 바가 뒤로 가지 않는다.
  // react-hooks/refs 때문에 ref 대신 렌더 중 조건부 setState로
  // 최고값을 추적한다.
  const [maxPctState, setMaxPctState] = useState({ courseId, pct: rawPct });
  if (maxPctState.courseId !== courseId) {
    setMaxPctState({ courseId, pct: rawPct });
  } else if (rawPct > maxPctState.pct) {
    setMaxPctState({ courseId, pct: rawPct });
  }
  const progressPct = maxPctState.courseId === courseId ? Math.max(maxPctState.pct, rawPct) : rawPct;
  const currentStep = resolveCurrentStep(readyCount, totalChapters);
  // 지금 생성 중인 챕터(첫 LOCKED). 노드 단위 단계 라벨을 여기에 붙인다.
  const generatingChapter = chapters.find(c => c.status === 'LOCKED');

  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center',
      padding: '40px 24px', textAlign: 'center',
    }}>
      <div style={{
        width: 72, height: 72, borderRadius: '50%',
        background: palette.wash, display: 'grid', placeItems: 'center', marginBottom: 20,
        animation: 'lp-pulse 2s ease-in-out infinite',
      }}>
        <Icon name="sparkle" size={32} color={palette.primary} />
      </div>

      <h2 style={{ fontSize: 20, fontWeight: 800, color: 'var(--ink)', margin: '0 0 8px' }}>
        AI가 강의를 만들고 있어요
      </h2>
      <p style={{ fontSize: 13, color: 'var(--ink-3)', margin: '0 0 6px', lineHeight: 1.6 }}>
        {totalChapters > 0 ? `${totalChapters}개 챕터` : ''} 기준으로 슬라이드를 생성 중입니다<br />
        첫 강의는 보통 2~5분 안에 열려요 · 준비되면 자동으로 시작됩니다
      </p>
      {/* 생성은 서버 백그라운드에서 돌아 페이지를 떠나도 계속된다는 안내 */}
      <p style={{ fontSize: 12, color: 'var(--ink-3)', margin: '0 0 6px', lineHeight: 1.5 }}>
        이 페이지를 떠나도 생성은 계속 진행돼요. 나중에 다시 들어와서 확인해도 됩니다.
      </p>

      {/* 남은 시간(주) + 경과 시간(보조) */}
      <div style={{
        marginBottom: 24, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
      }}>
        <div style={{
          fontSize: 14, color: palette.primary, fontWeight: 800,
          display: 'flex', alignItems: 'center', gap: 5,
        }}>
          <Icon name="clock" size={13} color={palette.primary} />
          {formatRemaining(progressPct, elapsedMs)}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', fontWeight: 600 }}>
          {formatElapsed(elapsedMs)}
        </div>
      </div>

      <LessonGeneratingStepper currentStep={currentStep} palette={palette} />

      {totalChapters > 0 && (
        <div style={{ width: '100%', maxWidth: 340, marginBottom: 20 }}>
          <div style={{
            display: 'flex', justifyContent: 'space-between',
            alignItems: 'baseline', marginBottom: 6, fontSize: 12,
          }}>
            <span style={{ color: 'var(--ink-3)', fontWeight: 600 }}>챕터 생성 진행</span>
            <span style={{ color: palette.primary, fontWeight: 800 }}>
              {readyCount} / {totalChapters} ({progressPct}%)
            </span>
          </div>
          <div style={{ height: 6, borderRadius: 999, background: 'var(--sand-2)', overflow: 'hidden' }}>
            <div style={{
              height: '100%', width: `${progressPct}%`, background: palette.primary,
              borderRadius: 999, transition: 'width .6s ease',
            }} />
          </div>
          {/* 노드 단위 현재 단계: 자료 분석 → 슬라이드 생성 → 검증 → 음성 합성 → 마무리 */}
          {currentStage && (
            <div style={{
              marginTop: 8, fontSize: 12, fontWeight: 700, color: palette.primary,
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
            }}>
              <span style={{
                width: 6, height: 6, borderRadius: '50%', background: palette.primary,
                animation: 'lp-pulse 1.5s ease-in-out infinite',
              }} />
              {generatingChapter ? `${generatingChapter.order}강 ` : ''}{currentStage.label}
              {currentStage.percent > 0 ? ` · ${currentStage.percent}%` : ''}
            </div>
          )}
        </div>
      )}

      <TipsCarousel palette={palette} />

      <ChapterListPreview chapters={chapters} palette={palette} />

      {timedOut && (
        <div style={{
          padding: '12px 16px', borderRadius: 10, background: 'var(--honey-tint)',
          border: '1px solid var(--honey-mid)', fontSize: 12, color: 'var(--honey-ink)',
          marginBottom: 16, maxWidth: 320,
        }}>
          15분이 지났습니다. 서버 상태를 확인하거나 잠시 후 다시 시도해 주세요.
        </div>
      )}
      {pollError && !timedOut && (
        <div style={{
          padding: '10px 14px', borderRadius: 8, background: 'var(--sand)',
          fontSize: 11, color: 'var(--ink-3)', marginBottom: 14,
        }}>
          일시적인 네트워크 오류입니다. 자동으로 재시도 중입니다.
        </div>
      )}

      <button
        onClick={() => setRoute('myCourses')}
        style={{
          marginTop: 4, padding: '9px 16px', borderRadius: 9, fontSize: 12, fontWeight: 700,
          background: 'var(--bg)', color: 'var(--ink-2)', border: '1px solid var(--line)',
        }}
      >
        내 과외 목록
      </button>

      <style>{`
        @keyframes lp-pulse {
          0%, 100% { transform: scale(1); opacity: 1; }
          50% { transform: scale(1.08); opacity: 0.85; }
        }
      `}</style>
    </div>
  );
}
