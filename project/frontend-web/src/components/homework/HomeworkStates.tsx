// 채점 중(GradingState)과 제출 대기(PendingState) 과제 상태 화면
import type { Palette } from '@/lib';
import type { Assignment } from '@/shared/types';
import Icon from '@/Icon';

interface GradingStateProps {
  hw: Assignment;
  palette: Palette;
}

// PendingState는 palette를 쓰지 않아 hw만 받는다
interface PendingStateProps {
  hw: Assignment;
}

export function GradingState({ hw, palette }: GradingStateProps) {
  return (
    <div style={{ padding: '60px 40px', textAlign: 'center' }}>
      <div style={{ marginBottom: 14 }}><Icon name="brain" size={44} color="var(--ink-3)" /></div>
      <h2 style={{ margin: '0 0 6px', fontSize: 22, fontWeight: 800 }}>채점 중이에요</h2>
      <div style={{ fontSize: 13, color: 'var(--ink-3)', marginBottom: 20 }}>보통 1~2분이면 끝나요. 완료되면 텔레그램으로 알려드릴게요.</div>
      <div style={{ margin: '0 auto', maxWidth: 300, height: 6, background: 'var(--sand)', borderRadius: 3, overflow: 'hidden' }}>
        <div style={{
          height: '100%', width: '68%', background: palette.primary2, borderRadius: 3,
        }} />
      </div>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 10 }}>{hw.title}</div>
    </div>
  );
}

export function PendingState({ hw }: PendingStateProps) {
  return (
    <div style={{ padding: '50px 40px', textAlign: 'center' }}>
      <div style={{ marginBottom: 14 }}><Icon name="note" size={44} color="var(--ink-3)" /></div>
      <h2 style={{ margin: '0 0 6px', fontSize: 22, fontWeight: 800 }}>{hw.title}</h2>
      {hw.due && <div style={{ fontSize: 13, color: 'var(--coral-primary)', fontWeight: 700, marginBottom: 20 }}>마감 {hw.due}</div>}
      <div style={{ fontSize: 13, color: 'var(--ink-3)', maxWidth: 400, margin: '0 auto', lineHeight: 1.6 }}>
        텔레그램으로 과제를 보내드렸어요. 답변을 보내면 바로 채점해드립니다.
      </div>
      <button className="lift" style={{
        marginTop: 18, padding: '11px 22px', borderRadius: 10, fontSize: 13, fontWeight: 800,
        background: 'var(--telegram-blue)', color: 'var(--surface)', boxShadow: '0 8px 18px -10px var(--telegram-blue)',
      }}>텔레그램으로 이동</button>
    </div>
  );
}
