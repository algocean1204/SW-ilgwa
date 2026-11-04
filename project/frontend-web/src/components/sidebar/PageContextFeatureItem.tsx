// 컨텍스트 사이드바의 기능 항목 하나.
// anchor / filter / subview / modal 4가지 항목을 그린다.
// 타입별 버튼이 같이 쓰는 스타일은 상수로 뺐다.
import Icon from '@/Icon'
import type { Palette } from '@/lib'
import type { PageContextFeature } from '@/shared/types/page-context'
import type { RouteSetter } from '@/shared/types'

// 기능 버튼 공통 스타일. 타입별 컴포넌트가 덮어써서 쓴다
const BTN_BASE: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 10,
  padding: '0 12px', borderRadius: 9,
  minHeight: 40, height: 44,
  textAlign: 'left', transition: 'background .15s', width: '100%',
}

// 아이콘 래퍼 공통 스타일
const ICON_WRAP: React.CSSProperties = {
  width: 20, display: 'inline-flex', justifyContent: 'center', flexShrink: 0,
}

// 레이블 공통 스타일
const LABEL_BASE: React.CSSProperties = {
  flex: 1, fontSize: 12, fontWeight: 600, lineHeight: 1.4,
}

// anchor 타입 기능 버튼
// description이 있으면 native title 툴팁으로 보여준다(coin-info 같은 정보성 라벨)
export function AnchorFeatureItem({
  feature, palette, setRoute,
}: {
  feature: PageContextFeature
  palette: Palette
  /** navigateTo 항목의 페이지 이동에 쓴다. anchor 항목에는 필요 없다 */
  setRoute?: RouteSetter
}) {
  const handleClick = () => {
    if (feature.onClick) { feature.onClick(); return }
    if (feature.navigateTo && setRoute) { setRoute(feature.navigateTo); return }
    // scrollIntoView는 스크롤 위치만 옮기는 브라우저 API라 라우터 상태와 충돌하지 않는다
    document.getElementById(feature.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
  return (
    <button onClick={handleClick} title={feature.description ?? feature.label}
      style={{ ...BTN_BASE, background: 'transparent' }}
      onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)' }}
      onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
    >
      <span style={ICON_WRAP}>
        <Icon name={feature.icon} size={14} color={palette.primary} strokeWidth={1.7} />
      </span>
      <span style={{ ...LABEL_BASE, color: 'var(--text-primary)' }}>{feature.label}</span>
    </button>
  )
}

// filter 타입 기능 버튼
interface FilterFeatureItemProps {
  feature: PageContextFeature
  palette: Palette
  isActive: boolean
  onSelect: (filterValue: string) => void
}

export function FilterFeatureItem({ feature, palette, isActive, onSelect }: FilterFeatureItemProps) {
  return (
    <button onClick={() => { if (feature.filterValue !== undefined) onSelect(feature.filterValue) }}
      style={{ ...BTN_BASE, background: isActive ? 'var(--accent-soft)' : 'transparent' }}
      onMouseEnter={e => { if (!isActive) (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)' }}
      onMouseLeave={e => { if (!isActive) (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
    >
      <span style={ICON_WRAP}>
        <Icon name={feature.icon} size={14} color={isActive ? 'var(--accent-primary)' : palette.primary} strokeWidth={1.7} />
      </span>
      <span style={{ ...LABEL_BASE, color: isActive ? 'var(--accent-primary)' : 'var(--text-primary)' }}>
        {feature.label}
      </span>
      {/* count 배지. 과제 개수를 오른쪽 끝에 작게 표시한다 */}
      {feature.count !== undefined && (
        <span style={{
          fontSize: 10, fontWeight: 800, lineHeight: 1,
          padding: '2px 6px', borderRadius: 999, flexShrink: 0,
          background: isActive ? 'var(--accent-soft)' : 'var(--surface-subtle)',
          color: isActive ? 'var(--accent-primary)' : 'var(--text-tertiary)',
        }}>{feature.count}</span>
      )}
    </button>
  )
}

// subview 타입 기능 버튼
// 누르면 해당 섹션만 전체 화면으로 바꾼다. 토글은 PageContextSidebar가 처리한다
interface SubViewFeatureItemProps {
  feature: PageContextFeature
  palette: Palette
  isActive: boolean
  onSelect: (id: string) => void
}

export function SubViewFeatureItem({ feature, palette, isActive, onSelect }: SubViewFeatureItemProps) {
  return (
    <button onClick={() => onSelect(feature.id)}
      style={{ ...BTN_BASE, background: isActive ? 'var(--accent-soft)' : 'transparent' }}
      onMouseEnter={e => { if (!isActive) (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)' }}
      onMouseLeave={e => { if (!isActive) (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
    >
      <span style={ICON_WRAP}>
        <Icon name={feature.icon} size={14} color={isActive ? 'var(--accent-primary)' : palette.primary} strokeWidth={1.7} />
      </span>
      <span style={{ ...LABEL_BASE, color: isActive ? 'var(--accent-primary)' : 'var(--text-primary)' }}>
        {feature.label}
      </span>
      {/* 지금 보고 있는 서브뷰는 오른쪽 끝에 작은 점으로 표시한다 */}
      {isActive && (
        <span style={{ width: 6, height: 6, borderRadius: 3, flexShrink: 0, background: 'var(--accent-primary)' }} />
      )}
    </button>
  )
}

// modal 타입 기능 버튼
// 누르면 AppState.activeModal 키를 바꿔 모달/시트를 열거나 닫는다
interface ModalFeatureItemProps {
  feature: PageContextFeature
  palette: Palette
  isActive: boolean
  onSelect: (modalKey: string) => void
}

export function ModalFeatureItem({ feature, palette, isActive, onSelect }: ModalFeatureItemProps) {
  return (
    <button
      onClick={() => { if (feature.modalKey) onSelect(feature.modalKey) }}
      style={{ ...BTN_BASE, background: isActive ? 'var(--accent-soft)' : 'transparent' }}
      onMouseEnter={e => { if (!isActive) (e.currentTarget as HTMLButtonElement).style.background = 'var(--surface-subtle)' }}
      onMouseLeave={e => { if (!isActive) (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
    >
      <span style={ICON_WRAP}>
        <Icon name={feature.icon} size={14} color={isActive ? 'var(--accent-primary)' : palette.primary} strokeWidth={1.7} />
      </span>
      <span style={{ ...LABEL_BASE, color: isActive ? 'var(--accent-primary)' : 'var(--text-primary)' }}>
        {feature.label}
      </span>
    </button>
  )
}

// type에 따라 anchor / filter / subview / modal 컴포넌트를 고른다
interface FeatureItemProps {
  feature: PageContextFeature
  palette: Palette
  activeFilter: string
  onFilterSelect: (v: string) => void
  /** AnchorFeatureItem의 navigateTo 처리에 사용 */
  setRoute?: RouteSetter
  /** 현재 활성 서브뷰 ID */
  activeSubView: string | null
  /** 서브뷰 선택 콜백. 토글은 PageContextSidebar가 처리한다 */
  onSubViewSelect: (id: string) => void
  /** 현재 열린 모달 키 */
  activeModal: string | null
  /** 모달 선택 콜백. 토글은 PageContextSidebar가 처리한다 */
  onModalSelect: (key: string) => void
}

export function FeatureItem({
  feature, palette, activeFilter, onFilterSelect,
  setRoute, activeSubView, onSubViewSelect, activeModal, onModalSelect,
}: FeatureItemProps) {
  if (feature.type === 'filter') {
    return <FilterFeatureItem feature={feature} palette={palette} isActive={feature.filterValue === activeFilter} onSelect={onFilterSelect} />
  }
  if (feature.type === 'subview') {
    return <SubViewFeatureItem feature={feature} palette={palette} isActive={feature.id === activeSubView} onSelect={onSubViewSelect} />
  }
  if (feature.type === 'modal') {
    return <ModalFeatureItem feature={feature} palette={palette} isActive={feature.modalKey === activeModal} onSelect={onModalSelect} />
  }
  // type이 없거나 'anchor'면 navigateTo 이동을 위해 setRoute를 넘긴다
  return <AnchorFeatureItem feature={feature} palette={palette} setRoute={setRoute} />
}
