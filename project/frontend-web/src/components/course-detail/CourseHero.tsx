// 커리큘럼 히어로 카드.
// 제목 인라인 편집(클릭하면 input), 태그, 슬라이드 수 합계를 보여준다.
import { type ReactNode } from 'react';
import type { Palette } from '@/lib';
import Icon from '@/Icon';

interface TagProps {
  children: ReactNode;
  dark?: boolean;
}
function Tag({ children, dark }: TagProps) {
  return (
    <span style={{
      fontSize: 12, fontWeight: 700, padding: "4px 10px", borderRadius: 999,
      background: dark ? "var(--black-overlay-sm)" : "var(--telegram-overlay-soft)",
      color: "var(--paper)", letterSpacing: ".02em",
    }}>
      {children}
    </span>
  );
}

interface StatProps {
  n: number | string;
  l: string;
}
function Stat({ n, l }: StatProps) {
  return (
    <div>
      <div style={{ fontSize: 22, fontWeight: 800, letterSpacing: 0 }}>{n}</div>
      <div style={{ fontSize: 12, opacity: .8, marginTop: 2 }}>{l}</div>
    </div>
  );
}

interface CourseHeroProps {
  palette: Palette;
  title: string;
  setTitle: (v: string) => void;
  editingTitle: boolean;
  setEditingTitle: (v: boolean) => void;
  onTitleSave?: (title: string) => void;
  chapterCount: number;
  totalSlides: number;
  totalMin: number;
  readOnly?: boolean;
  tutorName?: string;
  /** 과외 생성 방식: 'pdf'(PDF 업로드) / 'topic'(주제 입력). 본문 문구를 고르는 데 쓴다 */
  sourceType?: string;
}

export function CourseHero({
  palette, title, setTitle, editingTitle, setEditingTitle, onTitleSave,
  chapterCount, totalSlides, totalMin, readOnly, tutorName, sourceType,
}: CourseHeroProps) {
  const commitTitle = () => {
    setEditingTitle(false);
    if (title.trim()) onTitleSave?.(title.trim());
  };
  return (
    <div style={{
      /* 페이지에서 가장 눈에 띄어야 해서 --shadow-md를 준다 */
      padding: "28px 32px", borderRadius: 20,
      background: palette.primary,
      color: "var(--paper)", position: "relative", overflow: "hidden", minHeight: 300,
      boxShadow: "var(--shadow-md)",
    }}>
      <div className="dots" style={{ position: "absolute", inset: 0, opacity: .18 }} />
      <div style={{ position: "relative" }}>
        <div style={{ display: "flex", gap: 6, marginBottom: 14 }}>
          <Tag>{chapterCount} 챕터</Tag>
          <Tag>{totalSlides} 슬라이드</Tag>
          {tutorName && (
            <Tag dark>
              <Icon name="owl" size={11} color="var(--paper)" style={{ marginRight: 4 }} />
              {tutorName}
            </Tag>
          )}
        </div>

        {!readOnly && editingTitle ? (
          <input
            autoFocus
            value={title}
            onChange={e => setTitle(e.target.value)}
            onBlur={commitTitle}
            onKeyDown={e => e.key === "Enter" && commitTitle()}
            style={{
              width: "100%", background: "var(--white-overlay-md)",
              border: "1.5px solid var(--white-overlay-xl)", borderRadius: 10,
              padding: "10px 14px", color: "var(--paper)", fontSize: 22, fontWeight: 800,
              outline: "none", letterSpacing: 0,
            }}
          />
        ) : (
          <h1
            onClick={readOnly ? undefined : () => setEditingTitle(true)}
            style={{
              margin: 0, fontSize: 28, fontWeight: 800, letterSpacing: 0, lineHeight: 1.2,
              cursor: readOnly ? "default" : "text",
            }}
          >
            {title}
            {!readOnly && (
              <span style={{
                fontSize: 12, opacity: .6, marginLeft: 10, fontWeight: 500,
                display: "inline-flex", alignItems: "center", gap: 4,
              }}>
                <Icon name="edit" size={11} color="var(--white-overlay-2xl)" />
                클릭해서 수정
              </span>
            )}
          </h1>
        )}

        <p style={{ margin: "14px 0 0", fontSize: 13, lineHeight: 1.65, opacity: .92, maxWidth: 500 }}>
          {readOnly
            ? <><b>{chapterCount}개 챕터, {totalSlides}개 슬라이드</b>로 구성된 커리큘럼입니다. 챕터를 눌러 강의를 시작하세요.</>
            /* sourceType별 본문 문구. pdf는 교재 분석, topic은 주제 기반 */
            : sourceType === 'pdf'
              ? <>업로드한 교재를 분석해서 <b>{chapterCount}개 챕터, {totalSlides}개 슬라이드</b>로 쪼갰어요. 과외 한 개당 슬라이드 10~13개 내외로 핵심만 추려요. 빼고 싶은 챕터가 있으면 제거하고 확정하세요.</>
              : <>입력한 주제로 <b>{chapterCount}개 챕터, {totalSlides}개 슬라이드</b>를 구성했어요. 과외 한 개당 슬라이드 10~13개 내외로 핵심만 추려요. 빼고 싶은 챕터가 있으면 제거하고 확정하세요.</>
          }
        </p>

        <div style={{ display: "flex", gap: 18, marginTop: 22, fontSize: 12 }}>
          <Stat n={chapterCount} l="챕터" />
          <Stat n={totalSlides} l="슬라이드" />
          <Stat n={`${Math.round(totalMin / 60 * 10) / 10}h`} l="예상 강의" />
          <Stat n="30일" l="학습 기간" />
        </div>
      </div>
    </div>
  );
}
