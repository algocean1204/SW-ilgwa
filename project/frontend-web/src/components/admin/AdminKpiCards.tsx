// 관리자 대시보드 KPI 카드 4개.
// totalUsers, weeklySubmissionRate, averageProgress, unansweredCount를 보여준다.
import type { AdminDashboardResponse } from '@/apis/admin';

// 부모 컴포넌트와 같이 쓰는 KPI 통계 타입
export type KpiStats = AdminDashboardResponse;

interface AdminKpiCardsProps {
  stats: KpiStats;
  primary: string;
}

interface KpiItem {
  label: string;
  value: string;
  sub: string;
}

function buildKpiItems(stats: KpiStats): KpiItem[] {
  return [
    {
      label: '활성 사용자',
      value: String(stats.totalUsers),
      sub: '전체 가입 사용자 수',
    },
    {
      label: '주간 제출률',
      value: `${Math.round(stats.weeklySubmissionRate * 100)}%`,
      sub: '이번 주 과제 제출률',
    },
    {
      label: '평균 진행률',
      value: `${Math.round(stats.averageProgress)}%`,
      sub: '전체 사용자 평균',
    },
    {
      label: '미응답 세션',
      value: String(stats.unansweredCount),
      sub: '답변 대기 중인 세션',
    },
  ];
}

function KpiCard({ item, primary }: { item: KpiItem; primary: string }) {
  return (
    <div style={{
      flex: 1,
      padding: '16px 18px',
      borderRadius: 12,
      background: 'var(--paper)',
      border: '1px solid var(--line)',
      boxShadow: 'var(--shadow-sm)',
    }}>
      <div style={{
        fontSize: 10,
        fontWeight: 700,
        color: 'var(--ink-3)',
        letterSpacing: '.08em',
        textTransform: 'uppercase',
        marginBottom: 8,
      }}>
        {item.label}
      </div>
      <div style={{ fontSize: 32, fontWeight: 800, color: primary, letterSpacing: '-.03em' }}>
        {item.value}
      </div>
      <div style={{ fontSize: 11, marginTop: 4, color: 'var(--ink-3)' }}>
        {item.sub}
      </div>
    </div>
  );
}

function AdminKpiCards({ stats, primary }: AdminKpiCardsProps) {
  const items = buildKpiItems(stats);
  return (
    <div style={{ display: 'flex', gap: 12 }}>
      {items.map(item => (
        <KpiCard key={item.label} item={item} primary={primary} />
      ))}
    </div>
  );
}

export default AdminKpiCards;
