// 강사용 강의 에디터. 드래그 상태와 4컬럼 그리드를 두고, 화면은 components/lesson-editor가 그린다.
import { useState } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import type { Palette } from '@/lib';
import type { LessonItem } from '@/components/lesson-editor/types';
import { EditorTopBar }         from '@/components/lesson-editor/EditorTopBar';
import { EditorRail }           from '@/components/lesson-editor/EditorRail';
import { LessonStructureAside } from '@/components/lesson-editor/LessonStructureAside';
import { LessonMainPane }       from '@/components/lesson-editor/LessonMainPane';
import { EditorAsideRight }     from '@/components/lesson-editor/EditorAsideRight';

// 초기 강의 구조
const INITIAL_ITEMS: LessonItem[] = [
  { id:"c1",  kind:"chapter", label:"Chapter 1. SW 개발 생명주기와 프로세스 모델" },
  { id:"1-1", kind:"lesson",  label:"SDLC 개요와 소프트웨어 공학의 핵심…",    state:"done" },
  { id:"1-2", kind:"lesson",  label:"소프트웨어 개발 프로세스 모델 정리 및…", state:"gen" },
  { id:"c2",  kind:"chapter", label:"Chapter 2. 계획 단계와 요구사항 분석의 이해" },
  { id:"2-1", kind:"lesson",  label:"계획 단계: 비용 산정과 일정 계획 핵심…", state:"wait" },
  { id:"2-2", kind:"lesson",  label:"요구사항 분석 종합 평가",                state:"wait" },
];

function LessonEditor(){
  const t = useTweaks();
  const palette: Palette = PALETTES[t.palette] ?? PALETTES.grass!;

  // 선택된 레슨 ID. aside 클릭으로 바뀐다.
  const [selected, setSelected] = useState("1-1");
  // 드래그 중인 아이템 인덱스. null이면 드래그 중이 아니다.
  const [draggedIdx, setDraggedIdx] = useState<number | null>(null);
  const [items, setItems] = useState<LessonItem[]>(INITIAL_ITEMS);

  const onDragStart = (i: number) => setDraggedIdx(i);

  // 드래그 오버할 때마다 아이템 순서를 바로 바꾼다
  const onDragOver = (e: React.DragEvent, i: number) => {
    e.preventDefault();
    if(draggedIdx===null || draggedIdx===i) return;
    const next = [...items];
    const [m] = next.splice(draggedIdx, 1);
    // 실제로 undefined일 수 없지만 타입 검사를 위해 확인한다
    if(!m) return;
    next.splice(i, 0, m);
    setItems(next);
    setDraggedIdx(i);
  };

  const onDragEnd = () => setDraggedIdx(null);

  return (
    // 고정 폭 대신 --page-max-w 토큰을 쓴다
    <div style={{
      width:'100%', maxWidth:'var(--page-max-w)', minHeight:900, margin:"0 auto", background:"var(--bg)",
      borderRadius:20, border:"1px solid var(--line)", overflow:"hidden", boxShadow:"var(--shadow-lg)",
    }}>
      {/* 상단 헤더 바 */}
      <EditorTopBar palette={palette} />

      {/* 4컬럼 그리드: 레일, 구조 aside, 메인, 우측 보조 패널 */}
      <div style={{ display:"grid", gridTemplateColumns:"64px 300px 1fr 360px", minHeight:840 }}>
        <EditorRail palette={palette} />

        <LessonStructureAside
          palette={palette}
          items={items}
          selected={selected}
          draggedIdx={draggedIdx}
          setSelected={setSelected}
          onDragStart={onDragStart}
          onDragOver={onDragOver}
          onDragEnd={onDragEnd}
        />

        <LessonMainPane palette={palette} />

        <EditorAsideRight palette={palette} />
      </div>
    </div>
  );
}

export default LessonEditor;
