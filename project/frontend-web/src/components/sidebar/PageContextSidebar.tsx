// 현재 페이지의 기능 목록을 보여주는 컨텍스트 사이드바. 두 섹션으로 나눈다.
//   - 페이지 기능: filter / subview / modal / navigateTo 항목
//   - 빠른 이동:  anchor(scrollIntoView) 항목. 누르면 해당 섹션으로 부드럽게 스크롤한다
// 하단 고정 푸터: 글로벌 사이드바 복귀 버튼(onExpandGlobal)
import { useCallback } from 'react'
import Icon from '@/Icon'
import type { Palette } from '@/lib'
import type { RouteKey, AppState, RouteSetter } from '@/shared/types'
import { PAGE_CONTEXT_MANIFEST } from '@/shared/types/page-context'
import type { PageContextFeature } from '@/shared/types/page-context'
import { FeatureItem } from './PageContextFeatureItem'
import HomeworkSidebarList from './HomeworkSidebarList'

interface PageContextSidebarProps {
  route: RouteKey
  onBack: () => void
  // 글로벌 사이드바(260px)로 돌아간다. 하단 푸터 버튼이 부른다
  onExpandGlobal: () => void
  palette: Palette
  appState: AppState
  setRoute: RouteSetter
}

// 기능 항목이 빠른 이동(스크롤 앵커)인지 페이지 기능인지 가른다
function isScrollAnchor(f: PageContextFeature): boolean {
  // type이 anchor이고 navigateTo가 없는 경우만 스크롤 앵커로 분류
  return (!f.type || f.type === 'anchor') && !f.navigateTo && !f.onClick
}

// 섹션 헤더 공통 스타일
const SECTION_HEADER: React.CSSProperties = {
  fontSize: 10, fontWeight: 700, color: 'var(--text-tertiary)',
  letterSpacing: '.12em', padding: '6px 12px 4px',
  textTransform: 'uppercase',
}

function PageContextSidebar({ route, onBack, onExpandGlobal, palette, appState, setRoute }: PageContextSidebarProps) {
  const ctx = PAGE_CONTEXT_MANIFEST[route]
  const activeFilter = appState.activeHomeworkFilter
  const activeSubView = appState.pageSubView
  const activeModal = appState.activeModal ?? null

  const handleFilterSelect = useCallback((v: string) => { appState.setActiveHomeworkFilter(v) }, [appState])
  const handleSubViewSelect = useCallback((id: string) => {
    appState.setPageSubView(appState.pageSubView === id ? null : id)
  }, [appState])
  const handleModalSelect = useCallback((key: string) => {
    appState.setActiveModal(appState.activeModal === key ? null : key)
  }, [appState])

  // 페이지 기능과 빠른 이동(스크롤 앵커)으로 나눈다
  const features = ctx.features.slice(0, 5)
  const pageFeatures = features.filter(f => !isScrollAnchor(f))
  const scrollAnchors = features.filter(isScrollAnchor)

  return (
    <aside style={{
      width: 260, flexShrink: 0, height: '100vh',
      position: 'sticky', top: 0,
      background: 'var(--surface-card)', borderRight: '1px solid var(--border-default)',
      boxShadow: '12px 0 28px rgba(41, 36, 20, 0.08)',
      display: 'flex', flexDirection: 'column', zIndex: 90,
    }}>
      {/* 헤더: 뒤로가기 버튼과 페이지 제목 */}
      <div style={{
        height: 64, padding: '0 16px',
        borderBottom: '1px solid var(--border-soft)',
        display: 'flex', alignItems: 'center', gap: 8,
        flexShrink: 0,
      }}>
        <button onClick={onBack} title="기능 패널 닫기" aria-label="기능 패널 닫기"
          style={{
            width: 28, height: 28, borderRadius: 7,
            background: 'var(--surface-subtle)', display: 'grid',
            placeItems: 'center', flexShrink: 0, transition: 'background .15s',
          }}
        >
          <Icon name="chevron-left" size={14} color="var(--text-tertiary)" />
        </button>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 9, fontWeight: 700, color: 'var(--text-tertiary)', letterSpacing: '.1em', textTransform: 'uppercase' }}>
            이 페이지에서
          </div>
          <div style={{ fontSize: 13, fontWeight: 800, color: 'var(--text-primary)', marginTop: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {ctx.title}
          </div>
        </div>
      </div>

      {/* 기능 목록 */}
      <div className="scroll" style={{ flex: 1, padding: '10px 6px', display: 'flex', flexDirection: 'column', gap: 1, overflowY: 'auto' }}>
        {ctx.features.length > 0 ? (
          <>
            {/* 섹션 1: 페이지 기능 (filter / subview / modal / navigateTo) */}
            {pageFeatures.length > 0 && (
              <>
                <div style={SECTION_HEADER}>페이지 기능</div>
                {pageFeatures.map(f => (
                  <FeatureItem key={f.id} feature={f} palette={palette}
                    activeFilter={activeFilter} onFilterSelect={handleFilterSelect}
                    setRoute={setRoute} activeSubView={activeSubView}
                    onSubViewSelect={handleSubViewSelect}
                    activeModal={activeModal} onModalSelect={handleModalSelect} />
                ))}
              </>
            )}

            {/* 섹션 구분선. 두 섹션이 다 있을 때만 보인다 */}
            {pageFeatures.length > 0 && scrollAnchors.length > 0 && (
              <div style={{ height: 1, background: 'var(--border-soft)', margin: '8px 12px' }} />
            )}

            {/* 섹션 2: 빠른 이동 (스크롤 앵커) */}
            {scrollAnchors.length > 0 && (
              <>
                <div style={SECTION_HEADER}>빠른 이동</div>
                {scrollAnchors.map(f => (
                  // 스크롤 앵커는 작은 화살표 아이콘과 더 작은 글자로 구분한다
                  <button
                    key={f.id}
                    // scrollIntoView는 가로로도 움직여 잘리므로 window.scrollTo로 세로만 옮긴다
                    onClick={() => {
                      const el = document.getElementById(f.id)
                      if (el) window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY, behavior: 'smooth' })
                    }}
                    title={f.description ?? f.label}
                    style={{
                      display: 'flex', alignItems: 'center', gap: 8,
                      padding: '0 12px', borderRadius: 9,
                      minHeight: 36, textAlign: 'left',
                      transition: 'background .15s', width: '100%',
                      background: 'transparent',
                    }}
                    onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)' }}
                    onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
                  >
                    {/* 스크롤 방향을 나타내는 아이콘 */}
                    <Icon name="arrowDown" size={11} color="var(--text-tertiary)" strokeWidth={1.5} />
                    <span style={{ flex: 1, fontSize: 11, fontWeight: 600, color: 'var(--text-secondary)', lineHeight: 1.4 }}>
                      {f.label}
                    </span>
                  </button>
                ))}
              </>
            )}

            {/* myHomework 페이지에서만 필터 아래에 과제 카드 목록을 그린다 */}
            {route === 'myHomework' && (
              <>
                <div style={{ height: 1, background: 'var(--border-soft)', margin: '8px 12px' }} />
                <HomeworkSidebarList palette={palette} appState={appState} />
              </>
            )}
          </>
        ) : (
          // 기능이 없는 페이지는 빈 상태 안내를 띄운다
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '32px 20px', textAlign: 'center' }}>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', lineHeight: 1.7 }}>
              이 페이지는 핵심 진입 흐름이므로 보조 기능을 노출하지 않는다
            </div>
          </div>
        )}
      </div>

      {/* 하단 고정 푸터: 글로벌 사이드바(전체 메뉴) 복귀 버튼 */}
      <button
        onClick={onExpandGlobal}
        title="전체 메뉴로 돌아가기"
        aria-label="전체 메뉴로 돌아가기"
        style={{
          display: 'flex', alignItems: 'center', gap: 6,
          padding: '10px 16px', width: '100%', flexShrink: 0,
          borderTop: '1px solid var(--border-soft)',
          background: 'transparent',
          fontSize: 11, fontWeight: 600, color: 'var(--text-tertiary)',
          transition: 'background .15s',
        }}
        onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)' }}
        onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
      >
        {/* 전체 메뉴 진입을 나타내는 햄버거 아이콘 */}
        <Icon name="menu" size={13} color="var(--text-tertiary)" />
        전체 메뉴
      </button>
    </aside>
  )
}

export default PageContextSidebar
