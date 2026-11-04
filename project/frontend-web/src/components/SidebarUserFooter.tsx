// 사이드바 하단 유저 패널. 아바타, 이름, 플랜, 설정 버튼을 한 줄에 둔다.
// 코인은 헤더 칩에서만 보여준다.

import { useState, useEffect } from 'react';
import Icon from '@/Icon';
import { getProfile } from '@/apis';
import type { AppState, RouteSetter, SubscriptionPlan } from '@/shared/types';
import type { UserProfileResponse } from '@/apis';

interface SidebarUserFooterProps {
  appState: AppState;
  setRoute: RouteSetter;
  paletteProps: {
    primary: string;
    primary2: string;
    wash: string;
    soft: string;
  };
}

function SidebarUserFooter({ appState, setRoute, paletteProps: palette }: SidebarUserFooterProps) {
  const { plan, authReady } = appState;

  // 마운트 시 프로필 API로 닉네임과 레벨을 가져온다.
  // authReady가 false면 부트스트랩 전이라 보호 API를 부르지 않는다.
  const [profile, setProfile] = useState<UserProfileResponse | null>(null);

  useEffect(() => {
    if (!authReady) return;
    void getProfile().then((res) => {
      if (res.data) setProfile(res.data);
    }).catch(() => {});
  }, [authReady]);

  return (
    <div style={{
      margin: '0 10px 10px',
      borderRadius: 12,
      border: '1px solid var(--line-2)',
      overflow: 'hidden',
      background: 'var(--paper)',
    }}>
      {/* 유저 행: 아바타 · 이름 · 플랜 · 설정 버튼 */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 12px',
      }}>
        {/* 아바타 */}
        <div style={{
          width: 32,
          height: 32,
          borderRadius: '50%',
          background: palette.primary,
          color: 'var(--paper)',
          fontSize: 12,
          fontWeight: 800,
          display: 'grid',
          placeItems: 'center',
          flexShrink: 0,
        }}>
          {profile?.nickname?.charAt(0) ?? '?'}
        </div>

        {/* 이름 + 플랜 */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 12, fontWeight: 800, color: 'var(--ink)', lineHeight: 1.3 }}>
            {profile?.nickname ?? '사용자'}
          </div>
          <div style={{
            fontSize: 12,
            color: palette.primary,
            display: 'flex',
            alignItems: 'center',
            gap: 3,
            marginTop: 1,
          }}>
            <Icon name="sparkle" size={9} />
            <span>{planLabel(plan)}</span>
          </div>
        </div>

        {/* 설정 버튼 */}
        <button
          onClick={() => setRoute('settings')}
          style={{
            width: 28,
            height: 28,
            borderRadius: 8,
            background: 'var(--sand)',
            display: 'grid',
            placeItems: 'center',
            flexShrink: 0,
            transition: 'background .15s',
          }}
          title="설정"
        >
          <Icon name="settings" size={13} color="var(--ink-3)" />
        </button>
      </div>
    </div>
  );
}

function planLabel(p: SubscriptionPlan): string {
  return p === 'premium' ? 'Premium' : p === 'pro' ? 'Pro' : 'Free';
}

export default SidebarUserFooter;
