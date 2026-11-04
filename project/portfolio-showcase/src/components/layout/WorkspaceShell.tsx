// 워크스페이스 레이아웃 셸. 사이드바, 탑바, 메인 콘텐츠 영역을 구성한다.
import type { ReactNode } from 'react';
import type { AppState, RouteKey, RouteSetter } from '@/shared/types';
import { WorkspaceTopBar } from '@/components/layout/WorkspaceTopBar';
import { WorkspaceContextStrip } from '@/components/layout/WorkspaceContextStrip';
import ErrorBoundary from '@/components/ErrorBoundary';

interface WorkspaceShellProps {
  route: RouteKey;
  setRoute: RouteSetter;
  appState: AppState;
  children: ReactNode;
}

function WorkspaceShell({ route, setRoute, appState, children }: WorkspaceShellProps) {
  return (
    <div className="workspace-shell">
      <WorkspaceTopBar route={route} setRoute={setRoute} appState={appState} />
      <WorkspaceContextStrip route={route} setRoute={setRoute} appState={appState} />
      {/* 메인 콘텐츠가 크래시해도 탑바와 네비는 남도록 ErrorBoundary로 감싼다 */}
      <main className="workspace-main">
        <ErrorBoundary scope="workspace">
          {children}
        </ErrorBoundary>
      </main>
    </div>
  );
}

export default WorkspaceShell;
