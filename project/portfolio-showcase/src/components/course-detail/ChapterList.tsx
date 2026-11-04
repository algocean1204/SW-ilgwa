// 커리큘럼 챕터 목록과 추가·재생성 버튼. 순서 변경, 편집, 삭제를 처리한다.
// chapters 배열 상태는 상위 컨트롤러가 갖고 props로 내려준다.
import { type ReactNode } from 'react';
import type { Palette } from '@/lib';
import type { Chapter } from './types';
import Icon from '@/Icon';
import SectionFrame from '@/components/ui/SectionFrame';

interface IconBtnProps {
  children: ReactNode;
  onClick?: () => void;
  title?: string;
}
function IconBtn({ children, onClick, title }: IconBtnProps) {
  return (
    <button onClick={onClick} title={title} style={{
      width: 28, height: 28, borderRadius: 8,
      /* 표면 버튼이라 var(--paper) 토큰을 쓴다 */
      background: "var(--paper)", border: "1px solid var(--line)",
      color: "var(--ink-3)", fontSize: 13, fontWeight: 700,
    }}>
      {children}
    </button>
  );
}

interface ChapterListProps {
  palette: Palette;
  chapters: Chapter[];
  removeChapter: (id: string) => void;
  moveChapterUp: (id: string) => void;
  moveChapterDown: (id: string) => void;
  readOnly?: boolean;
  onChapterClick?: (chapter: Chapter) => void;
  /**
   * 지금 생성 중인(아직 LOCKED) 챕터 id 집합. 확정 뷰에서 폴링 결과로 넘겨준다.
   * 넘기면 해당 챕터에 "생성중" 펄스 배지를 붙이고, 없으면 배지를 붙이지 않는다.
   */
  generatingChapterIds?: ReadonlySet<string>;
}

export function ChapterList({ palette, chapters, removeChapter, moveChapterUp, moveChapterDown, readOnly, onChapterClick, generatingChapterIds }: ChapterListProps) {
  const actions = readOnly ? undefined : (
    <div style={{ display: "flex", gap: 6 }}>
      <button className="lift" style={{
        padding: "8px 12px", borderRadius: 9, fontSize: 12, fontWeight: 700,
        background: "var(--bg)", color: "var(--ink-2)", border: "1px solid var(--line)",
      }}>
        + 챕터 추가
      </button>
      <button className="lift" style={{
        padding: "8px 12px", borderRadius: 9, fontSize: 12, fontWeight: 700,
        background: "var(--bg)", color: "var(--ink-2)", border: "1px solid var(--line)",
      }}>
        <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
          <Icon name="wand" size={12} /> 다시 구성
        </span>
      </button>
    </div>
  );

  return (
    <SectionFrame
      title={readOnly ? "커리큘럼" : "커리큘럼 검토 · 조정"}
      description={readOnly ? "CURRICULUM" : "CURRICULUM · DRAFT"}
      action={actions}
    >
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {chapters.map((c, i) => {
          const isLocked = c.status === 'locked';
          const clickable = readOnly && !isLocked && onChapterClick;
          // 확정 뷰에서 폴링 중일 때만 LOCKED 챕터에 "생성중" 펄스 배지를 붙인다
          const isGenerating = !!generatingChapterIds?.has(c.id);
          return (
          <div
            key={c.id}
            className="lift"
            onClick={clickable ? () => onChapterClick(c) : undefined}
            style={{
              display: "grid",
              gridTemplateColumns: readOnly ? "auto 1fr auto" : "auto 1fr auto auto",
              alignItems: "center", gap: 16,
              padding: "14px 18px", borderRadius: 12,
              background: "var(--paper)", border: "1px solid var(--line)",
              cursor: clickable ? "pointer" : isLocked ? "not-allowed" : undefined,
              opacity: isLocked && readOnly ? 0.55 : 1,
            }}
          >
            {/* 챕터 번호 뱃지 */}
            <div style={{
              width: 32, height: 32, borderRadius: "50%",
              background: isLocked && readOnly ? "var(--sand-2)" : palette.wash,
              border: `1.5px solid ${isLocked && readOnly ? "var(--line)" : palette.soft}`,
              color: isLocked && readOnly ? "var(--ink-3)" : palette.primary,
              fontSize: 12, fontWeight: 800,
              display: "grid", placeItems: "center",
            }}>
              {isLocked && readOnly ? <Icon name="lock" size={12} /> : String(i + 1).padStart(2, "0")}
            </div>

            {/* 챕터 제목 + 슬라이드·분 정보 */}
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                <span style={{ fontSize: 14, fontWeight: 700, color: "var(--ink)", letterSpacing: 0 }}>
                  {c.t}
                </span>
                {/* 생성중 배지. 폴링으로 LOCKED가 확인된 챕터에만 붙인다 */}
                {isGenerating && (
                  <span style={{
                    display: "inline-flex", alignItems: "center", gap: 5,
                    padding: "2px 8px", borderRadius: 999,
                    fontSize: 10, fontWeight: 800, letterSpacing: 0.3,
                    background: palette.wash, color: palette.primary,
                    border: `1px solid ${palette.soft}`,
                  }}>
                    <span style={{
                      width: 5, height: 5, borderRadius: "50%", background: palette.primary,
                      animation: "cl-pulse 1.5s ease-in-out infinite",
                    }} />
                    생성중
                  </span>
                )}
                {/* 준비 완료 배지. readOnly 뷰에서 LOCKED가 풀린 챕터에만 붙인다 */}
                {readOnly && !isLocked && (
                  <span style={{
                    display: "inline-flex", alignItems: "center", gap: 4,
                    padding: "2px 8px", borderRadius: 999,
                    fontSize: 10, fontWeight: 800, letterSpacing: 0.3,
                    background: "var(--grass-wash)", color: "var(--grass-ink)",
                    border: "1px solid var(--grass-soft)",
                  }}>
                    <Icon name="check" size={9} color="var(--grass-ink)" strokeWidth={2.6} />
                    준비됨
                  </span>
                )}
              </div>
              <div style={{ fontSize: 12, color: "var(--ink-3)", marginTop: 3 }}>
                {c.s}개 슬라이드 · 약 {c.m}분
              </div>
            </div>

            {/* 슬라이드 수 시각화 막대 */}
            <div style={{ display: "flex", gap: 3 }}>
              {Array.from({ length: c.s }).map((_, j) => (
                <div key={j} style={{
                  width: 8, height: 24, borderRadius: 2,
                  background: palette.soft, opacity: .5 + j * 0.08,
                }} />
              ))}
            </div>

            {/* 편집 모드에서만 아이콘 버튼 표시 */}
            {!readOnly && (
              <div style={{ display: "flex", gap: 4 }}>
                <IconBtn title="순서 위로" onClick={() => moveChapterUp(c.id)}><Icon name="arrowUp" size={12} /></IconBtn>
                <IconBtn title="순서 아래" onClick={() => moveChapterDown(c.id)}><Icon name="arrowDown" size={12} /></IconBtn>
                <IconBtn title="세부 편집"><Icon name="edit" size={11} /></IconBtn>
                <IconBtn title="삭제" onClick={() => removeChapter(c.id)}>
                  <Icon name="x" size={12} />
                </IconBtn>
              </div>
            )}
          </div>
          );
        })}
      </div>

      {/* 편집 모드에서만 주제 추가 버튼 표시 */}
      {!readOnly && (
        <button className="lift" style={{
          width: "100%", padding: "14px", borderRadius: 12, marginTop: 10,
          fontSize: 12, fontWeight: 700, color: "var(--ink-3)",
          background: "transparent", border: "1.5px dashed var(--line)",
        }}>
          + 놓친 주제 직접 추가
        </button>
      )}

      <style>{`
        @keyframes cl-pulse {
          0%, 100% { transform: scale(1); opacity: 1; }
          50% { transform: scale(1.5); opacity: 0.5; }
        }
      `}</style>
    </SectionFrame>
  );
}
