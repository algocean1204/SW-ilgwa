// 커리큘럼 생성 중 진행률 화면. 단계 스테퍼, 경과 시간, 대기 애니메이션을 보여준다.
// 폴링은 useCourseDetailController가 하고, 여기서는 경과 시간만 센다.
// 폴링이 챕터를 감지하면 바로 상세 화면으로 넘어가므로 챕터 단위 부분 진행 표시는 없다
// (useCurriculumPolling 참고).
import { useEffect, useRef, useState } from 'react';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import Icon from '@/Icon';
import { Breadcrumb } from './Breadcrumb';
import {
  CurriculumStepper,
  resolveStepFromElapsed,
  formatElapsed,
} from './CurriculumStepper';

// 대기 중 순환 안내 문구. 5초마다 바꿔 화면이 멈춘 것처럼 보이지 않게 한다
const WAITING_TIPS = [
  'AI가 주제를 단원 단위로 쪼개서 학습 순서를 정하고 있어요.',
  '커리큘럼이 마음에 안 들면 확정 전에 챕터를 수정할 수 있어요.',
  '확정 후에는 챕터별 강의가 순서대로 자동 생성돼요.',
  '시험일을 등록하면 일정에 맞춰 학습 캘린더가 만들어져요.',
  '챕터마다 슬라이드·퀴즈·과제가 함께 준비될 예정이에요.',
] as const;

/** 순환 안내 문구. LessonGeneratingProgress의 TipsCarousel과 같은 페이드 방식이다 */
function WaitingTipsCarousel({ palette }: { palette: Palette }) {
  const [idx, setIdx] = useState(0);
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    // 언마운트 뒤 누수가 없게 페이드 아웃 후 교체 타임아웃까지 cleanup에서 정리한다
    let fadeTimer: ReturnType<typeof setTimeout> | undefined;
    const timer = setInterval(() => {
      setVisible(false);
      fadeTimer = setTimeout(() => {
        setIdx(prev => (prev + 1) % WAITING_TIPS.length);
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
      width: '100%', maxWidth: 360, marginBottom: 20,
      padding: '11px 16px', borderRadius: 12,
      background: palette.wash, border: `1px solid ${palette.soft}`,
      display: 'flex', gap: 10, alignItems: 'flex-start',
    }}>
      <Icon name="sparkle" size={14} color={palette.primary} />
      <p style={{
        margin: 0, fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.6,
        opacity: visible ? 1 : 0, transition: 'opacity 0.3s ease', minHeight: 19,
        textAlign: 'left',
      }}>
        {WAITING_TIPS[idx]}
      </p>
    </div>
  );
}

export interface CurriculumGeneratingViewProps {
  palette: Palette;
  generationNotice: string | null;
  generationTimedOut: boolean;
  onManualPoll: () => void;
  setRoute: RouteSetter;
  /** 과외 생성 방식: 'pdf'(PDF 업로드) / 'topic'(주제 입력). 안내 문구를 고르는 데 쓴다 */
  sourceType?: string;
  /** 과외 ID. 바뀌면 경과 시간을 초기화한다 */
  courseId: string;
}

export function CurriculumGeneratingView({
  palette, generationNotice, generationTimedOut, onManualPoll, setRoute,
  sourceType, courseId,
}: CurriculumGeneratingViewProps) {
  // 경과 시간. 스테퍼 단계 추정과 표시에 쓴다.
  // react-hooks/purity 규칙상 렌더 중 Date.now()를 못 불러 마운트 effect에서 초기화한다
  const startedAtRef = useRef(0);
  const [elapsedMs, setElapsedMs] = useState(0);

  // courseId가 바뀌면 경과 시간을 바로 초기화한다. effect 안 동기 setState가 금지라
  // (react-hooks/set-state-in-effect) 렌더 중 조건부 setState로 이전 값과 비교한다
  const [prevCourseId, setPrevCourseId] = useState(courseId);
  if (prevCourseId !== courseId) {
    setPrevCourseId(courseId);
    setElapsedMs(0);
  }

  useEffect(() => {
    // courseId가 바뀌면 시작 시각을 다시 잡고 1초마다 경과 시간을 갱신한다
    startedAtRef.current = Date.now();
    const tick = setInterval(() => {
      setElapsedMs(Date.now() - startedAtRef.current);
    }, 1_000);
    return () => clearInterval(tick);
  }, [courseId]);

  // generationNotice에 "갱신됩니다"가 있으면 마지막 폴링 중이다
  const isNearComplete = generationNotice?.includes('갱신됩니다') ?? false;
  const currentStep = resolveStepFromElapsed(elapsedMs, isNearComplete);
  // 60초가 지나면 "계속 처리 중" 안내를 띄운다
  const showLongWaitNotice = elapsedMs >= 60_000;

  // sourceType별 본문 문구. pdf는 교재 분석, topic은 주제 기반
  const bodyText = sourceType === 'pdf'
    ? '업로드한 교재를 바탕으로 커리큘럼을 만들고 있습니다.'
    : '입력한 주제로 커리큘럼을 만들고 있습니다.';

  return (
    <div style={{
      maxWidth: 'var(--page-max-w)', margin: '0 auto',
      padding: 'var(--page-py) var(--page-px) 56px',
    }}>
      {/* breadcrumb 문구도 PDF/주제에 따라 달라서 sourceType을 넘긴다 */}
      <Breadcrumb setRoute={setRoute} sourceType={sourceType} />
      <div style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center',
        padding: '48px 24px', textAlign: 'center',
      }}>
        {/* 펄스 아이콘 서클 */}
        <div style={{
          width: 72, height: 72, borderRadius: '50%',
          background: palette.wash, display: 'grid', placeItems: 'center', marginBottom: 24,
          animation: 'cg-pulse 2s ease-in-out infinite',
        }}>
          <Icon name="sparkle" size={32} color={palette.primary} />
        </div>

        <h2 style={{ fontSize: 20, fontWeight: 800, color: 'var(--text-primary)', margin: '0 0 8px' }}>
          AI가 커리큘럼을 생성하고 있습니다
        </h2>
        <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: '0 0 6px', lineHeight: 1.6 }}>
          {bodyText}<br />
          예상 시간 약 10분 · 완료되면 이 페이지에서 바로 확인할 수 있습니다.
        </p>
        {/* 생성은 FastAPI 백그라운드에서 돌아 페이지를 떠나도 계속된다는 안내 */}
        <p style={{ fontSize: 12, color: 'var(--ink-3)', margin: '0 0 6px', lineHeight: 1.5 }}>
          이 페이지를 떠나도 생성은 계속 진행돼요. 내 과외 목록에서 나중에 확인해도 됩니다.
        </p>

        {/* 경과 시간 표시 */}
        <div style={{
          fontSize: 12, color: palette.primary, fontWeight: 700, marginBottom: 28,
          display: 'flex', alignItems: 'center', gap: 5,
        }}>
          <Icon name="clock" size={11} color={palette.primary} />
          {formatElapsed(elapsedMs)}
        </div>

        {/* 단계 스테퍼: 주제 분석 → 커리큘럼 설계 → 챕터 생성 → 완료 */}
        <CurriculumStepper currentStep={currentStep} palette={palette} />

        {/* 대기 중 순환 안내. 기다리는 동안 다음 단계를 미리 알려준다 */}
        <WaitingTipsCarousel palette={palette} />

        {/* 1분 경과 안내. 타임아웃 전까지만 보인다 */}
        {showLongWaitNotice && !generationTimedOut && (
          <div style={{
            marginBottom: 12, padding: '10px 16px', borderRadius: 10,
            background: palette.wash, border: `1px solid ${palette.soft}`,
            fontSize: 12, color: palette.primary, fontWeight: 700, lineHeight: 1.5,
            display: 'flex', alignItems: 'center', gap: 6,
          }}>
            <Icon name="clock" size={12} color={palette.primary} />
            AI가 계속 커리큘럼을 구성하고 있어요. 잠시만 기다려 주세요.
          </div>
        )}

        {/* generationNotice가 있으면 폴링 상태를 알려준다 */}
        {generationNotice && (
          <p style={{
            fontSize: 12,
            color: generationTimedOut ? 'var(--state-danger)' : 'var(--ink-3)',
            marginTop: 4, marginBottom: 16, lineHeight: 1.5,
          }}>
            {generationNotice}
          </p>
        )}

        {/* 수동 확인 버튼. 기본은 자동 폴링이고 이 버튼은 보조 수단이다 */}
        <button
          onClick={onManualPoll}
          style={{
            marginTop: 4, padding: '10px 20px', borderRadius: 10,
            fontSize: 13, fontWeight: 700,
            background: palette.wash, color: palette.primary,
            border: `1px solid ${palette.soft}`,
          }}
        >
          {generationTimedOut ? '다시 확인 시작' : '지금 다시 확인'}
        </button>

        {/* 1분이 넘거나 타임아웃이면 "목록으로" 링크를 띄운다 */}
        {(showLongWaitNotice || generationTimedOut) && (
          <button
            onClick={() => setRoute('myCourses')}
            style={{
              marginTop: 10, fontSize: 12, color: 'var(--ink-3)', fontWeight: 600,
              background: 'transparent', border: 'none', cursor: 'pointer',
              textDecoration: 'underline',
            }}
          >
            목록으로 돌아가서 나중에 확인하기
          </button>
        )}
      </div>

      {/* 아이콘 서클 pulse 애니메이션 */}
      <style>{`
        @keyframes cg-pulse {
          0%, 100% { transform: scale(1); opacity: 1; }
          50% { transform: scale(1.08); opacity: 0.85; }
        }
      `}</style>
    </div>
  );
}
