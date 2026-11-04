import { useState, useEffect } from 'react';
import Icon from '@/Icon';
import { getMyCourses } from '@/apis';
import type { CourseSummaryResponse } from '@/apis';
import type { AppState, RouteKey, RouteSetter } from '@/shared/types';
import {
  WORKSPACE_ROUTE_META,
  findWorkspaceZone,
} from '@/components/layout/workspaceShellData';
// draft 상태 집합. GlobalSidebar, StudentHome도 이 값을 쓴다.
import { DRAFT_STATUSES } from '@/shared/constants/courseStatus';

interface WorkspaceContextStripProps {
  route: RouteKey;
  setRoute: RouteSetter;
  appState: AppState;
}


function WorkspaceContextStrip({ route, setRoute, appState }: WorkspaceContextStripProps) {
  const [courses, setCourses] = useState<CourseSummaryResponse[]>([]);

  // authReady 전에는 401이 쌓이지 않게 과외 목록을 부르지 않는다.
  // authReady가 true가 되면 effect가 다시 돌며 목록을 불러온다.
  useEffect(() => {
    if (!appState.authReady) return;
    void getMyCourses({ size: 50 }).then((res) => {
      if (res.data?.content) setCourses(res.data.content);
    }).catch(() => {});
  }, [appState.authReady]);

  const activeZone = findWorkspaceZone(route);
  const activeCourse = courses.find((c) => c.id === appState.activeCourseId) ?? courses[0];
  const currentMeta = WORKSPACE_ROUTE_META[route] ?? { label: '작업', note: '현재 화면', icon: 'diamond' };
  const visibleRoutes = activeZone.routes.filter((item) => WORKSPACE_ROUTE_META[item]);
  const showCourseContext = activeZone.id !== 'system';
  // course.progress(= DB progress_percent)는 백엔드가 갱신하지 않아 항상 0을 반환한다.
  // 그래서 내 과외 카드, GlobalSidebar처럼 completedLessons / totalLessons로 계산해
  // 같은 과외가 화면마다 다른 진행률(0% vs 100%)로 보이지 않게 한다.
  const progressPct = activeCourse && activeCourse.totalLessons > 0
    ? Math.round((activeCourse.completedLessons / activeCourse.totalLessons) * 100)
    : 0;

  if (visibleRoutes.length <= 1) {
    return null;
  }

  return (
    <section className="workspace-context-strip" data-zone={activeZone.id}>
      <div className="workspace-context-head">
        <div className="workspace-route-title">
          <span className="workspace-route-icon">
            <Icon name={currentMeta.icon} size={17} />
          </span>
          <span>
            <strong>{currentMeta.label}</strong>
            <small>{currentMeta.note}</small>
          </span>
        </div>

        {showCourseContext && activeCourse && (
          <button
            className="workspace-course-chip"
            onClick={() => {
              // draft 상태면 커리큘럼 확인 화면으로, ACTIVE면 학습실로 이동한다.
              // 코스 카드, GlobalSidebar와 같은 규칙이다.
              setRoute(DRAFT_STATUSES.has(activeCourse.status) ? 'courseDetail' : 'learning');
            }}
            title={activeCourse.status === 'CURRICULUM_GENERATING'
              ? `${activeCourse.title} 생성 중 · 확인하기`
              : `${activeCourse.title} 수업 이어가기`}
          >
            {/* 생성 중 코스는 "생성 중" 배지를 달아 클릭하면 어디로 가는지 알린다 */}
            <span className="workspace-course-chip__label">
              {activeCourse.status === 'CURRICULUM_GENERATING' ? '생성 중' : '현재 과외'}
            </span>
            <strong>{activeCourse.title}</strong>
            <span>{progressPct}%</span>
            <span className="workspace-course-chip__track">
              <span style={{ width: `${progressPct}%` }} />
            </span>
          </button>
        )}
      </div>

      <div className="workspace-route-row" aria-label={`${activeZone.label} 하위 메뉴`}>
        {visibleRoutes.map((item) => {
          const meta = WORKSPACE_ROUTE_META[item]!;
          return (
            <button
              key={item}
              className="workspace-route-pill"
              data-active={item === route}
              aria-current={item === route ? 'page' : undefined}
              onClick={() => setRoute(item)}
            >
              <Icon name={meta.icon} size={14} />
              <span>{meta.label}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

export { WorkspaceContextStrip };
