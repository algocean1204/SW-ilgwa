// 생성권 표시용 이미지 컴포넌트.
// 이미지는 기존 자산을 쓰고 UI 문구만 학습 맥락에 맞춘다.
import aiCoin from '@/assets/ai-coin.png';

interface AICoinIconProps {
  // 너비·높이(px, 기본값 16)
  size?: number;
  style?: React.CSSProperties;
  className?: string;
  // 주변 문구가 이미 생성권을 설명하는 경우 스크린리더 중복 읽기를 막는다.
  decorative?: boolean;
}

function AICoinIcon({ size = 16, style, className, decorative = false }: AICoinIconProps) {
  return (
    <img
      src={aiCoin}
      alt={decorative ? "" : "생성권"}
      aria-hidden={decorative ? true : undefined}
      width={size}
      height={size}
      className={className}
      style={{
        display: 'inline-block',
        verticalAlign: '-2px',
        objectFit: 'contain',
        ...style,
      }}
    />
  );
}

export default AICoinIcon;
