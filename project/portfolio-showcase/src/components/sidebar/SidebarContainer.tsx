// 사이드바 상태와 translateX 슬라이드 애니메이션 래퍼.
//
// 3가지 시각 상태:
//   1. !sidebarCollapsed              : 260px 전체 글로벌 사이드바
//   2. sidebarCollapsed && !showContext : 56px 아이콘 레일만
//   3. sidebarCollapsed && showContext  : 56px 아이콘 레일 + 컨텍스트 패널 슬라이드 인
import { AnimatePresence, motion } from 'motion/react'
import { useTweaks, PALETTES } from '@/lib'
import GlobalSidebar from '@/GlobalSidebar'
import PageContextSidebar from './PageContextSidebar'
import SidebarIconRail from './SidebarIconRail'
import type { AppState, RouteKey, RouteSetter } from '@/shared/types'

interface SidebarContainerProps {
  route: RouteKey
  setRoute: RouteSetter
  appState: AppState
  /** 글로벌 사이드바가 접힌 상태인지 여부 */
  sidebarCollapsed: boolean
  setSidebarCollapsed: (v: boolean) => void
  /** 컨텍스트 패널 표시 여부. sidebarCollapsed=true일 때만 쓴다 */
  showContext: boolean
  setShowContext: (v: boolean) => void
}

// 슬라이드 이징. Material Design 표준 곡선
const EASE_MATERIAL: [number, number, number, number] = [0.4, 0, 0.2, 1]
// 슬라이드 지속 시간 (초)
const SLIDE_DURATION = 0.15

// 컨텍스트 패널 슬라이드 인/아웃 variants
const CONTEXT_VARIANTS = {
  hidden: { x: '100%', opacity: 0 },
  visible: { x: 0, opacity: 1 },
  exit:   { x: '100%', opacity: 0 },
}

// 글로벌 사이드바 슬라이드 인/아웃 variants
const GLOBAL_VARIANTS = {
  hidden:  { x: '-100%', opacity: 0 },
  visible: { x: 0, opacity: 1 },
  exit:    { x: '-100%', opacity: 0 },
}

function SidebarContainer({
  route, setRoute, appState,
  sidebarCollapsed, setSidebarCollapsed,
  showContext, setShowContext,
}: SidebarContainerProps) {
  const t = useTweaks()
  const palette = PALETTES[t.palette] ?? PALETTES.grass!

  // 컨텍스트 사이드바로 전환: 접고 컨텍스트 패널을 연다
  const handleToggleToContext = () => {
    setSidebarCollapsed(true)
    setShowContext(true)
  }

  // 접기: 접고 컨텍스트 패널은 닫는다
  const handleSlideCollapse = () => {
    setSidebarCollapsed(true)
    setShowContext(false)
  }

  // 전체 글로벌 사이드바로 복귀
  const handleExpand = () => {
    setSidebarCollapsed(false)
    setShowContext(false)
  }

  // 컨텍스트 패널만 닫고 아이콘 레일은 둔다
  const handleContextBack = () => {
    setShowContext(false)
  }

  return (
    // 컨텍스트 패널은 main 위에 얹히는 overlay drawer다.
    // showContext일 때 overflow를 열어 56px 아이콘 레일 밖 260px 패널이 보이게 한다.
    <div style={{
      height: '100vh', position: 'sticky', top: 0, zIndex: showContext ? 80 : 5,
      overflow: showContext ? 'visible' : 'hidden', display: 'flex',
    }}>
      <AnimatePresence mode="wait" initial={false}>
        {!sidebarCollapsed ? (
          // 상태 1: 전체 글로벌 사이드바 260px
          <motion.div
            key="global"
            variants={GLOBAL_VARIANTS}
            initial="hidden"
            animate="visible"
            exit="exit"
            transition={{ duration: SLIDE_DURATION, ease: EASE_MATERIAL }}
            style={{ height: '100vh', width: 260, flexShrink: 0 }}
          >
            <GlobalSidebar
              route={route}
              setRoute={setRoute}
              appState={appState}
              onToggle={handleToggleToContext}
              onCollapse={handleSlideCollapse}
              isContext={false}
              palette={palette}
            />
          </motion.div>
        ) : (
          // 상태 2, 3: 아이콘 레일과 선택적 컨텍스트 패널
          <motion.div
            key="icon-rail-area"
            initial="hidden"
            animate="visible"
            exit="exit"
            variants={GLOBAL_VARIANTS}
            transition={{ duration: SLIDE_DURATION, ease: EASE_MATERIAL }}
            style={{ height: '100vh', display: 'flex', flexShrink: 0 }}
          >
            {/* 아이콘 레일은 항상 보인다 */}
            <SidebarIconRail
              route={route}
	              setRoute={setRoute}
	              palette={palette}
	              onExpand={handleExpand}
	              onShowContext={() => setShowContext(true)}
	              showContext={showContext}
	            />

            {/* 컨텍스트 패널은 showContext일 때만 슬라이드 인한다 */}
            <AnimatePresence>
              {showContext && (
                <motion.div
                  key="context"
                  variants={CONTEXT_VARIANTS}
                  initial="hidden"
                  animate="visible"
                  exit="exit"
                  transition={{ duration: SLIDE_DURATION, ease: EASE_MATERIAL }}
                  style={{
                    height: '100vh',
                    width: 260,
                    flexShrink: 0,
                    position: 'relative',
                    zIndex: 81,
                  }}
                >
                  <PageContextSidebar
                    route={route}
                    onBack={handleContextBack}
                    onExpandGlobal={handleExpand}
                    palette={palette}
                    appState={appState}
                    setRoute={setRoute}
                  />
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

export default SidebarContainer
