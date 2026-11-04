// 모의고사 개요 화면. 과외를 고르고, 기존 모의고사를 상태 뱃지와 함께 보이며, 생성 폼을 연다.
import { useState } from 'react';
import Icon from '@/Icon';
import { ShimmerButton } from '@/components/animated';
import StatusBadge from '@/components/ui/StatusBadge';
import SectionFrame from '@/components/ui/SectionFrame';
import type { Palette } from '@/lib';
import type { CourseSummaryResponse, GenerateExamRequest, MockExamListResponse } from '@/apis';
import ExamGenerateOptions from './ExamGenerateOptions';
import PartialSourceNotice from './PartialSourceNotice';
import { resolveGenerationDisplay } from './generationOutcome';
import { formatDifficultyLabel } from './examOptions';

interface ExamOverviewProps {
  courses: CourseSummaryResponse[];
  selectedCourseId: string;
  examList: MockExamListResponse[];
  listLoading: boolean;
  coins: number;
  cost: number;
  palette: Palette;
  /**
   * CON_004(미완강)이면 true. 가격 페이지로 보내지 않고 생성 버튼을 끄고 안내를 보인다.
   */
  courseNotCompleted: boolean;
  onSelectCourse: (id: string) => void;
  onGenerate: (req: GenerateExamRequest) => void;
  /**
   * 응시 진입. cutover 항목은 attemptId(eat_), 레거시 항목은 examId(mex_)를 넘긴다.
   */
  onOpenExam: (examOrAttemptId: string) => void;
  /**
   * 결과 조회. cutover 항목은 attemptId(eat_), 레거시 항목은 examId(mex_)를 넘긴다.
   */
  onViewResult: (examOrAttemptId: string) => void;
  /** 생성권 부족(CON_001) 시 충전 페이지로 보낸다. */
  onNeedCoins: () => void;
}

// 모의고사 상태 → 뱃지 tone/label. generationFailureReason이 있으면(최신 attempt FAILED)
// status가 GENERATING이어도 '생성 실패'로 보여, 실패한 시험이 '출제 중'으로 남지 않게 한다.
function statusBadge(
  status: MockExamListResponse['status'],
  generationFailureReason?: string | null,
): { tone: 'success' | 'warning' | 'info' | 'danger'; label: string } {
  if (status === 'COMPLETED') return { tone: 'success', label: '응시 완료' };
  if (status === 'READY') return { tone: 'info', label: '응시 가능' };
  if (generationFailureReason) return { tone: 'danger', label: '생성 실패' };
  return { tone: 'warning', label: '출제 중' };
}

export default function ExamOverview({
  courses, selectedCourseId, examList, listLoading, coins, cost, palette,
  courseNotCompleted, onSelectCourse, onGenerate, onOpenExam, onViewResult, onNeedCoins,
}: ExamOverviewProps) {
  const [creating, setCreating] = useState(false);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--section-gap)' }}>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {courses.length === 0 && (
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', padding: '12px 0' }}>
            수강 중인 과외가 없어요.
          </div>
        )}
        {courses.map((c) => {
          const active = c.id === selectedCourseId;
          return (
            <button
              key={c.id}
              onClick={() => { onSelectCourse(c.id); setCreating(false); }}
              className="lift"
              style={{
                flex: '1 1 180px', padding: '10px 14px', borderRadius: 10, textAlign: 'left',
                background: 'var(--surface-card)',
                border: active ? `2px solid ${palette.primary}` : '1px solid var(--border-default)',
                boxShadow: active ? 'var(--shadow-md)' : 'none', cursor: 'pointer',
              }}
            >
              <span style={{ fontSize: 12, fontWeight: 800, color: 'var(--text-primary)' }}>{c.title}</span>
              {/* 완강해야 생성할 수 있어 "0/N강" 대신 완강 여부만 보인다 */}
              <div style={{ fontSize: 11, color: c.status === 'COMPLETED' ? 'var(--state-success, #16a34a)' : 'var(--text-tertiary)', marginTop: 4 }}>
                {c.status === 'COMPLETED' ? '완강' : '완강 필요'}
              </div>
            </button>
          );
        })}
      </div>

      <SectionFrame
        title="새 모의고사"
        description={creating ? '옵션을 고르고 생성하세요' : 'AI가 전 범위에서 실전 문항을 출제합니다'}
      >
        {/* CON_004 미완강 안내. 가격 페이지로 보내지 않고 여기서 보인다 */}
        {courseNotCompleted && (
          <div
            role="alert"
            style={{
              marginBottom: 12, padding: '10px 14px', borderRadius: 10,
              background: 'var(--state-warning-bg, #fff8e6)',
              border: '1px solid var(--state-warning, #f59e0b)',
              fontSize: 13, fontWeight: 600,
              color: 'var(--text-primary)',
            }}
          >
            강의를 먼저 완료해야 모의고사를 만들 수 있어요.
          </div>
        )}
        {creating ? (
          <ExamGenerateOptions
            palette={palette}
            coins={coins}
            cost={cost}
            onGenerate={(req) => { setCreating(false); onGenerate(req); }}
            onCancel={() => setCreating(false)}
            onNeedCoins={onNeedCoins}
          />
        ) : (
          /* 완강 전에는 생성 버튼을 끈다 */
          <ShimmerButton
            onClick={() => { if (!courseNotCompleted) setCreating(true); }}
            className="lift"
            shimmerColor="rgba(255,255,255,0.15)"
            disabled={!selectedCourseId || courseNotCompleted}
            style={{
              width: '100%', padding: '16px 20px', borderRadius: 12, gap: 8,
              background: (selectedCourseId && !courseNotCompleted) ? palette.primary : 'var(--text-tertiary)',
              color: 'var(--paper)', fontSize: 14, fontWeight: 800,
              boxShadow: 'var(--shadow-lg)',
              cursor: (selectedCourseId && !courseNotCompleted) ? 'pointer' : 'not-allowed',
            }}
          >
            <Icon name="target" size={18} color="var(--paper)" /> 새 모의고사 시작
          </ShimmerButton>
        )}
      </SectionFrame>

      <SectionFrame title="내 모의고사" description="이전에 생성한 모의고사를 다시 응시하거나 결과를 확인하세요">
        {listLoading ? (
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', textAlign: 'center', padding: '20px 0' }}>
            목록을 불러오는 중...
          </div>
        ) : examList.length === 0 ? (
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', textAlign: 'center', padding: '20px 0' }}>
            아직 생성한 모의고사가 없어요.
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {examList.map((e) => {
              const badge = statusBadge(e.status, e.generationFailureReason);
              const ready = e.status === 'READY';
              const done = e.status === 'COMPLETED';
              // cutover 항목은 attemptId(eat_), 레거시 항목은 examId(mex_)로 진입한다.
              const entryId = e.attemptId ?? e.id;
              const canEnter = ready || done;
              // 자료가 좁아 일부만 생성됐는지 본다. 'ok'면 배지가 없다.
              const genDisplay = resolveGenerationDisplay(e);
              return (
                <button
                  key={e.id}
                  onClick={() => {
                    if (!canEnter) return;
                    if (ready) onOpenExam(entryId);
                    else if (done) onViewResult(entryId);
                  }}
                  className={canEnter ? 'lift' : undefined}
                  disabled={!canEnter}
                  aria-disabled={!canEnter}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 12, width: '100%', textAlign: 'left',
                    padding: '12px 14px', borderRadius: 10,
                    background: 'var(--surface-page)', border: '1px solid var(--border-soft)',
                    cursor: canEnter ? 'pointer' : 'default',
                  }}
                >
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 800, color: 'var(--text-primary)', lineHeight: 1.4 }}>
                      {e.title}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 3 }}>
                      {e.questionCount}문항 · {e.timeLimit != null ? `${e.timeLimit}분` : '무제한'} · {formatDifficultyLabel(e.difficulty)}
                    </div>
                    {/* "요청 N문항 중 M문항 생성됨" 배지. 응시는 그대로 할 수 있다 */}
                    {genDisplay.kind === 'partial' && (
                      <div style={{ marginTop: 5 }}>
                        <PartialSourceNotice state={genDisplay} variant="badge" />
                      </div>
                    )}
                  </div>
                  <StatusBadge tone={badge.tone} label={badge.label} size="sm" />
                  {canEnter && <Icon name="chevron-right" size={16} color="var(--text-tertiary)" />}
                </button>
              );
            })}
          </div>
        )}
      </SectionFrame>
    </div>
  );
}
