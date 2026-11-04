// 온보딩 1단계: 과목 선택(9개 그리드, 복수 선택). 선택 상태와 토글은 부모가 넘긴다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';

export interface Step1Props {
  subjects: Set<string>;
  toggle: (id: string) => void;
  palette: Palette;
}

// 선택 가능한 과목. 화면 순서는 이 배열 순서를 따른다.
export const SUBJECT_LIST = [
  { id: "sdlc",   label: "정보처리기사",  icon: "book",    desc: "실기 · 필기 · 과제형" },
  { id: "toeic",  label: "토익 · 토플",   icon: "eye",     desc: "LC · RC · Writing" },
  { id: "su",     label: "수능 · 내신",   icon: "note",    desc: "국어 · 영어 · 수학" },
  { id: "cs",     label: "컴퓨터과학",    icon: "sliders", desc: "자료구조 · OS · 네트워크" },
  { id: "ds",     label: "데이터 분석",   icon: "chart",   desc: "통계 · ML · LLM" },
  { id: "biz",    label: "경영 · 회계",   icon: "credit",  desc: "재무제표 · 세무 · 원가" },
  { id: "law",    label: "법학 · 시사",   icon: "flag",    desc: "행정고시 · 법무사 · 상식" },
  { id: "lang",   label: "외국어",        icon: "message", desc: "JLPT · HSK · DELE · 독일어" },
  { id: "design", label: "디자인 · 기획", icon: "edit",    desc: "UX 리서치 · 프로덕트" },
];

export function OnboardingStep1({ subjects, toggle, palette }: Step1Props) {
  return (
    <div>
      <h1 style={{ margin: 0, fontSize: 32, fontWeight: 800, letterSpacing: 0, color: "var(--ink)" }}>
        어떤 과목을 공부하세요?
      </h1>
      <p style={{ marginTop: 10, fontSize: 14, color: "var(--ink-3)" }}>
        여러 개 골라도 돼요. 과외는 나중에 더 추가할 수 있어요.
      </p>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10, marginTop: 24 }}>
        {SUBJECT_LIST.map(it => {
          const on = subjects.has(it.id);
          return (
            <button key={it.id} onClick={() => toggle(it.id)} className="lift" style={{
              padding: "16px 14px", borderRadius: 14, textAlign: "left",
              background: on ? palette.wash : "var(--paper)",
              border: on ? `1.5px solid ${palette.primary}` : "1px solid var(--line)",
              position: "relative",
              transition: "all .2s",
            }}>
              <div style={{ marginBottom: 8 }}>
                <Icon name={it.icon} size={20} color={on ? palette.primary : "var(--ink-3)"} strokeWidth={1.7} />
              </div>
              <div style={{ fontSize: 14, fontWeight: 700, color: on ? palette.primary : "var(--ink)", letterSpacing: 0 }}>
                {it.label}
              </div>
              <div style={{ fontSize: 12, color: "var(--ink-3)", marginTop: 3 }}>
                {it.desc}
              </div>
              {on && (
                <div style={{
                  position: "absolute", top: 10, right: 10,
                  width: 18, height: 18, borderRadius: "50%",
                  background: palette.primary, color: "var(--paper)",
                  display: "grid", placeItems: "center",
                }}>
                  <Icon name="check" size={10} color="var(--paper)" strokeWidth={3} />
                </div>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
