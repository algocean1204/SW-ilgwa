// KPI 카드, 검색·필터, 사용자 테이블을 묶은 영역.
// 마운트 시 KPI와 사용자 목록을 병렬로 불러오고 검색·필터가 바뀌면 다시 조회한다.
import { useState, useEffect, useCallback } from 'react';
import { getAdminDashboard, getAdminUsers, suspendUser } from '@/apis/admin';
import type { AdminDashboardResponse, AdminUserListResponse, AdminSubscriptionStatus, AdminUserStatus, OffsetPageResponse } from '@/apis';
import AdminKpiCards from './AdminKpiCards';
import AdminUserDataTable from './AdminUserDataTable';
import AdminUserAside from './AdminUserAside';
import PageContentWithAside from '@/components/layout/PageContentWithAside';

interface AdminUserTableProps {
  primary: string;
}

function AdminUserTable({ primary }: AdminUserTableProps) {
  const [kpi, setKpi] = useState<AdminDashboardResponse | null>(null);
  const [page, setPage] = useState<OffsetPageResponse<AdminUserListResponse> | null>(null);
  const [keyword, setKeyword] = useState('');
  const [subFilter, setSubFilter] = useState<AdminSubscriptionStatus | ''>('');
  const [statusFilter, setStatusFilter] = useState<AdminUserStatus | ''>('');
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(true);

  // KPI는 최초 한 번만 로드
  useEffect(() => {
    getAdminDashboard()
      .then(res => { if (res.data) setKpi(res.data); })
      .catch(() => {});
  }, []);

  const loadUsers = useCallback(() => {
    setLoading(true);
    getAdminUsers({
      keyword: keyword || undefined,
      subscriptionStatus: subFilter || undefined,
      status: statusFilter || undefined,
      page: currentPage,
      size: 20,
    })
      .then(res => { if (res.data) setPage(res.data); })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [keyword, subFilter, statusFilter, currentPage]);

  useEffect(() => { loadUsers(); }, [loadUsers]);

  // suspendUser가 성공한 뒤에만 로컬 목록을 바꾼다
  async function handleSuspended(userId: string, reason?: string) {
    try {
      await suspendUser(userId, reason ? { reason } : undefined);
    } catch {
      return;
    }
    setPage(prev => {
      if (!prev) return prev;
      return {
        ...prev,
        content: prev.content.map(u => u.id === userId ? { ...u, status: 'SUSPENDED' as AdminUserStatus } : u),
      };
    });
  }

  const mainContent = (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--section-gap)' }}>
      {kpi && <AdminKpiCards stats={kpi} primary={primary} />}

      {/* 검색·필터 바 */}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <input
          type="text"
          placeholder="이름·이메일 검색..."
          value={keyword}
          onChange={e => { setKeyword(e.target.value); setCurrentPage(0); }}
          style={{
            flex: 1, minWidth: 180, padding: '8px 12px', borderRadius: 8,
            border: '1px solid var(--line)', fontSize: 12, background: 'var(--paper)',
            color: 'var(--ink)', outline: 'none', fontFamily: 'inherit',
          }}
        />
        <select
          value={subFilter}
          onChange={e => { setSubFilter(e.target.value as AdminSubscriptionStatus | ''); setCurrentPage(0); }}
          style={{
            padding: '8px 12px', borderRadius: 8, border: '1px solid var(--line)',
            fontSize: 12, background: 'var(--paper)', color: 'var(--ink)',
            outline: 'none', fontFamily: 'inherit', cursor: 'pointer',
          }}
        >
          <option value="">구독 전체</option>
          <option value="FREE">Free</option>
          <option value="PRO">Pro</option>
          <option value="PREMIUM">Premium</option>
          <option value="EXPIRED">만료</option>
        </select>
        <select
          value={statusFilter}
          onChange={e => { setStatusFilter(e.target.value as AdminUserStatus | ''); setCurrentPage(0); }}
          style={{
            padding: '8px 12px', borderRadius: 8, border: '1px solid var(--line)',
            fontSize: 12, background: 'var(--paper)', color: 'var(--ink)',
            outline: 'none', fontFamily: 'inherit', cursor: 'pointer',
          }}
        >
          <option value="">상태 전체</option>
          <option value="ACTIVE">활성</option>
          <option value="SUSPENDED">정지</option>
        </select>
      </div>

      {/* 테이블 */}
      {loading ? (
        <div style={{ padding: 24, textAlign: 'center', fontSize: 13, color: 'var(--muted)' }}>
          불러오는 중...
        </div>
      ) : page ? (
        <AdminUserDataTable
          users={page.content}
          totalElements={page.totalElements}
          page={currentPage}
          totalPages={page.totalPages}
          onPageChange={p => setCurrentPage(p)}
          onSuspended={handleSuspended}
          primary={primary}
        />
      ) : null}
    </div>
  );

  return (
    <PageContentWithAside
      main={mainContent}
      aside={<AdminUserAside primary={primary} />}
      asideWidth={260}
    />
  );
}

export default AdminUserTable;
