import { useTweaks, PALETTES } from '@/lib';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import Icon from '@/Icon';
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

  return (
    <div className="landing-redesign">
      <LandingHero palette={palette} setRoute={setRoute} />
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
          <h2>오늘 할 수업부터 정리하세요.</h2>
        </div>
        <button onClick={() => setRoute('auth')} style={{ background: palette.primary }}>
          시작하기
        </button>
      </section>
    </div>
  );
}

export default Landing;
