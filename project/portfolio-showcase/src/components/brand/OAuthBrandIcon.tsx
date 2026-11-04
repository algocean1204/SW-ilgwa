// OAuth 브랜드 로고. 이모지 대신 각 사의 공식 마크를 그린다.
// 네이버: 녹색 원 + 흰 N 워드마크
// Google: 공식 4색 G 마크 (파랑/빨강/노랑/초록)
// 카카오: 노란 원 + 검정 K

interface OAuthBrandIconProps {
  provider: 'naver' | 'google' | 'kakao';
  size?: number;
}

// 네이버 공식 녹색 (#03C75A) + 흰 N 마크
function NaverIcon({ size }: { size: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none">
      <rect width="24" height="24" rx="5" fill="#03C75A" />
      <path
        d="M13.2 12.4L10.6 8H8v8h2.8v-4.4L13.4 16H16V8h-2.8v4.4z"
        fill="#fff"
      />
    </svg>
  );
}

// Google 공식 G 마크. 파랑(#4285F4)/빨강(#EA4335)/노랑(#FBBC05)/초록(#34A853)
function GoogleIcon({ size }: { size: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none">
      <rect width="24" height="24" rx="5" fill="#fff" stroke="#E8E8E8" strokeWidth="1" />
      {/* G 모양: 오른쪽 수평 막대 + 원호 세그먼트 */}
      <path
        d="M21.8 12.2c0-.7-.06-1.37-.18-2H12v3.79h5.5a4.7 4.7 0 0 1-2.04 3.08v2.56h3.3c1.93-1.78 3.04-4.4 3.04-7.43z"
        fill="#4285F4"
      />
      <path
        d="M12 22c2.76 0 5.08-.91 6.77-2.47l-3.3-2.56c-.91.61-2.08.97-3.47.97-2.67 0-4.93-1.8-5.74-4.22H2.85v2.64A10 10 0 0 0 12 22z"
        fill="#34A853"
      />
      <path
        d="M6.26 13.72a6.02 6.02 0 0 1 0-3.44V7.64H2.85a10 10 0 0 0 0 8.72l3.41-2.64z"
        fill="#FBBC05"
      />
      <path
        d="M12 5.56c1.5 0 2.85.52 3.91 1.53l2.93-2.93A9.97 9.97 0 0 0 12 2a10 10 0 0 0-9.15 5.64l3.41 2.64C7.07 7.36 9.33 5.56 12 5.56z"
        fill="#EA4335"
      />
    </svg>
  );
}

// 카카오 공식 노란 (#FEE500) + 검정 K
function KakaoIcon({ size }: { size: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none">
      <rect width="24" height="24" rx="5" fill="#FEE500" />
      <path
        d="M8.5 15.5V9h1.4v2.7L12.7 9h1.7L11.5 12l3.1 3.5h-1.8L10 12.8v2.7H8.5z"
        fill="#191919"
      />
    </svg>
  );
}

function OAuthBrandIcon({ provider, size = 20 }: OAuthBrandIconProps) {
  if (provider === 'naver') return <NaverIcon size={size} />;
  if (provider === 'google') return <GoogleIcon size={size} />;
  return <KakaoIcon size={size} />;
}

export default OAuthBrandIcon;
