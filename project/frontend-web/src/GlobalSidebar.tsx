// 전역 내비게이션 사이드바 (학습/도구/관리 3개 그룹)
import { useState, useEffect } from 'react';
// palette는 SidebarContainer가 계산해 prop으로 넘긴다
import type { Palette } from '@/lib';
import { getMyCourses } from '@/apis';
import type { CourseSummaryResponse } from '@/apis';
// 사이드바 코스 목록의 원형 진행률 링
import { ProgressRing } from '@/components/animated';
import Icon from '@/Icon';
import type { AppState, RouteKey, RouteSetter } from '@/shared/types';
import SidebarUserFooter from '@/components/SidebarUserFooter';
import NavBtn from '@/components/sidebar/NavBtn';
// 브랜드 헤더 (로고, 콜랩스 버튼, 토글 버튼)
import SidebarBrandHeader from '@/components/sidebar/SidebarBrandHeader';
// draft 상태 집합은 StudentHome과 같이 쓴다
import { DRAFT_STATUSES } from '@/shared/constants/courseStatus';

interface GlobalSidebarProps {
  route: RouteKey;
  setRoute: RouteSetter;
  appState: AppState;
  /** 컨텍스트 사이드바로 바꾸는 토글 핸들러 (SidebarContainer가 넘김) */
  onToggle: () => void;
  /** 슬라이드 콜랩스. 아이콘 레일만 남기고 메인 콘텐츠를 넓힌다. */
  onCollapse: () => void;
  /** 컨텍스트 사이드바 표시 여부. 토글 버튼 활성 표시에 쓴다. */
  isContext: boolean;
  /** SidebarContainer가 계산한 palette. 여기서 다시 계산하지 않는다. */
  palette: Palette;
}

// 그룹 헤더 레이블 공통 스타일
const GROUP_HEADER_STYLE: React.CSSProperties = {
  fontSize: 10, fontWeight: 800, color: 'var(--text-muted)',
  letterSpacing: '.1em', padding: '10px 10px 6px', textTransform: 'uppercase',
};

function GlobalSidebar({ route, setRoute, appState, onToggle, onCollapse, isContext, palette }: GlobalSidebarProps) {
  const [courses, setCourses] = useState<CourseSummaryResponse[]>([]);
  const [coursesOpen, setCoursesOpen] = useState(true);
  const { setHasCourses } = appState;

  // 라우트가 바뀔 때마다 내 과외 목록을 다시 불러 생성·삭제를 셸에 반영한다.
  // authReady가 false면 부트스트랩 전이라 보호 API를 호출하지 않는다.
  useEffect(() => {
    if (!appState.authReady) return;
    let mounted = true;
    void getMyCourses({ size: 20 }).then((res) => {
      if (!mounted) return;
      if (res.data?.content) {
        setCourses(res.data.content);
        setHasCourses(res.data.content.length > 0);
      } else {
        setHasCourses(false);
      }
    }).catch(() => {
      if (mounted) setHasCourses(false);
    });

    return () => {
      mounted = false;
    };
  }, [route, setHasCourses, appState.authReady]);

  // 과외를 누르면 draft는 커리큘럼 확인 화면, ACTIVE는 학습실로 보낸다.
  const goCourse = (c: { id: string; status: string }) => {
    appState.setActiveCourseId(c.id);
    setRoute(DRAFT_STATUSES.has(c.status) ? 'courseDetail' : 'learning');
  };

  return (
    <aside style={{
      width: 260, flexShrink: 0, height: '100vh', position: 'sticky', top: 0,
      background: 'var(--paper)', borderRight: '1px solid var(--line)',
      display: 'flex', flexDirection: 'column', zIndex: 5,
    }}>
      {/* 브랜드 헤더: 로고, 슬라이드 콜랩스 버튼, 컨텍스트 토글 버튼 */}
      <SidebarBrandHeader isContext={isContext} onToggle={onToggle} onCollapse={onCollapse} palette={palette} />

      {/* Nav: 학습 / 도구 / 관리 */}
      <nav className="scroll" style={{
        flex: 1, padding: '12px 10px', display: 'flex', flexDirection: 'column', gap: 2, overflowY: 'auto',
      }}>
        {/* 과외가 있을 때만 학습·도구 그룹을 보인다. 재조회 전에도 hasCourses로 바로 반영한다. */}
        {(courses.length > 0 || appState.hasCourses === true) ? (
          <>
            {/* 그룹: 학습 */}
            <div style={GROUP_HEADER_STYLE}>학습</div>
            <NavBtn icon="home" label="홈" active={route === 'home'} onClick={() => setRoute('home')} palette={palette} />

            {/* 내 과외 (inline tree) */}
            <div style={{ marginTop: 2 }}>
              <div role="group" style={{
                width: '100%', padding: '8px 10px', borderRadius: 10,
                display: 'flex', alignItems: 'center', gap: 8,
                background: route === 'myCourses' ? 'var(--accent-soft)' : 'transparent',
                borderLeft: route === 'myCourses' ? '2px solid var(--accent-primary)' : '2px solid transparent',
              }}>
                <button
                  onClick={() => setRoute('myCourses')}
                  aria-label="내 과외로 이동"
                  style={{
                    flex: 1, display: 'flex', alignItems: 'center', gap: 12,
                    textAlign: 'left', background: 'transparent',
                    color: route === 'myCourses' ? 'var(--accent-primary)' : 'var(--text-secondary)',
                    fontSize: 13, fontWeight: route === 'myCourses' ? 800 : 600,
                    minWidth: 0,
                  }}
                >
                  <span style={{ width: 20, display: 'inline-flex', justifyContent: 'center' }}>
                    <Icon name="book" size={16} color={route === 'myCourses' ? 'var(--accent-primary)' : 'var(--text-tertiary)'} />
                  </span>
                  <span style={{ flex: 1, fontSize: 12 }}>내 과외</span>
                  <span style={{ fontSize: 10, color: 'var(--text-tertiary)', fontWeight: 700 }}>{courses.length}</span>
                </button>
                <button
                  onClick={() => setCoursesOpen(open => !open)}
                  title={coursesOpen ? '과외 목록 접기' : '과외 목록 펼치기'}
                  aria-label={coursesOpen ? '과외 목록 접기' : '과외 목록 펼치기'}
                  style={{ width: 24, height: 24, borderRadius: 6, display: 'grid', placeItems: 'center', background: 'transparent', flexShrink: 0, transition: 'background .15s' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)' }}
                  onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
                >
                  <span style={{ fontSize: 9, color: 'var(--text-tertiary)', transform: coursesOpen ? 'rotate(180deg)' : 'none', transition: 'transform .15s', display: 'inline-block' }}>▼</span>
                </button>
                <button onClick={() => setRoute('explore')} title="새 과외 만들기" aria-label="새 과외 만들기"
                  style={{ width: 28, height: 28, borderRadius: 7, display: 'grid', placeItems: 'center', background: 'transparent', flexShrink: 0, transition: 'background .15s' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)' }}
                  onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
                >
                  <Icon name="plus" size={12} color="var(--text-tertiary)" />
                </button>
              </div>

              {coursesOpen && (
                <div style={{ paddingLeft: 10, marginTop: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {courses.map(c => {
                    const active = route === 'learning' && appState.activeCourseId === c.id;
                    // course.progress(DB progress_percent)는 백엔드가 갱신하지 않아 항상 0이다.
                    // 그래서 completedLessons / totalLessons로 계산한다.
                    const coursePct = c.totalLessons > 0 ? Math.round((c.completedLessons / c.totalLessons) * 100) : 0;
                    return (
                      <button key={c.id} onClick={() => goCourse(c)} style={{
                        padding: '8px 10px 8px 14px', borderRadius: 8,
                        display: 'flex', alignItems: 'center', gap: 8,
                        textAlign: 'left', position: 'relative',
                        background: active ? 'var(--accent-soft)' : 'transparent',
                        border: '1px solid transparent',
                        borderLeft: active ? '2px solid var(--accent-primary)' : '2px solid transparent',
                      }}>
                        <ProgressRing value={coursePct} size={28} strokeWidth={3}
                          color={active ? palette.primary : 'var(--accent-primary)'} showLabel={false} />
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginBottom: 2 }}>
                            <span style={{ fontSize: 12, fontWeight: 700, color: active ? 'var(--accent-primary)' : 'var(--text-primary)', flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{c.title}</span>
                          </div>
                          <div style={{ fontSize: 11, color: 'var(--text-tertiary)', display: 'flex', gap: 4 }}>
                            <span>{c.completedLessons}/{c.totalLessons}강</span>
                            <span style={{ opacity: .3 }}>·</span>
                            <span>{coursePct}%</span>
                          </div>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            <NavBtn icon="file" label="내 과제" active={route === 'myHomework'} onClick={() => setRoute('myHomework')} palette={palette} />
            <NavBtn icon="calendar" label="학습 캘린더" active={route === 'calendar'} onClick={() => setRoute('calendar')} palette={palette} />

            {/* 그룹: 도구 */}
            <div style={{ ...GROUP_HEADER_STYLE, marginTop: 4 }}>도구</div>
            <NavBtn icon="cards" label="암기노트" active={route === 'notes'} onClick={() => setRoute('notes')} palette={palette} />
            <NavBtn icon="chart" label="학습 리포트" active={route === 'report'} onClick={() => setRoute('report')} palette={palette} />
            <NavBtn icon="sparkle" label="실전 모의고사" active={route === 'aiMockExam'} onClick={() => setRoute('aiMockExam')} palette={palette} />
          </>
        ) : (
          <>
            {/* 과외가 없으면 새 과외 만들기만 강조한다 */}
            <NavBtn icon="plus" label="새 과외 만들기" active={route === 'explore'} onClick={() => setRoute('explore')} palette={palette} />
          </>
        )}

        {/* 그룹: 관리 */}
        <div style={{ ...GROUP_HEADER_STYLE, marginTop: 4 }}>관리</div>
        <NavBtn icon="owl" label="튜터 설정" active={route === 'tutor'} onClick={() => setRoute('tutor')} palette={palette} />
        <NavBtn icon="sparkle" label="구독 · 코인" active={route === 'pricing'} onClick={() => setRoute('pricing')} palette={palette} />
        <NavBtn icon="settings" label="설정" active={route === 'settings'} onClick={() => setRoute('settings')} palette={palette} />
        <div style={{ flex: 1 }} />
      </nav>

      {/* 유저 정보 패널 */}
      <SidebarUserFooter appState={appState} setRoute={setRoute} paletteProps={palette} />
    </aside>
  );
}

export default GlobalSidebar;
