// 서비스 신뢰 지표 바. 구매 직전에 이 서비스를 믿을 수 있는 이유를 보여준다.
import Icon from '@/Icon';

// 신뢰 항목 하나의 props
interface TrustProps {
  // Icon.tsx 아이콘 키
  i: string;
  // 주 문구. 서비스 특징을 적는다
  t: string;
  // 부 설명. 없으면 그리지 않는다
  sub?: string;
}

// 아이콘, 주 문구, 부 설명으로 된 신뢰 항목 하나
function Trust({ i, t, sub }: TrustProps) {
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 3, textAlign: "center", flex: 1 }}>
      <Icon name={i} size={16} color="var(--text-primary)" />
      <span style={{ fontWeight: 700, fontSize: 12, color: "var(--text-primary)" }}>{t}</span>
      {sub && (
        <span style={{ fontSize: 11, color: "var(--text-tertiary)", lineHeight: 1.45 }}>{sub}</span>
      )}
    </div>
  );
}

// 이 서비스를 믿을 수 있는 이유 4가지. 결제 수단 대신 특징, 유연성, 지원을 적는다
const TRUST_ITEMS: TrustProps[] = [
  { i: "phone",   t: "텔레그램 과제·채점 포함",   sub: "루브릭으로 채점·총평 전달" },
  { i: "compass", t: "튜터 커스터마이징",          sub: "학습 스타일 맞게 설정 가능" },
  { i: "chart",   t: "학습 리포트·모의고사 포함",  sub: "완강 후 모의고사 1회 무료" },
  { i: "refresh", t: "언제든 업·다운그레이드",     sub: "7일 환불 보장 · 위약금 없음" },
];

export function TrustBar() {
  return (
    // 보조 패널이라 var(--surface-subtle) 배경을 쓴다
    <div style={{
      marginTop: 24, padding: "18px 24px", borderRadius: 14,
      background: "var(--surface-subtle)", border: "1px solid var(--border-soft)",
      display: "flex", gap: 8, justifyContent: "space-between",
      fontSize: 12, color: "var(--text-secondary)",
    }}>
      {TRUST_ITEMS.map(item => (
        <Trust key={item.t} i={item.i} t={item.t} sub={item.sub} />
      ))}
    </div>
  );
}
