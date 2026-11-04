import { ProductPreview } from '@/components/landing/ProductPreview';
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import { isDemoMode } from '@/lib/demoMode';
import type { RouteSetter } from '@/shared/types';

interface LandingHeroProps {
  palette: Palette;
  setRoute: RouteSetter;
  onDemoEnter?: () => void | Promise<void>;
  demoEntering?: boolean;
}

function LandingHero({ palette, setRoute, onDemoEnter, demoEntering }: LandingHeroProps) {
  const signals = [
    ['PDF', '목차와 챕터 구성'],
    ['VOICE', '음성 수업 진행'],
    ['REVIEW', '복습과 과제 연결'],
  ];

  return (
    <section className="landing-poster">
      <div className="landing-poster-nav">
        {!isDemoMode && (
          <>
            <button onClick={() => setRoute('home')} title="앱 작업 화면 둘러보기">둘러보기</button>
            <button onClick={() => setRoute('auth')} title="로그인 화면으로 이동">로그인</button>
          </>
        )}
        {isDemoMode && (
          <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-tertiary)' }}>
            정적 포트폴리오 데모 · 로그인 불필요
          </span>
        )}
      </div>

      <div className="landing-poster-copy">
        <span className="landing-kicker">{isDemoMode ? '포트폴리오 데모' : '개인 학습 운영 데스크'}</span>
        <h1>일과</h1>
        <p>
          {isDemoMode
            ? '실제 제품 UI로 학습 흐름을 체험하세요. 3과목 커리큘럼·시험·노트가 준비되어 있습니다.'
            : '교재, 수업, 과제, 복습 기록을 흩어두지 않고 오늘의 학습 흐름으로 이어갑니다.'}
        </p>
        <div className="landing-poster-actions">
          {isDemoMode && onDemoEnter ? (
            <button
              className="landing-primary-action"
              onClick={() => void onDemoEnter()}
              disabled={demoEntering}
              style={{ background: palette.primary, opacity: demoEntering ? 0.7 : 1 }}
              title="데모 체험하기"
            >
              <Icon name="play" size={15} />
              <span>{demoEntering ? '입장 중...' : '데모 체험하기'}</span>
            </button>
          ) : (
            <button className="landing-primary-action" onClick={() => setRoute('auth')} style={{ background: palette.primary }} title="학습 시작하기">
              <Icon name="play" size={15} />
              <span>학습 시작</span>
            </button>
          )}
          <button className="landing-secondary-action" onClick={() => setRoute(isDemoMode ? 'myCourses' : 'home')} title="작업 화면 보기">
            <Icon name="eye" size={15} />
            <span>{isDemoMode ? '강의 목록 보기' : '작업 화면 보기'}</span>
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