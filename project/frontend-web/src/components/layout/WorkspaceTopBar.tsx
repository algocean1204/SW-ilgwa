// 워크스페이스 상단 커맨드바.
// 코인 잔액은 appState.coins, 구독 등급은 appState.plan을 읽는다. 둘 다 App.tsx가 채운다.
// 닉네임만 getProfile()로 따로 조회하고 getCurrentSubscription은 부르지 않는다.
import { useState, useEffect } from 'react';
import AICoinIcon from '@/components/AICoinIcon';
import Icon from '@/Icon';
import logo from '@/assets/logo.png';
import { getProfile } from '@/apis';
import type { AppState, RouteKey, RouteSetter } from '@/shared/types';
import type { UserProfileResponse } from '@/apis';
import { WORKSPACE_ZONES, findWorkspaceZone } from '@/components/layout/workspaceShellData';

interface WorkspaceTopBarProps {
  route: RouteKey;
  setRoute: RouteSetter;
  appState: AppState;
}

function WorkspaceTopBar({ route, setRoute, appState }: WorkspaceTopBarProps) {
  const activeZone = findWorkspaceZone(route);

  // 닉네임 아바타용 프로필
  const [profile, setProfile] = useState<UserProfileResponse | null>(null);

  useEffect(() => {
    // 부트스트랩(refresh)이 끝나기 전에는 401이 나지 않게 부르지 않는다
    if (!appState.authReady) return;
    void getProfile().then((res) => {
      if (res.data) setProfile(res.data);
    }).catch(() => {});
  }, [appState.authReady]);

  // App.tsx가 getCoinBalance()로 초기화하고 소비·충전 때 setCoins()로 갱신하므로 API를 다시 부르지 않는다
  const coinBalance = appState.coins;

  return (
    <header className="workspace-commandbar">
      <button className="workspace-brandmark" onClick={() => setRoute('home')} aria-label="홈으로 이동">
        <img src={logo} alt="일과" className="workspace-brandmark__logo" />
      </button>

      <nav className="workspace-primary-nav" aria-label="주요 작업 구역">
        {WORKSPACE_ZONES
          .filter((zone) => appState.hasCourses !== false || zone.id === 'system')
          .map((zone) => (
          <button
            key={zone.id}
            className="workspace-nav-item"
            data-active={activeZone.id === zone.id}
            aria-current={activeZone.id === zone.id ? 'page' : undefined}
            onClick={() => setRoute(zone.primaryRoute)}
          >
            <Icon name={zone.icon} size={16} />
            <span>{zone.label}</span>
          </button>
        ))}
      </nav>

      <div className="workspace-actions">
        <button className="workspace-ghost-action" onClick={() => setRoute('explore')} title="새 과외 만들기">
          <Icon name="plus" size={15} />
          <span>새 과외</span>
        </button>
        <button className="workspace-coin-action" onClick={() => { localStorage.setItem('pricing_init_tab', 'coin'); setRoute('pricing'); }} title={`${coinBalance}개 생성권 관리`}>
          <AICoinIcon size={13} decorative />
          <span>{coinBalance}</span>
          <small>생성권</small>
        </button>
        <button className="workspace-avatar" onClick={() => setRoute('settings')} title="설정">
          {profile?.nickname?.charAt(0) ?? '?'}
        </button>
      </div>
    </header>
  );
}

export { WorkspaceTopBar };
