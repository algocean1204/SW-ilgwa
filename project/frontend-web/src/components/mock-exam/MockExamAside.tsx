// 모의고사 오른쪽 패널. 응시 기록과 시험 준비 팁을 보인다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';
import InspectorPanel from '@/components/ui/InspectorPanel';

interface ExamHistoryItem {
  id: string;
  cid: string;
  title: string;
  score: number;
  date: string;
  q: number;
}

interface MockExamAsideProps {
  palette: Palette;
  history: ExamHistoryItem[];
}

const TIPS = [
  "완강 후 응시하면 전 범위에서 핵심 문제가 고르게 나와요.",
  "약점으로 표시된 챕터를 한 번 더 복습하면 점수가 올라요.",
  "해설 보기로 틀린 개념을 바로 확인하면 기억에 오래 남아요.",
];

// 문항 수·시간 같은 지표 하나를 보이는 카드. 다른 aside에서도 써서 export한다.
export function InfoBox({ k, v }: { k: string; v: number | string }) {
  return (
    <div style={{
      padding: "8px 10px", borderRadius: 8,
      background: "var(--surface-page)",
      border: "1px solid var(--border-soft)",
      textAlign: "center",
    }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "var(--text-tertiary)", letterSpacing: ".04em" }}>
        {k}
      </div>
      <div style={{ fontSize: 15, fontWeight: 800, color: "var(--text-primary)", marginTop: 2 }}>
        {v}
      </div>
    </div>
  );
}

// 사이드바는 메인 카드보다 눈에 덜 띄게 surface-subtle 배경과 작은 글씨를 쓴다.
function MockExamAside({ palette, history }: MockExamAsideProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--section-gap)' }}>
      <InspectorPanel title="응시 기록" sticky={false}>
        {history.length === 0 ? (
          <div style={{ fontSize: 11, color: "var(--text-tertiary)", textAlign: "center", padding: "12px 0" }}>
            아직 응시 기록이 없어요
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {history.map(h => (
              <div key={h.id} style={{
                padding: "8px 10px", borderRadius: 8,
                background: "var(--surface-page)",
                border: "1px solid var(--border-soft)",
              }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: "var(--text-primary)", lineHeight: 1.4 }}>
                  {h.title}
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 6, marginTop: 3, fontSize: 11, color: "var(--text-tertiary)" }}>
                  <span style={{ color: palette.primary, fontWeight: 800 }}>{h.score}점</span>
                  <span>· {h.q}문항</span>
                  <span>· {h.date}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </InspectorPanel>

      <InspectorPanel title={<><Icon name="wand" size={11} /> 시험 준비 팁</>} sticky={false}>
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {TIPS.map((tip, i) => (
            <div key={i} style={{ display: "flex", gap: 6, fontSize: 11, color: "var(--text-secondary)", lineHeight: 1.6 }}>
              <span style={{ color: palette.primary, fontWeight: 800, flexShrink: 0 }}>{i + 1}.</span>
              <span>{tip}</span>
            </div>
          ))}
        </div>
      </InspectorPanel>
    </div>
  );
}

export default MockExamAside;
