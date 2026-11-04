// 시험 제한 시간을 1초 단위로 센다. 0이 되면 onExpire를 한 번 불러 자동 제출한다.
import { useEffect, useRef, useState } from 'react';

interface ExamTimerProps {
  /** 제한 시간(분). null이면 무제한이라 타이머를 그리지 않는다. */
  limitMinutes: number | null;
  primary: string;
  /** 남은 시간이 0이 되면 한 번 호출된다. */
  onExpire: () => void;
}

function fmt(totalSec: number): string {
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export default function ExamTimer({ limitMinutes, primary, onExpire }: ExamTimerProps) {
  const [remain, setRemain] = useState<number>((limitMinutes ?? 0) * 60);
  // onExpire가 바뀌어도 타이머가 다시 시작되지 않게 ref로 최신 값을 둔다.
  const expireRef = useRef(onExpire);

  useEffect(() => {
    expireRef.current = onExpire;
  }, [onExpire]);

  useEffect(() => {
    if (limitMinutes == null) return;
    const id = setInterval(() => {
      setRemain((prev) => {
        if (prev <= 1) {
          clearInterval(id);
          expireRef.current();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(id);
  }, [limitMinutes]);

  if (limitMinutes == null) return null;

  // 5분 이하로 남으면 위험 색으로 바꾼다.
  const danger = remain <= 300;
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '10px 12px', borderRadius: 10,
      background: danger ? 'var(--state-danger-bg)' : 'var(--surface-page)',
      border: `1px solid ${danger ? 'var(--state-danger)' : 'var(--border-soft)'}`,
    }}>
      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-tertiary)', letterSpacing: '.04em' }}>
        남은 시간
      </span>
      <span style={{
        fontSize: 18, fontWeight: 800, letterSpacing: '.02em',
        fontVariantNumeric: 'tabular-nums',
        color: danger ? 'var(--coral-ink-deep)' : primary,
      }}>
        {fmt(remain)}
      </span>
    </div>
  );
}
