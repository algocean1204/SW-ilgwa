// 사이드바가 접혔을 때 보이는 56px 아이콘 내비게이션 레일.
// 아이콘에 호버하면 툴팁이 뜨고, 누르면 라우트를 옮긴다.
import Icon from '@/Icon'
import type { Palette } from '@/lib'
import type { RouteKey, RouteSetter } from '@/shared/types'

interface SidebarIconRailProps {
  route: RouteKey
  setRoute: RouteSetter
  palette: Palette
  /** 전체 사이드바로 돌아간다. 브랜드 아이콘을 누르면 부른다 */
  onExpand: () => void
  /** 접힘 상태에서 현재 페이지 기능 패널 열기 */
  onShowContext: () => void
  showContext: boolean
}

// 레일 내비게이션 항목: 아이콘, 라우트, 툴팁 레이블
const NAV_ITEMS: Array<{
  icon: string
  label: string
  route: RouteKey
  group: '학습' | '도구' | '관리'
}> = [
  // 학습 그룹
  { icon: 'home',     label: '홈',         route: 'home',       group: '학습' },
  { icon: 'book',     label: '내 과외',    route: 'myCourses',  group: '학습' },
  { icon: 'file',     label: '내 과제',    route: 'myHomework', group: '학습' },
  { icon: 'calendar', label: '학습 캘린더', route: 'calendar',  group: '학습' },
  // 도구 그룹
  { icon: 'cards',   label: '암기노트',     route: 'notes',      group: '도구' },
  { icon: 'chart',   label: '학습 리포트',  route: 'report',     group: '도구' },
  // 실전 모의고사: AI 맞춤 문제 세트 생성
  { icon: 'sparkle', label: '실전 모의고사', route: 'aiMockExam', group: '도구' },
  // 관리 그룹
  { icon: 'owl',     label: '튜터 설정',  route: 'tutor',      group: '관리' },
  { icon: 'sparkle', label: '구독 · 코인', route: 'pricing',   group: '관리' },
  { icon: 'settings', label: '설정',       route: 'settings',   group: '관리' },
]

// 그룹 사이에 그리는 구분선
function GroupDivider() {
  return (
    <div style={{
      width: 28, height: 1,
      background: 'var(--line)',
      margin: '4px auto',
      flexShrink: 0,
    }} />
  )
}

function SidebarIconRail({ route, setRoute, palette, onExpand, onShowContext, showContext }: SidebarIconRailProps) {
  return (
    <aside style={{
      width: 56, flexShrink: 0, height: '100vh',
      background: 'var(--paper)', borderRight: '1px solid var(--line)',
      display: 'flex', flexDirection: 'column', alignItems: 'center',
      zIndex: 5, overflow: 'hidden',
    }}>
      {/* 브랜드 아이콘 버튼. 누르면 전체 사이드바로 돌아간다 */}
      <button
        onClick={onExpand}
        title="일과"
        aria-label="사이드바 펼치기"
        style={{
          width: 40, height: 40,
          borderRadius: 10, margin: '12px 0 8px',
          display: 'grid', placeItems: 'center',
          background: 'transparent',
          border: 'none',
          flexShrink: 0, transition: 'background .15s',
          cursor: 'pointer',
        }}
        onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = palette.wash }}
        onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
      >
        {/* 앱 브랜드 번개 아이콘. favicon.svg와 같은 SVG를 인라인으로 그린다 */}
        <svg width={20} height={19} viewBox="0 0 48 46" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M25.946 44.938c-.664.845-2.021.375-2.021-.698V33.937a2.26 2.26 0 0 0-2.262-2.262H10.287c-.92 0-1.456-1.04-.92-1.788l7.48-10.471c1.07-1.497 0-3.578-1.842-3.578H1.237c-.92 0-1.456-1.04-.92-1.788L10.013.474c.214-.297.556-.474.92-.474h28.894c.92 0 1.456 1.04.92 1.788l-7.48 10.471c-1.07 1.498 0 3.579 1.842 3.579h11.377c.943 0 1.473 1.088.89 1.83L25.947 44.94z"
            fill={palette.primary}
          />
        </svg>
      </button>

      {/* 컨텍스트 패널 열기. 접힌 상태에서도 현재 페이지 기능으로 바로 가게 한다 */}
      <button
        onClick={onShowContext}
        title="현재 페이지 기능 보기"
        aria-label="현재 페이지 기능 보기"
        style={{
          width: 40, height: 36,
          borderRadius: 10, marginBottom: 8,
          display: 'grid', placeItems: 'center',
          background: showContext ? palette.wash : 'transparent',
          border: showContext ? `1px solid ${palette.soft}` : '1px solid transparent',
          flexShrink: 0, transition: 'background .15s, border-color .15s',
          cursor: 'pointer',
        }}
        onMouseEnter={e => {
          if (!showContext) (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)'
        }}
        onMouseLeave={e => {
          if (!showContext) (e.currentTarget as HTMLButtonElement).style.background = 'transparent'
        }}
      >
        <Icon name="menu" size={16} color={showContext ? palette.primary : 'var(--ink-3)'} strokeWidth={1.7} />
      </button>

      {/* 구분선 */}
      <div style={{ width: '100%', height: 1, background: 'var(--line-2)', flexShrink: 0 }} />

      {/* 내비게이션 아이콘 목록 */}
      <nav style={{
        flex: 1, paddingTop: 8,
        display: 'flex', flexDirection: 'column', alignItems: 'center',
        gap: 2, overflowY: 'auto', width: '100%',
      }}>
        {NAV_ITEMS.map((item, idx) => {
          const isActive = route === item.route
          // 이전 항목과 그룹이 다르면 구분선을 넣는다
          const prevGroup = idx > 0 ? NAV_ITEMS[idx - 1]?.group : item.group
          const showDivider = idx > 0 && prevGroup !== item.group

          return (
            <div key={`${item.route}-${item.label}`} style={{ width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              {showDivider && <GroupDivider />}
              <button
	                onClick={() => setRoute(item.route)}
	                title={item.label}
	                aria-label={item.label}
	                style={{
                  width: 40, height: 40, borderRadius: 10,
                  display: 'grid', placeItems: 'center',
                  background: isActive ? palette.wash : 'transparent',
                  border: isActive ? `1px solid ${palette.soft}` : '1px solid transparent',
                  transition: 'background .15s, border-color .15s',
                  position: 'relative', cursor: 'pointer',
                }}
                onMouseEnter={e => {
                  if (!isActive) (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)'
                }}
                onMouseLeave={e => {
                  if (!isActive) (e.currentTarget as HTMLButtonElement).style.background = 'transparent'
                }}
              >
                {/* 활성 상태 좌측 강조 바 */}
                {isActive && (
                  <div style={{
                    position: 'absolute', left: 0, top: '50%', transform: 'translateY(-50%)',
                    width: 3, height: 18, borderRadius: '0 3px 3px 0',
                    background: palette.primary,
                  }} />
                )}
                <Icon
                  name={item.icon}
                  size={17}
                  color={isActive ? palette.primary : 'var(--ink-3)'}
                  strokeWidth={isActive ? 1.9 : 1.6}
                />
              </button>
            </div>
          )
        })}
      </nav>
    </aside>
  )
}

export default SidebarIconRail
