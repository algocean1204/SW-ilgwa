// 개발 전용 페이지 이동 도구. import.meta.env.DEV일 때만 마운트한다.
// 번들에서 tree-shake되지는 않지만 프로덕션 DOM에는 나오지 않는다.
import { useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { slideUp } from '@/shared/motion/variants';
import { TRANSITION_SLOW } from '@/shared/motion/transitions';
import type { RouteKey, RouteSetter } from '@/shared/types';
import Icon from '@/Icon';

interface DevRouteJumperProps {
  route: RouteKey;
  setRoute: RouteSetter;
}

// 그룹별 라우트 목록
const ROUTE_GROUPS: { g: string; items: [RouteKey, string][] }[] = [
  {
    g: '시작',
    items: [
      ['landing', '랜딩'],
      ['auth', '로그인'],
      ['onboarding', '온보딩'],
    ],
  },
  {
    g: '학생',
    items: [
      ['home', '홈'],
      ['explore', '새 과외'],
      ['courseDetail', '커리큘럼'],
      ['myCourses', '내 과외'],
      ['learning', '과외중'],
      ['notes', '암기노트'],
      ['calendar', '캘린더'],
      ['myHomework', '내 과제'],
      ['aiMockExam', '실전 모의고사'],
      ['report', '리포트'],
    ],
  },
  {
    g: '설정',
    items: [
      ['tutor', '튜터 설정'],
      ['pricing', '구독·코인'],
      ['settings', '설정'],
      ['apiTest', 'API 테스트'],
    ],
  },
  {
    g: '운영',
    items: [
      ['editor', '에디터'],
      ['admin', '관리자'],
    ],
  },
];

function DevRouteJumper({ route, setRoute }: DevRouteJumperProps) {
  const [open, setOpen] = useState(false);

  return (
    <div className="dev-route-jumper">
      <button
        className="dev-route-toggle"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        title="개발용 화면 이동"
      >
        <Icon name="bolt" size={13} />
        <span>화면 이동</span>
        <span style={{ fontSize: 12, opacity: 0.6 }}>{open ? '▼' : '▲'}</span>
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            variants={slideUp}
            initial="hidden"
            animate="visible"
            exit="exit"
            transition={TRANSITION_SLOW}
            style={{
              position: 'absolute',
              bottom: 48,
              left: 0,
              minWidth: 280,
              padding: 12,
              borderRadius: 14,
              background: 'var(--ink)',
              color: 'var(--paper)',
              boxShadow: '0 20px 60px -20px rgba(0,0,0,.5)',
            }}
          >
            {ROUTE_GROUPS.map((grp) => (
              <div key={grp.g} style={{ marginBottom: 10 }}>
                <div
                  style={{
                    fontSize: 12,
                    fontWeight: 800,
                    color: 'rgba(255,255,255,.45)',
                    letterSpacing: '.15em',
                    marginBottom: 6,
                  }}
                >
                  {grp.g.toUpperCase()}
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                  {grp.items.map(([k, l]) => (
                    <button
                      key={k}
                      className="dev-route-item"
                      data-active={route === k}
                      onClick={() => {
                        setRoute(k);
                        setOpen(false);
                      }}
                    >
                      {l}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export default DevRouteJumper;
