// 수강 중인 과외가 없을 때 보여주는 빈 상태.
// StudentHome.tsx에서 따로 뺐다.
import type { RouteSetter } from '@/shared/types';

interface EmptyHomeStateProps {
  setRoute: RouteSetter;
}

export function EmptyHomeState({ setRoute }: EmptyHomeStateProps) {
  return (
    <div className="home-studio">
      <div style={{ padding: 40, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
        <div style={{ marginBottom: 12, fontSize: 15, fontWeight: 700, color: 'var(--ink)' }}>
          아직 수강 중인 과외가 없어요
        </div>
        <div style={{ marginBottom: 20 }}>새 과외를 만들어 학습을 시작해보세요.</div>
        <button
          onClick={() => setRoute('explore')}
          style={{
            padding: '10px 20px',
            borderRadius: 'var(--radius-md)',
            fontSize: 13,
            fontWeight: 800,
            background: 'var(--ink)',
            color: 'var(--paper)',
            border: 'none',
            cursor: 'pointer',
          }}
        >
          새 과외 만들기
        </button>
      </div>
    </div>
  );
}
