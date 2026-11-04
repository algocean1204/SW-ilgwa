// SUPER_ADMIN 전용 관리자 콘솔. 탭은 사용자 관리와 텔레그램 설정 두 개다.
// 진입 시 토큰과 dashboard API로 권한을 확인해 unauthorized, forbidden 화면으로 나눈다.
import { useEffect, useState } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import { getAccessToken, getAdminDashboard } from '@/apis';
import type { RouteSetter } from '@/shared/types';
import AdminUserTable from '@/components/admin/AdminUserTable';
import AdminTelegramSettings from '@/components/admin/AdminTelegramSettings';
import AdminAlertRules from '@/components/admin/AdminAlertRules';

interface AdminConsoleProps { setRoute: RouteSetter; appState?: unknown; }

type AuthState = 'checking' | 'unauthorized' | 'forbidden' | 'ready';
type TabKey = 'users' | 'telegram' | 'alerts';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'users',    label: '사용자 관리' },
  { key: 'telegram', label: '텔레그램 설정' },
  { key: 'alerts',   label: '알림 규칙' },
];

const HEADER_MAP: Record<TabKey, { title: string; sub: string }> = {
  users:    { title: '사용자 관리', sub: '가입 사용자 현황과 구독·정지 상태를 한자리에서 관리해요.' },
  telegram: { title: '텔레그램 설정', sub: '봇 자격증명을 갱신하고 메시지 수신자를 관리해요.' },
  alerts:   { title: '알림 규칙', sub: '에러 유형별 텔레그램 알림 발송 규칙을 관리해요.' },
};

function AdminConsole({ setRoute }: AdminConsoleProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;
  const [authState, setAuthState] = useState<AuthState>('checking');
  const [tab, setTab] = useState<TabKey>('users');

  // 토큰이 있으면 dashboard API로 SUPER_ADMIN 권한을 확인한다
  useEffect(() => {
    if (!getAccessToken()) { setAuthState('unauthorized'); return; }
    getAdminDashboard()
      .then(() => setAuthState('ready'))
      .catch((err: unknown) => {
        // 403이면 권한 부족, 401이면 미인증
        const status = (err as { status?: number })?.status;
        setAuthState(status === 403 ? 'forbidden' : 'unauthorized');
      });
  }, []);

  const { title, sub } = HEADER_MAP[tab];

  if (authState === 'checking') {
    return (
      <div style={{ maxWidth: 'var(--page-max-w)', margin: '0 auto', padding: 'var(--page-py) var(--page-px)', textAlign: 'center', color: 'var(--muted)', fontSize: 14 }}>
        권한 확인 중...
      </div>
    );
  }

  if (authState === 'unauthorized' || authState === 'forbidden') {
    const msg = authState === 'unauthorized' ? '관리자 로그인이 필요합니다' : '관리자 권한이 없습니다';
    return (
      <div style={{ maxWidth: 'var(--page-max-w)', margin: '0 auto', padding: 'var(--page-py) var(--page-px)', textAlign: 'center' }}>
        <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--coral-ink)', marginBottom: 16 }}>{msg}</div>
        <button onClick={() => { localStorage.setItem('post_login_route', 'admin'); setRoute('auth'); }} style={{
          padding: '10px 24px', borderRadius: 10, border: 'none',
          background: palette.primary, color: 'var(--paper)',
          fontSize: 13, fontWeight: 700, cursor: 'pointer',
        }}>로그인 페이지로</button>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 'var(--page-max-w)', margin: '0 auto', padding: 'var(--page-py) var(--page-px) 48px' }}>
      {/* 헤더 */}
      <header style={{ marginBottom: 'var(--section-gap)' }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: palette.primary, letterSpacing: '.1em', textTransform: 'uppercase' }}>ADMIN</div>
        <h1 style={{ margin: '6px 0 0', fontSize: 32, fontWeight: 800, letterSpacing: '-.03em' }}>
          {title} <span className="script" style={{ fontSize: 32, color: palette.primary }}>콘솔</span>
        </h1>
        <div style={{ marginTop: 5, fontSize: 13, color: 'var(--ink-3)', lineHeight: 1.5 }}>{sub}</div>
      </header>

      {/* 언더라인 탭 */}
      <div style={{ display: 'flex', gap: 0, borderBottom: '2px solid var(--line)', marginBottom: 28 }}>
        {TABS.map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            style={{
              padding: '10px 20px', fontSize: 13, fontWeight: tab === key ? 800 : 600,
              color: tab === key ? palette.primary : 'var(--ink-3)',
              background: 'none', border: 'none', borderBottom: tab === key ? `2px solid ${palette.primary}` : '2px solid transparent',
              marginBottom: -2, cursor: 'pointer', transition: 'color .15s',
            }}
          >
            {label}
          </button>
        ))}
      </div>

      {/* 탭 콘텐츠 */}
      {tab === 'users' && <AdminUserTable primary={palette.primary} />}
      {tab === 'telegram' && <AdminTelegramSettings primary={palette.primary} />}
      {tab === 'alerts' && <AdminAlertRules primary={palette.primary} />}
    </div>
  );
}

export default AdminConsole;
