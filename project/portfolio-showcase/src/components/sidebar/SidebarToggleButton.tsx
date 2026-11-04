// 로고 오른쪽에서 글로벌/컨텍스트 사이드바를 전환하는 버튼.
// 상태(isContext)는 SidebarContainer가 갖고, 여기서는 표시와 클릭만 처리한다.
import Icon from '@/Icon'

interface SidebarToggleButtonProps {
  /** true = 현재 컨텍스트 사이드바 표시 중, false = 글로벌 사이드바 표시 중 */
  isContext: boolean
  onToggle: () => void
  palette: { primary: string; wash: string }
}

function SidebarToggleButton({ isContext, onToggle, palette: _palette }: SidebarToggleButtonProps) {
  return (
    <button
      onClick={onToggle}
      title={isContext ? '메뉴로 돌아가기' : '현재 페이지 기능 보기'}
      aria-label={isContext ? '메뉴로 돌아가기' : '현재 페이지 기능 보기'}
      style={{
        width: 34,
        height: 34,
        borderRadius: 9,
        display: 'grid',
        placeItems: 'center',
        flexShrink: 0,
        // 컨텍스트 모드: accent-soft 배경 + accent-primary 아이콘, 아닐 때는 subtle 중립 배경
        background: isContext ? 'var(--accent-soft)' : 'var(--surface-subtle)',
        border: isContext ? '1px solid var(--accent-soft)' : '1px solid var(--line)',
        transition: 'background .2s, border-color .2s',
      }}
    >
      <Icon
        name={isContext ? 'chevron-left' : 'menu'}
        size={16}
        color={isContext ? 'var(--accent-primary)' : 'var(--text-tertiary)'}
      />
    </button>
  )
}

export default SidebarToggleButton
