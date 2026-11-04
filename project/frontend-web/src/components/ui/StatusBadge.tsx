// 상태 배지 공용 컴포넌트. 코스, 과제, 결제, 텔레그램 상태를 모두 이 배지로 그리고 이모지는 쓰지 않는다.
interface StatusBadgeProps {
  tone: 'success' | 'warning' | 'danger' | 'neutral' | 'info';
  label: string;
  size?: 'sm' | 'md';
}

// tone별 스타일
function getToneStyles(tone: StatusBadgeProps['tone']): { bg: string; color: string; border: string } {
  switch (tone) {
    case 'success':
      return { bg: 'var(--state-success-bg)', color: 'var(--accent-primary)', border: 'var(--accent-soft)' };
    case 'warning':
      return { bg: 'var(--state-warning-bg)', color: 'var(--honey-ink)', border: 'var(--honey-wash)' };
    case 'danger':
      return { bg: 'var(--state-danger-bg)', color: 'var(--state-danger)', border: 'var(--blush-wash)' };
    case 'info':
      return { bg: 'var(--retrieve-bg)', color: 'var(--retrieve-ink)', border: 'var(--retrieve-border)' };
    default:
      return { bg: 'var(--surface-subtle)', color: 'var(--text-secondary)', border: 'var(--border-soft)' };
  }
}

export default function StatusBadge({ tone, label, size = 'md' }: StatusBadgeProps) {
  const styles = getToneStyles(tone);
  const isSmall = size === 'sm';
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      padding: isSmall ? '2px 6px' : '3px 8px',
      borderRadius: isSmall ? 4 : 6,
      fontSize: isSmall ? 10 : 11,
      fontWeight: 700,
      letterSpacing: '0.04em',
      lineHeight: 1.3,
      background: styles.bg,
      color: styles.color,
      border: `1px solid ${styles.border}`,
    }}>
      {label}
    </span>
  );
}
