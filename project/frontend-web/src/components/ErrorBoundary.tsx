// 전역 에러 경계. 에러 경계는 클래스 생명주기로만 만들 수 있다.
// 자식 트리에서 throw되면 화이트스크린 대신 fallback UI를 그린다.
// 에러는 삼키지 않고 console.error로 남긴다.
import React from 'react';
import Icon from '@/Icon';

interface ErrorBoundaryProps {
  children: React.ReactNode;
  // 커스텀 fallback이 없으면 기본 fallback UI를 쓴다
  fallback?: React.ReactNode;
  // 로그 식별용 격리 레벨 이름 (예: "workspace", "global")
  scope?: string;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

// 기본 fallback UI. 색은 디자인 토큰 CSS 변수만 쓴다.
function DefaultFallback({ onRetry, onHome }: { onRetry: () => void; onHome: () => void }) {
  return (
    <div
      role="alert"
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: 240,
        padding: '32px 24px',
        gap: 16,
        textAlign: 'center',
      }}
    >
      <div style={{
        width: 48,
        height: 48,
        borderRadius: '50%',
        background: 'var(--state-danger-bg)',
        border: '1px solid var(--coral)',
        display: 'grid',
        placeItems: 'center',
      }}>
        <Icon name="warning" size={22} color="var(--coral)" />
      </div>
      <div>
        <div style={{
          fontSize: 15,
          fontWeight: 800,
          color: 'var(--ink)',
          marginBottom: 6,
        }}>
          문제가 발생했어요
        </div>
        <div style={{
          fontSize: 12.5,
          color: 'var(--ink-3)',
          lineHeight: 1.6,
          maxWidth: 280,
        }}>
          이 화면을 불러오는 중 오류가 발생했습니다.
          <br />
          잠시 후 다시 시도해주세요.
        </div>
      </div>
      <div style={{ display: 'flex', gap: 10 }}>
        <button
          onClick={onRetry}
          style={{
            padding: '9px 18px',
            borderRadius: 'var(--radius-md)',
            fontSize: 12.5,
            fontWeight: 800,
            background: 'var(--ink)',
            color: 'var(--paper)',
            border: 'none',
            cursor: 'pointer',
          }}
        >
          다시 시도
        </button>
        <button
          onClick={onHome}
          style={{
            padding: '9px 18px',
            borderRadius: 'var(--radius-md)',
            fontSize: 12.5,
            fontWeight: 700,
            background: 'var(--surface-subtle)',
            color: 'var(--ink-2)',
            border: '1px solid var(--line)',
            cursor: 'pointer',
          }}
        >
          홈으로
        </button>
      </div>
    </div>
  );
}

class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  // fallback을 보여 주면서도 에러 내용은 콘솔에 남긴다
  componentDidCatch(error: Error, info: React.ErrorInfo): void {
    const scope = this.props.scope ?? 'unknown';
    console.error(`[ErrorBoundary:${scope}] 컴포넌트 에러 발생`, error, info.componentStack);
  }

  private handleRetry = () => {
    // 에러 상태를 지워 자식 트리를 다시 마운트한다
    this.setState({ hasError: false, error: null });
  };

  private handleHome = () => {
    // 페이지를 루트로 새로 불러온다
    window.location.href = '/';
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }
      return (
        <DefaultFallback
          onRetry={this.handleRetry}
          onHome={this.handleHome}
        />
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
