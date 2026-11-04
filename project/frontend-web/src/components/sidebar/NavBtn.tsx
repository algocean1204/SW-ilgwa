// 글로벌 사이드바의 개별 내비게이션 버튼.
// GlobalSidebar 밖(테스트 등)에서도 써서 따로 뒀다.
import Icon from '@/Icon'

export interface NavBtnProps {
  icon: string
  label: string
  active: boolean
  onClick: () => void
  palette: { primary: string; primary2: string; wash: string; soft: string }
  badge?: number | null
  tag?: string
}

function NavBtn({ icon, label, active, onClick, palette, badge, tag }: NavBtnProps) {
  return (
    <button
      onClick={onClick}
      // 스크린리더용 aria-label. 화면의 label 텍스트와 같게 둔다
      aria-label={label}
      aria-current={active ? 'page' : undefined}
      style={{
        display: 'flex', alignItems: 'center', gap: 12,
        padding: '10px 12px', borderRadius: 10, textAlign: 'left',
        background: active ? palette.wash : 'transparent',
        color: active ? palette.primary : 'var(--ink-2)',
        fontSize: 13, fontWeight: active ? 800 : 600,
        transition: 'all .15s', position: 'relative',
      }}
    >
      {/* 활성 상태 좌측 강조 바 */}
      {active && (
        <div style={{
          position: 'absolute', left: 0, top: '50%', transform: 'translateY(-50%)',
          width: 3, height: 18, borderRadius: '0 3px 3px 0', background: palette.primary,
        }} />
      )}
      <span style={{ width: 20, display: 'inline-flex', justifyContent: 'center' }}>
        <Icon
          name={icon}
          size={17}
          color={active ? palette.primary : 'var(--ink-3)'}
          strokeWidth={active ? 1.9 : 1.6}
        />
      </span>
      <span style={{ flex: 1 }}>{label}</span>
      {/* 숫자 배지(과제 미제출 건수 등) */}
      {badge != null && (
        <span style={{
          padding: '1px 7px', borderRadius: 10, background: 'var(--state-danger)', color: 'var(--surface-strong)',
          fontSize: 12, fontWeight: 800, minWidth: 18, textAlign: 'center',
        }}>{badge}</span>
      )}
      {/* "코인" 같은 보조 태그 */}
      {tag && (
        <span style={{
          padding: '1px 6px', borderRadius: 4, background: 'var(--honey-tint)', color: 'var(--honey-ink)',
          fontSize: 12, fontWeight: 800, letterSpacing: '.04em',
        }}>{tag}</span>
      )}
    </button>
  )
}

export default NavBtn
