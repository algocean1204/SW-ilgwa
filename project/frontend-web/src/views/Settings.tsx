// 설정 페이지. 탭 상태와 사이드바를 두고, 탭 내용은 components/settings가 그린다.
import { useState, useEffect } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import Icon from '@/Icon';
import { SettingsAccount }  from '@/components/settings/SettingsAccount';
import { SettingsNotify }   from '@/components/settings/SettingsNotify';
import { SettingsTelegram } from '@/components/settings/SettingsTelegram';
import { SettingsBilling }  from '@/components/settings/SettingsBilling';
import { SettingsPrivacy }  from '@/components/settings/SettingsPrivacy';
import SectionFrame         from '@/components/ui/SectionFrame';
import PageIntro            from '@/components/ui/PageIntro';

interface SettingsProps {
  setRoute: RouteSetter;
}

// 사이드바 탭. 화면 순서는 이 배열 순서를 따른다.
const TABS = [
  { id: "account",  l: "계정",        i: "user"     },
  { id: "notify",   l: "알림",        i: "bell"     },
  { id: "telegram", l: "텔레그램 연동", i: "telegram" },
  { id: "billing",  l: "결제 · 구독", i: "credit"   },
  { id: "privacy",  l: "개인정보",    i: "lock"     },
];

function Settings({ setRoute }: SettingsProps) {
  const t = useTweaks();
  const palette: Palette = PALETTES[t.palette] ?? PALETTES.grass!;
  // 암기노트의 '연동하러 가기' 등에서 들어오면 localStorage 플래그로 첫 탭을 정한다.
  // 초기화 함수는 읽기만 해서 StrictMode 이중 렌더에서도 같은 값이 나온다.
  const [tab, setTab] = useState(() => localStorage.getItem('settings_init_tab') ?? 'account');

  // 플래그 삭제는 부수효과라 useEffect에서 한다.
  // 초기화 함수 안에서 지우면 StrictMode 두 번째 렌더가 'account'로 덮어쓴다.
  useEffect(() => {
    if (localStorage.getItem('settings_init_tab')) {
      localStorage.removeItem('settings_init_tab');
    }
  }, []);

  return (
    <div style={{ maxWidth: 1040, margin: "0 auto", padding: "20px var(--page-px) 48px" }}>
      {/* 페이지 헤더 */}
      <div style={{ marginBottom: 22 }}>
        <PageIntro
          eyebrow="SETTINGS"
          title="설정"
          description="계정, 알림, 결제, 개인정보 설정을 한 곳에서 관리하세요"
          compact
        />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "220px 1fr", gap: 18 }}>
        {/* sticky 사이드바. 각 button id는 PAGE_CONTEXT_MANIFEST의 settings feature id와 같다 */}
        <div style={{
          padding: 8, borderRadius: 14,
          background: "var(--paper)", border: "1px solid var(--line)",
          alignSelf: "flex-start", position: "sticky", top: 20,
        }}>
          {TABS.map(x => {
            const active = tab === x.id;
            // 매니페스트 id와 내부 탭 id가 달라 notify → notification으로 매핑한다
            const anchorId = x.id === "notify" ? "notification" : x.id;
            return (
              <button
                key={x.id}
                id={anchorId}
                onClick={() => setTab(x.id)}
                style={{
                  width: "100%", display: "flex", alignItems: "center", gap: 10,
                  padding: "10px 12px", borderRadius: 9, textAlign: "left",
                  background: active ? palette.wash : "transparent",
                  color: active ? palette.primary : "var(--ink-2)",
                  fontSize: 12.5, fontWeight: active ? 800 : 600,
                  transition: "all .15s",
                }}
              >
                <Icon
                  name={x.i}
                  size={15}
                  color={active ? palette.primary : "var(--ink-3)"}
                  strokeWidth={active ? 1.9 : 1.6}
                />
                {x.l}
                {active && (
                  <span style={{ marginLeft: "auto" }}>
                    <Icon name="arrowRight" size={11} color={palette.primary} />
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* 탭 콘텐츠 영역 */}
        <SectionFrame tone="transparent" padded={false}>
          {tab === "account"  && <SettingsAccount  palette={palette} setRoute={setRoute} />}
          {tab === "notify"   && <SettingsNotify   palette={palette} />}
          {tab === "telegram" && <SettingsTelegram palette={palette} />}
          {tab === "billing"  && <SettingsBilling  palette={palette} setRoute={setRoute} />}
          {tab === "privacy"  && <SettingsPrivacy  palette={palette} />}
        </SectionFrame>
      </div>
    </div>
  );
}

export default Settings;
