// "내 과외" 카드 그리드 목록
import { useState, useEffect } from 'react';
import { useTweaks, PALETTES, formatRelativeTime } from '@/lib';
import Icon from '@/Icon';
import { getMyCourses } from '@/apis';
import type { CourseSummaryResponse, CourseStatus } from '@/apis';
import type { AppState, RouteSetter } from '@/shared/types';
import { SpotlightCard } from '@/components/animated';
import PageIntro from '@/components/ui/PageIntro';

interface MyCoursesProps {
  setRoute: RouteSetter;
  appState: AppState;
}

type FilterKey = 'all' | 'active' | 'completed';
const FILTERS: { key: FilterKey; label: string; status?: CourseStatus }[] = [
  { key: 'all', label: '전체' },
  { key: 'active', label: '진행중', status: 'ACTIVE' },
  { key: 'completed', label: '완강', status: 'COMPLETED' },
];

function MyCourses({ setRoute, appState }: MyCoursesProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;

  const [courses, setCourses] = useState<CourseSummaryResponse[]>([]);
  const [filter, setFilter] = useState<FilterKey>('all');

  useEffect(() => {
    const f = FILTERS.find(f => f.key === filter);
    void getMyCourses({ size: 50, status: f?.status }).then((res) => {
      if (res.data?.content) setCourses(res.data.content);
    }).catch((err) => {
      console.warn('내 과외 목록 조회 실패:', err);
    });
  }, [filter]);

  const go = (cid: string) => {
    appState.setActiveCourseId(cid);
    setRoute("courseDetail");
  };

  return (
    <div style={{ maxWidth:'var(--page-max-w)', margin:"0 auto", padding:"var(--page-py) var(--page-px) 56px" }}>
      {/* 페이지 헤더. 학습실 탭과 같은 PageIntro를 쓴다 */}
      <PageIntro
        eyebrow="MY TUTORING"
        title={
          <>
            내 과외{' '}
            <span style={{ fontSize: 16, color: 'var(--text-tertiary)', fontWeight: 600, marginLeft: 8 }}>
              {courses.length}개
            </span>
          </>
        }
        actions={
          <button onClick={() => setRoute("explore")} className="lift" style={{
            padding: "12px 20px", borderRadius: 12, fontSize: 13, fontWeight: 800,
            background: palette.primary, color: "var(--surface-strong)",
            flexShrink: 0,
            boxShadow: `0 10px 22px -10px ${palette.primary}`,
          }}>
            <Icon name="plus" size={13} color="var(--surface-strong)" style={{ marginRight: 6 }} />
            새 과외 만들기
          </button>
        }
      />

      {/* 필터 탭 */}
      <div id="active-courses" style={{ display:"flex", gap:6, marginBottom:16 }}>
        {FILTERS.map((f) => (
          <button key={f.key} onClick={() => setFilter(f.key)} style={{
            padding:"6px 14px", borderRadius:8, fontSize:12, fontWeight:700,
            background: filter === f.key ? "var(--accent-soft)" : "transparent",
            color: filter === f.key ? "var(--accent-primary)" : "var(--text-tertiary)",
            border: filter === f.key ? "1px solid var(--accent-primary)" : "1px solid var(--border-default)",
          }}>{f.label}</button>
        ))}
      </div>

      {/* 카드 그리드. 열 수는 index.css .course-grid 미디어 쿼리가 정한다.
           인라인 gridTemplateColumns를 두지 않아야 모바일(375px)에서 1열이 된다. */}
      <div id="completed-courses" className="course-grid" style={{ display:"grid", gap:18 }}>
        {courses.map(c => {
          // course.progress(DB progress_percent)는 백엔드가 갱신하지 않아 항상 0이다.
          // 그래서 completedLessons / totalLessons로 계산한다.
          const pct = c.totalLessons > 0 ? Math.round((c.completedLessons / c.totalLessons) * 100) : 0;
          return (
          <SpotlightCard key={c.id} radius={180} opacity={0.05} color={palette.primary}
            style={{ borderRadius:16 }}>
          <button onClick={() => go(c.id)} className="lift" style={{
            padding:0, borderRadius:16, background:"var(--surface-card)", border:"1px solid var(--border-default)",
            overflow:"hidden", textAlign:"left", display:"flex", flexDirection:"column",
            width:"100%",
          }}>
            {/* 헤더 배너 */}
            <div style={{ padding:"16px 18px", background:palette.primary, position:"relative" }}>
              <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:10 }}>
                <div style={{ width:30, height:30, borderRadius:"50%", background:"var(--white-overlay-3xl)", display:"grid", placeItems:"center", color:"var(--paper)" }}>
                  <Icon name="owl" size={16}/>
                </div>
                <div style={{ flex:1 }}>
                  <div style={{ fontSize:12, fontWeight:800, opacity:.85, color:"var(--paper)", letterSpacing:".08em" }}>TUTOR</div>
                  <div style={{ fontSize:12, fontWeight:800, color:"var(--paper)" }}>{c.tutorName}</div>
                </div>
              </div>
              <div style={{ fontSize:16, fontWeight:800, color:"var(--paper)", letterSpacing:"-.02em" }}>{c.title}</div>

            </div>

            {/* 본문 */}
            <div style={{ padding:"16px 18px", flex:1, display:"flex", flexDirection:"column", gap:12 }}>
              {/* CURRICULUM_FAILED면 진행도 대신 실패 안내를 보여 준다 */}
              {c.status === 'CURRICULUM_FAILED' ? (
                <div style={{
                  padding:"10px 12px", borderRadius:10,
                  background:"var(--state-danger-bg)",
                  border:"1px solid var(--state-danger)",
                  display:"flex", alignItems:"center", gap:8,
                }}>
                  <Icon name="warning" size={14} color="var(--state-danger)" />
                  <span style={{ fontSize:12, fontWeight:700, color:"var(--state-danger)" }}>
                    커리큘럼 생성 실패
                  </span>
                </div>
              ) : (
                <div>
                  <div style={{ display:"flex", justifyContent:"space-between", alignItems:"baseline", marginBottom:5 }}>
                    <span style={{ fontSize:12, color:"var(--text-tertiary)", fontWeight:700 }}>진행도</span>
                    <span style={{ fontSize:13, fontWeight:800, color:palette.primary }}>{pct}%</span>
                  </div>
                  <div style={{ height:5, background:"var(--sand)", borderRadius:3, overflow:"hidden" }}>
                    <div style={{ height:"100%", width:pct+"%", background:palette.primary, borderRadius:3 }} />
                  </div>
                </div>
              )}

              <div>
                <Stat lbl="강의" v={`${c.completedLessons}/${c.totalLessons}`} />
              </div>

              <div style={{
                marginTop:"auto", padding:"10px 12px", borderRadius:10,
                background: c.status === 'CURRICULUM_FAILED' ? "var(--state-danger-bg)" : palette.wash,
                border: c.status === 'CURRICULUM_FAILED' ? "1px solid var(--state-danger)" : `1px solid ${palette.soft}`,
                display:"flex", alignItems:"center", gap:8,
              }}>
                <div style={{ flex:1 }}>
                  <div style={{ fontSize:12, color:"var(--text-tertiary)", fontWeight:700 }}>마지막 학습</div>
                  <div style={{ fontSize:12, fontWeight:700, color:"var(--text-primary)" }}>{formatRelativeTime(c.lastStudiedAt)}</div>
                </div>
                <div style={{
                  padding:"6px 11px", borderRadius:8, fontSize:12, fontWeight:800,
                  background: c.status === 'CURRICULUM_FAILED' ? "var(--state-danger)" : palette.primary,
                  color:"var(--paper)",
                }}>
                  {c.status === 'CURRICULUM_FAILED' ? '다시 시도' : '이어하기'}
                </div>
              </div>
            </div>
          </button>
          </SpotlightCard>
        );
        })}

        {/* 새 과외 만들기 빈 카드 */}
        <button onClick={()=>setRoute("explore")} className="lift" style={{
          padding:18, borderRadius:16, background:"transparent",
          border:"1.5px dashed var(--border-default)", textAlign:"center",
          display:"flex", flexDirection:"column", gap:12, alignItems:"center", justifyContent:"center",
          minHeight:220, color:"var(--text-tertiary)",
        }}>
          <div style={{
            width:46, height:46, borderRadius:23, background:palette.wash,
            display:"grid", placeItems:"center", color:palette.primary,
          }}><Icon name="plus" size={20}/></div>
          <div style={{ fontSize:13, fontWeight:800, color:"var(--text-primary)" }}>새 과외 만들기</div>
          <div style={{ fontSize:12, lineHeight:1.5, maxWidth:200 }}>PDF나 교재를 올리면 30일 커리큘럼으로 정리해드려요</div>
        </button>
      </div>
    </div>
  );
}

interface StatProps {
  lbl: string;
  v: number | string;
}
function Stat({ lbl, v }: StatProps) {
  return (
    <div style={{ padding:"6px 8px", borderRadius:8, background:"var(--surface-page)", textAlign:"center" }}>
      <div style={{ fontSize:12, color:"var(--text-tertiary)", fontWeight:700 }}>{lbl}</div>
      <div style={{ fontSize:13, fontWeight:800, color:"var(--text-primary)", marginTop:2 }}>{v}</div>
    </div>
  );
}

export default MyCourses;
