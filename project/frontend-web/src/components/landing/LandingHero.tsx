import { ProductPreview } from '@/components/landing/ProductPreview';
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';

interface LandingHeroProps {
  palette: Palette;
  setRoute: RouteSetter;
}

function LandingHero({ palette, setRoute }: LandingHeroProps) {
  const signals = [
    ['PDF', '목차와 챕터 구성'],
    ['VOICE', '음성 수업 진행'],
    ['REVIEW', '복습과 과제 연결'],
  ];

  return (
    <section className="landing-poster">
      <div className="landing-poster-nav">
        <button onClick={() => setRoute('home')} title="앱 작업 화면 둘러보기">둘러보기</button>
        <button onClick={() => setRoute('auth')} title="로그인 화면으로 이동">로그인</button>
      </div>

      <div className="landing-poster-copy">
        <span className="landing-kicker">개인 학습 운영 데스크</span>
        <h1>일과</h1>
        <p>교재, 수업, 과제, 복습 기록을 흩어두지 않고 오늘의 학습 흐름으로 이어갑니다.</p>
        <div className="landing-poster-actions">
          <button className="landing-primary-action" onClick={() => setRoute('auth')} style={{ background: palette.primary }} title="학습 시작하기">
            <Icon name="play" size={15} />
            <span>학습 시작</span>
          </button>
          <button className="landing-secondary-action" onClick={() => setRoute('home')} title="실제 작업 화면 보기">
            <Icon name="eye" size={15} />
            <span>작업 화면 보기</span>
          </button>
        </div>
      </div>

      <div className="landing-signal-rail">
        {signals.map(([code, label]) => (
          <div key={code}>
            <strong>{code}</strong>
            <span>{label}</span>
          </div>
        ))}
      </div>

      <div className="landing-product-wall">
        <ProductPreview primaryColor={palette.primary} washColor={palette.wash} />
      </div>
    </section>
  );
}

export { LandingHero };
