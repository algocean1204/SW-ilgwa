// 출제 중 대기 화면. AI 생성은 수십 초에서 수 분이 걸려 폴링하는 동안 진행 상태를 보인다.
// TIMEOUT_MS가 지나거나 FAILED이면 재시도·나중에 확인 버튼을 보여 무한 대기를 끊는다.
import { useEffect, useRef, useState } from 'react';
import { ProgressRing } from '@/components/animated';
import type { Palette } from '@/lib';

// 이 시간을 넘으면 지연 화면으로 바꾼다. ExamForge 20문항(generate_questions→verify_answers)이
// 실측 약 4분이라, 정상 생성 중에 지연 화면이 뜨지 않게 5분으로 잡았다.
const TIMEOUT_MS = 5 * 60 * 1000;
const TICK_MS = 1000;

/** 경과 시간을 "X분 Y초 경과"로 보여 화면이 멈추지 않았음을 알린다. */
function formatElapsed(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  if (min === 0) return `${sec}초 경과`;
  return `${min}분 ${sec}초 경과`;
}

interface ExamGeneratingViewProps {
  palette: Palette;
  /** 상위에서 FAILED를 감지하면 true. 바로 지연 화면으로 바꾼다. */
  isFailed?: boolean;
  /**
   * 자료 부족(needs_more_source)으로 시험 구성에 실패한 경우 true.
   * 일반 FAILED 화면 대신 자료 부족 안내와 행동 제안을 보인다. isFailed보다 먼저 본다.
   */
  needsMoreSource?: boolean;
  onRetry: () => void;
  onLater: () => void;
}

export default function ExamGeneratingView({
  palette,
  isFailed = false,
  needsMoreSource = false,
  onRetry,
  onLater,
}: ExamGeneratingViewProps) {
  // 마운트 이후 경과 시간(ms)
  const [elapsedMs, setElapsedMs] = useState(0);
  // 틱 누적(prev + TICK_MS)은 백그라운드 탭에서 타이머가 느려지면 덜 세므로 Date.now 차이로 잰다.
  // 렌더 중 Date.now()는 react-hooks/purity에 걸려 마운트 effect에서 초기화한다.
  const startedAtRef = useRef(0);

  useEffect(() => {
    // 재생성을 연달아 눌러 다시 마운트돼도 매번 0부터 센다.
    startedAtRef.current = Date.now();
    // 탭으로 돌아온 직후 첫 틱에서도 실제 경과 시간이 반영된다.
    const id = setInterval(() => {
      setElapsedMs(Date.now() - startedAtRef.current);
    }, TICK_MS);
    return () => clearInterval(id);
  }, []);

  // 자료 부족(needs_more_source)은 일반 FAILED보다 먼저 분기해 원인과 해결책을 보인다.
  if (needsMoreSource) {
    return (
      <div style={{
        background: 'var(--surface-card)', border: '1px solid var(--honey-soft)',
        borderRadius: 16, boxShadow: 'var(--shadow-md)',
        padding: '48px 24px', textAlign: 'center',
      }}>
        <h2 style={{ margin: '0 0 8px', fontSize: 20, fontWeight: 800, color: 'var(--text-primary)' }}>
          현재 자료로는 충분한 문항을 만들기 어려워요
        </h2>
        <p style={{ margin: '0 0 20px', fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
          출제할 만한 고유한 내용이 부족했어요.<br />
          아래 방법으로 다시 시도하면 더 잘 만들 수 있어요.
        </p>
        {/* 행동 제안: 문항 수 줄이기, 챕터(강의) 추가, 범위 확장 */}
        <ul style={{
          listStyle: 'none', margin: '0 auto 26px', padding: 0,
          maxWidth: 360, textAlign: 'left',
          display: 'flex', flexDirection: 'column', gap: 8,
        }}>
          {[
            '문항 수를 줄여서 다시 만들어 보기',
            '강의(챕터)를 더 듣고 학습 범위 넓히기',
            '출제 범위를 더 넓게 설정하기',
          ].map((tip) => (
            <li key={tip} style={{
              display: 'flex', alignItems: 'flex-start', gap: 8,
              padding: '10px 14px', borderRadius: 10,
              background: 'var(--state-warning-bg)', border: '1px solid var(--honey-soft)',
              fontSize: 13, fontWeight: 600, color: 'var(--honey-ink)', lineHeight: 1.5,
            }}>
              <span aria-hidden style={{ fontWeight: 800 }}>·</span>
              <span>{tip}</span>
            </li>
          ))}
        </ul>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
          <button
            onClick={onRetry}
            className="lift"
            style={{
              padding: '11px 22px', borderRadius: 10, fontSize: 13, fontWeight: 800,
              background: palette.primary, color: 'var(--paper)',
              boxShadow: `0 8px 18px -10px ${palette.primary}`,
            }}
          >
            옵션 바꿔 다시 만들기
          </button>
          <button
            onClick={onLater}
            style={{
              padding: '11px 22px', borderRadius: 10, fontSize: 13, fontWeight: 700,
              background: 'var(--surface-page)', color: 'var(--text-secondary)',
              border: '1px solid var(--border-soft)',
            }}
          >
            목록으로
          </button>
        </div>
      </div>
    );
  }

  const isDelayed = isFailed || elapsedMs >= TIMEOUT_MS;

  if (isDelayed) {
    return (
      <div style={{
        background: 'var(--surface-card)', border: '1px solid var(--border-default)',
        borderRadius: 16, boxShadow: 'var(--shadow-md)',
        padding: '64px 24px', textAlign: 'center',
      }}>
        <h2 style={{ margin: '0 0 8px', fontSize: 20, fontWeight: 800, color: 'var(--text-primary)' }}>
          생성이 지연되고 있어요
        </h2>
        <p style={{ margin: '0 0 28px', fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
          {isFailed
            ? 'AI 서버에서 생성 오류가 발생했어요.'
            : '예상보다 시간이 오래 걸리고 있어요.'}<br />
          다시 시도하거나 나중에 돌아와서 확인해 주세요.
        </p>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
          <button
            onClick={onRetry}
            className="lift"
            style={{
              padding: '11px 22px', borderRadius: 10, fontSize: 13, fontWeight: 800,
              background: palette.primary, color: 'var(--paper)',
              boxShadow: `0 8px 18px -10px ${palette.primary}`,
            }}
          >
            다시 시도
          </button>
          <button
            onClick={onLater}
            style={{
              padding: '11px 22px', borderRadius: 10, fontSize: 13, fontWeight: 700,
              background: 'var(--surface-page)', color: 'var(--text-secondary)',
              border: '1px solid var(--border-soft)',
            }}
          >
            나중에 확인
          </button>
        </div>
      </div>
    );
  }

  return (
    <div style={{
      background: 'var(--surface-card)', border: '1px solid var(--border-default)',
      borderRadius: 16, boxShadow: 'var(--shadow-md)',
      padding: '64px 24px', textAlign: 'center',
    }}>
      {/* 완료 시점을 알 수 없어 72%로 채운 링으로 진행 중임만 표시한다 */}
      <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 18 }}>
        <ProgressRing
          value={72}
          size={64}
          strokeWidth={6}
          color={palette.primary2}
          trackColor="var(--border-soft)"
        />
      </div>
      <h2 style={{ margin: '0 0 8px', fontSize: 20, fontWeight: 800, color: 'var(--text-primary)' }}>
        문제를 출제하고 있어요
      </h2>
      <p style={{ margin: 0, fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
        AI가 전체 커리큘럼에서 실전 문항을 구성하는 중이에요.<br />
        {/* ExamForge 20문항 실측 약 4분을 기준으로 잡은 범위 */}
        보통 3~5분 정도 걸려요 · 화면을 닫아도 출제는 계속 진행됩니다.
      </p>
      <p style={{ margin: '10px 0 0', fontSize: 12, fontWeight: 700, color: palette.primary }}>
        {formatElapsed(elapsedMs)}
      </p>
    </div>
  );
}
