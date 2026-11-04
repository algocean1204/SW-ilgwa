import { useState } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import Icon from '@/Icon';
import { devLogin } from '@/apis';
import { isDemoMode } from '@/lib/demoMode';
import { LandingHero } from '@/components/landing/LandingHero';
import { LandingLaunchPanel } from '@/components/landing/LandingLaunchPanel';

interface LandingProps {
  setRoute: RouteSetter;
}

const FLOW = [
  { icon: 'file', title: '자료 수집', body: 'PDF와 주제 입력을 한 곳에서 받습니다.' },
  { icon: 'map', title: '수업 구성', body: '챕터, 음성 수업, 과제를 같은 흐름에 묶습니다.' },
  { icon: 'chart', title: '복습 추적', body: '남은 과제와 약점을 다음 학습으로 넘깁니다.' },
];

function Landing({ setRoute }: LandingProps) {
  const t = useTweaks();
  const palette: Palette = PALETTES[t.palette] ?? PALETTES.grass!;
  const [entering, setEntering] = useState(false);

  const handleDemoEnter = async () => {
    if (entering) return;
    setEntering(true);
    try {
      await devLogin();
      setRoute('home');
    } catch {
      setRoute('auth');
    } finally {
      setEntering(false);
    }
  };

  return (
    <div className="landing-redesign">
      {isDemoMode && (
        <section className="landing-demo-banner" style={{
          margin: '0 auto',
          maxWidth: 'var(--page-max-w)',
          padding: '20px var(--page-px) 0',
        }}>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 14,
            padding: '28px 24px',
            borderRadius: 16,
            background: `linear-gradient(135deg, ${palette.wash}, var(--surface-card))`,
            border: `1px solid ${palette.primary}33`,
            boxShadow: 'var(--shadow-md)',
          }}>
            <span style={{ fontSize: 12, fontWeight: 800, letterSpacing: '0.08em', color: palette.primary }}>
              PORTFOLIO DEMO
            </span>
            <h2 style={{ margin: 0, fontSize: 'clamp(1.4rem, 3vw, 1.9rem)', textAlign: 'center', lineHeight: 1.35 }}>
              백엔드 없이 바로 체험하는 AI 학습 데모
            </h2>
            <p style={{ margin: 0, maxWidth: 520, textAlign: 'center', color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6 }}>
              파이썬·Rust·운영체제 3과목, 슬라이드·시험·노트·과제가 미리 채워져 있습니다.
              생성 기능만 제외하고 열람·응시·복습을 직접 경험해 보세요.
            </p>
            <button
              onClick={() => void handleDemoEnter()}
              disabled={entering}
              className="landing-primary-action"
              style={{
                background: palette.primary,
                padding: '16px 28px',
                fontSize: 16,
                fontWeight: 800,
                borderRadius: 12,
                opacity: entering ? 0.7 : 1,
                boxShadow: `0 16px 36px -14px ${palette.primary}`,
              }}
            >
              <Icon name="play" size={18} />
              <span>{entering ? '입장 중...' : '데모 체험하기'}</span>
            </button>
          </div>
        </section>
      )}

      <LandingHero palette={palette} setRoute={setRoute} onDemoEnter={isDemoMode ? handleDemoEnter : undefined} demoEntering={entering} />
      <LandingLaunchPanel setRoute={setRoute} />

      <section className="landing-flow-section">
        <div className="landing-section-copy">
          <span>WORKFLOW</span>
          <h2>수업 준비와 진행, 복습이 같은 화면 언어로 움직입니다.</h2>
        </div>
        <div className="landing-flow-grid">
          {FLOW.map((item, index) => (
            <div key={item.title} className="landing-flow-item">
              <span>{String(index + 1).padStart(2, '0')}</span>
              <Icon name={item.icon} size={22} color={palette.primary} />
              <strong>{item.title}</strong>
              <p>{item.body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="landing-closing-panel">
        <div>
          <span>READY</span>
          <h2>{isDemoMode ? '지금 바로 데모를 시작하세요.' : '오늘 할 수업부터 정리하세요.'}</h2>
        </div>
        <button
          onClick={() => (isDemoMode ? void handleDemoEnter() : setRoute('auth'))}
          style={{ background: palette.primary }}
        >
          {isDemoMode ? '데모 체험하기' : '시작하기'}
        </button>
      </section>
    </div>
  );
}

export default Landing;