// 사이드바 상단 브랜드 로고와 버튼 영역.
// onToggle(컨텍스트 전환), onCollapse(아이콘 레일 접힘) 두 버튼을 담는다.
import Icon from '@/Icon'
import type { Palette } from '@/lib'
import SidebarToggleButton from './SidebarToggleButton'

interface SidebarBrandHeaderProps {
  isContext: boolean
  onToggle: () => void
  onCollapse: () => void
  palette: Palette
}

// 슬라이드 접기 아이콘 버튼 스타일
const COLLAPSE_BTN: React.CSSProperties = {
  width: 28, height: 28, borderRadius: 7,
  display: 'grid', placeItems: 'center', flexShrink: 0,
  background: 'var(--surface-subtle)', border: '1px solid var(--line)',
  transition: 'background .15s',
}

function SidebarBrandHeader({ isContext, onToggle, onCollapse, palette }: SidebarBrandHeaderProps) {
  return (
    <div style={{
      padding: '14px 14px 12px',
      borderBottom: '1px solid var(--line-2)',
      display: 'flex', alignItems: 'center', gap: 8,
    }}>
      {/* 작업용 앱처럼 보이게 로봇 일러스트 대신 텍스트 마크를 쓴다 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 9, flex: 1, minWidth: 0 }}>
        <div style={{
          width: 30, height: 30, borderRadius: 8,
          background: palette.primary,
          color: 'var(--paper)',
          display: 'grid',
          placeItems: 'center',
          fontSize: 13,
          fontWeight: 850,
          flexShrink: 0,
        }}>
          일
        </div>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: 14, fontWeight: 850, color: 'var(--text-primary)', lineHeight: 1.1 }}>일과</div>
          <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--text-tertiary)', marginTop: 2 }}>study desk</div>
        </div>
      </div>
      {/* 접기 버튼. 아이콘 레일로 접고 메인 콘텐츠 영역을 넓힌다 */}
      <button
        onClick={onCollapse}
        title="사이드바 접기"
        aria-label="사이드바 접기"
        style={COLLAPSE_BTN}
        onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-hover)' }}
        onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)' }}
      >
        <Icon name="chevron-left" size={13} color="var(--text-tertiary)" />
      </button>
      {/* 컨텍스트 사이드바 토글 버튼 */}
      <SidebarToggleButton isContext={isContext} onToggle={onToggle} palette={palette} />
    </div>
  )
}

export default SidebarBrandHeader
