// 리포트 페이지 헤더. 제목, 서브타이틀, 주간/월간 토글을 보여준다.
import type { Palette } from '@/lib';
import SegmentedControl from '@/components/ui/SegmentedControl';

interface ReportHeaderProps {
  palette: Palette;
  range: string;
  setRange: (v: string) => void;
  weekLabel?: string;
  totalMin?: number;
}

// 토글 옵션. 이 배열 순서대로 그린다.
const RANGE_OPTIONS = [
  { id: 'week', label: '주간' },
  { id: 'month', label: '월간' },
];

function getEncouragement(totalMin: number): string {
  if (totalMin >= 300) return '대단해요!';
  if (totalMin >= 120) return '잘 하고 있어요';
  if (totalMin > 0) return '조금씩 나아가고 있어요';
  return '시작해볼까요?';
}

function ReportHeader({ palette, range, setRange, weekLabel, totalMin = 0 }: ReportHeaderProps) {
  const rangeLabel = range === 'month' ? 'MONTHLY REPORT' : 'WEEKLY REPORT';
  const periodLabel = weekLabel || '';
  const encouragement = getEncouragement(totalMin);
  const hours = Math.floor(totalMin / 60);
  const mins = totalMin % 60;

  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 18, marginBottom: 'var(--section-gap)' }}>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--muted)', letterSpacing: '.15em', textTransform: 'uppercase' }}>
          {rangeLabel}{periodLabel ? ` · ${periodLabel}` : ''}
        </div>
        <h1 style={{ margin: '6px 0 0', fontSize: 32, fontWeight: 800, color: 'var(--ink)', letterSpacing: '-.03em' }}>
          {range === 'month' ? '이번 달,' : '이번 주,'}{' '}
          {/* fontWeight는 .script 클래스의 850을 따른다 */}
          <span className="script" style={{ fontSize: 32, color: palette.primary }}>
            {encouragement}
          </span>
        </h1>
        <p style={{ margin: '8px 0 0', fontSize: 13, color: 'var(--ink-3)', lineHeight: 1.6 }}>
          총 학습 시간{' '}
          <b style={{ color: palette.primary }}>{hours}h {mins}m</b>
        </p>
      </div>

      {/* 주간/월간 토글 */}
      <SegmentedControl
        items={RANGE_OPTIONS}
        value={range}
        onChange={setRange}
      />
    </div>
  );
}

export { ReportHeader };
