// 제출 대기(active) 과제 상세와 답안 제출 패널
import { useState, useMemo } from 'react';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import type { Palette } from '@/lib';
import type { AssignmentDetailResponse } from '@/apis';
import { submitAssignment, ApiError } from '@/apis';
import Icon from '@/Icon';

interface MarkdownEditorProps {
  answerKey: string;
  value: string;
  tab: 'write' | 'preview';
  onChange: (v: string) => void;
  onTabChange: (t: 'write' | 'preview') => void;
  palette: Palette;
  rows: number;
}

function MarkdownEditor({ value, tab, onChange, onTabChange, palette, rows }: MarkdownEditorProps) {
  const previewHtml = useMemo(() => {
    if (tab !== 'preview') return '';
    return DOMPurify.sanitize(marked.parse(value || '_아직 작성된 내용이 없어요._') as string);
  }, [tab, value]);

  return (
    <div style={{ border: '1px solid var(--line)', borderRadius: 10, overflow: 'hidden' }}>
      {/* 탭 헤더 */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 0,
        borderBottom: '1px solid var(--line)', background: 'var(--bg)',
      }}>
        {(['write', 'preview'] as const).map(t => (
          <button
            key={t}
            onClick={() => onTabChange(t)}
            style={{
              padding: '7px 14px', fontSize: 11, fontWeight: 700, border: 'none',
              background: tab === t ? 'var(--paper)' : 'transparent',
              color: tab === t ? palette.primary : 'var(--ink-3)',
              borderBottom: tab === t ? `2px solid ${palette.primary}` : '2px solid transparent',
              cursor: 'pointer',
            }}
          >
            {t === 'write' ? '작성' : '미리보기'}
          </button>
        ))}
        <span style={{ marginLeft: 'auto', paddingRight: 10, fontSize: 10, color: 'var(--ink-3)' }}>
          Markdown 지원
        </span>
      </div>

      {/* 작성 탭 */}
      {tab === 'write' && (
        <textarea
          value={value}
          onChange={e => onChange(e.target.value)}
          placeholder={`답안을 Markdown으로 작성하세요\n\n예) **굵게**, # 제목, - 항목`}
          rows={rows}
          style={{
            width: '100%', boxSizing: 'border-box', display: 'block',
            padding: '12px 14px', resize: 'vertical', border: 'none',
            background: 'var(--paper)', fontSize: 13, color: 'var(--ink)',
            lineHeight: 1.7, outline: 'none', fontFamily: 'monospace',
          }}
        />
      )}

      {/* 미리보기 탭 */}
      {tab === 'preview' && (
        <div
          className="md-preview"
          dangerouslySetInnerHTML={{ __html: previewHtml }}
          style={{
            padding: '12px 16px', minHeight: rows * 24,
            background: 'var(--paper)', fontSize: 13, color: 'var(--ink)', lineHeight: 1.8,
          }}
        />
      )}
    </div>
  );
}

interface AssignmentSubmitPanelProps {
  courseId: string;
  detail: AssignmentDetailResponse;
  palette: Palette;
  onSubmitSuccess: () => void;
}

/** ISO 날짜 문자열을 "6월 17일 (화) · 3일 후" 형식으로 변환 */
function formatDueDate(raw: string): string {
  const d = new Date(raw);
  if (isNaN(d.getTime())) return raw;
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  const month = d.getMonth() + 1;
  const date = d.getDate();
  const day = days[d.getDay()];
  const diffMs = d.getTime() - Date.now();
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
  const rel = diffDays < 0 ? `${Math.abs(diffDays)}일 지남` : diffDays === 0 ? '오늘 마감' : `${diffDays}일 후`;
  return `${month}월 ${date}일 (${day}) · ${rel}`;
}

/** description 텍스트를 섹션 배열로 파싱
 *
 * \n이 있으면 줄 단위로 분리하고,
 * \n이 없으면(FastAPI가 연속 문자열로 저장한 경우) 한국어 섹션 키워드·번호 앞에서 강제 분리한다.
 */
function parseDescriptionSections(text: string): { heading: boolean; text: string }[] {
  // 콜론 유무 관계없이 섹션 키워드 앞에서 분리
  const SECTION_RE = /(형식|예상\s*시간|절차|평가\s*기준|제출\s*방식|주의\s*사항|참고|목표|배경|조건)\s*[:：]?(?=\s)/g;
  const NUMBERED_RE = /(?<!\d)([1-9]\d*)\.\s+(?=[^\s])/g;
  const BULLET_RE = /\s[-•]\s/g;

  // \n이 이미 있으면 줄 단위 처리
  const hasNewlines = text.includes('\n');
  let lines: string[];

  if (hasNewlines) {
    lines = text.split('\n');
  } else {
    // 섹션 키워드·번호 앞에 \n 삽입 후 split
    const normalized = text
      .replace(SECTION_RE, '\n$&')
      .replace(NUMBERED_RE, '\n$1. ')
      .replace(BULLET_RE, '\n- ');
    lines = normalized.split('\n');
  }

  return lines
    .map(l => l.trim())
    .filter(Boolean)
    .map(t => ({
      heading:
        /^(형식|예상\s*시간|절차|평가\s*기준|제출\s*방식|주의\s*사항|참고|목표|배경|조건)[:：]/.test(t) ||
        /^[0-9]+\.\s/.test(t) ||
        /^[-•]\s/.test(t),
      text: t,
    }));
}

export default function AssignmentSubmitPanel({
  courseId,
  detail,
  palette,
  onSubmitSuccess,
}: AssignmentSubmitPanelProps) {
  const [answers, setAnswers] = useState<Record<string, string>>(() => {
    if (detail.previousAnswer && detail.questions.length > 0) {
      return { [String(detail.questions[0]!.questionId)]: detail.previousAnswer };
    }
    // 문항 없을 때 단일 자유 답안 키
    if (detail.previousAnswer) return { free: detail.previousAnswer };
    return {};
  });
  const [submitting, setSubmitting] = useState(false);
  // 버튼 잠금만으로는 이유가 안 보여, 제출한 과제에 다시 들어오면 잠금 사유를 안내한다
  const [resultMsg, setResultMsg] = useState<{ ok: boolean; text: string } | null>(
    detail.previousAnswer ? { ok: true, text: '이미 제출한 과제예요. 채점 결과를 기다려주세요' } : null,
  );
  // 제출 완료(또는 이미 제출됨) 시 재제출을 막고 "채점 중" 상태를 노출한다.
  // 초기값을 previousAnswer 존재 여부로 둬 재진입(컴포넌트 재마운트)해도 잠금이 유지된다.
  // 이 패널은 active(채점 전) 과제에서만 보이므로 previousAnswer 있음 = 제출 후 채점 중이다.
  const [submitted, setSubmitted] = useState(() => !!detail.previousAnswer);
  // 작성/미리보기 탭은 문항마다 따로 관리한다
  const [previewTabs, setPreviewTabs] = useState<Record<string, 'write' | 'preview'>>({});
  const getTab = (key: string) => previewTabs[key] ?? 'write';
  const setTab = (key: string, tab: 'write' | 'preview') =>
    setPreviewTabs(prev => ({ ...prev, [key]: tab }));

  const hasQuestions = detail.questions.length > 0;
  // description 첫 토큰이 title과 같으면 중복 제거 (FastAPI가 seed에 제목 포함해서 저장)
  const cleanDesc = detail.description?.startsWith(detail.title)
    ? detail.description.slice(detail.title.length).trimStart()
    : detail.description;
  const sections = cleanDesc ? parseDescriptionSections(cleanDesc) : [];

  const handleAnswerChange = (key: string, value: string) => {
    setAnswers(prev => ({ ...prev, [key]: value }));
  };

  const handleSubmit = async () => {
    if (hasQuestions) {
      const missing = detail.questions.some(q => !answers[String(q.questionId)]?.trim());
      if (missing) { setResultMsg({ ok: false, text: '모든 문항에 답안을 입력해주세요' }); return; }
    } else {
      if (!answers['free']?.trim()) { setResultMsg({ ok: false, text: '답안을 입력해주세요' }); return; }
    }
    setSubmitting(true);
    setResultMsg(null);
    try {
      const body = hasQuestions
        ? { answers: detail.questions.map(q => ({ questionId: q.questionId, answer: answers[String(q.questionId)] ?? '' })) }
        : { answers: [{ questionId: 1, answer: answers['free'] ?? '' }] };
      const res = await submitAssignment(courseId, detail.id, body);
      if (res.data) {
        setResultMsg({ ok: true, text: '제출 완료! 채점 결과를 기다려주세요' });
        setSubmitted(true);
        onSubmitSuccess();
      } else {
        setResultMsg({ ok: false, text: '제출 중 오류가 발생했어요. 다시 시도해주세요' });
      }
    } catch (e) {
      // 이미 제출된 과제(409 ASG_003)는 "제출됨"으로 안내한다
      if (e instanceof ApiError && e.status === 409) {
        setResultMsg({ ok: true, text: '이미 제출한 과제예요. 채점 결과를 기다려주세요' });
        setSubmitted(true);
        onSubmitSuccess();
      } else {
        setResultMsg({ ok: false, text: '제출 중 오류가 발생했어요. 다시 시도해주세요' });
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {/* 헤더 */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
          <div style={{
            width: 28, height: 28, borderRadius: 8,
            background: palette.wash, display: 'grid', placeItems: 'center', flexShrink: 0,
          }}>
            <Icon name="edit" size={13} color={palette.primary} />
          </div>
          <h2 style={{ margin: 0, fontSize: 17, fontWeight: 800, letterSpacing: '-.02em', color: 'var(--ink)', lineHeight: 1.3 }}>
            {detail.title}
          </h2>
        </div>
        {detail.dueDate && (
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 4,
            fontSize: 11, fontWeight: 700, color: 'var(--coral-primary)',
            background: 'var(--coral-bg)', padding: '3px 8px', borderRadius: 6,
          }}>
            <Icon name="clock" size={10} color="var(--coral-primary)" />
            마감 {formatDueDate(detail.dueDate)}
          </span>
        )}
      </div>

      {/* 과제 내용 */}
      {sections.length > 0 && (
        <div style={{
          background: 'var(--bg)', borderRadius: 12, border: '1px solid var(--line)',
          padding: '16px 18px', marginBottom: 20,
        }}>
          <div style={{
            fontSize: 10, fontWeight: 700, color: 'var(--ink-3)',
            letterSpacing: '.08em', textTransform: 'uppercase', marginBottom: 12,
          }}>
            과제 안내
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
            {sections.map((s, i) => {
              const isBullet = /^[-•]\s/.test(s.text);
              const isNumbered = /^[0-9]+\.\s/.test(s.text);
              const isSection = !isBullet && !isNumbered && s.heading;
              return (
                <div key={i} style={{
                  display: 'flex', gap: 8, alignItems: 'flex-start',
                  paddingTop: isSection && i > 0 ? 8 : 0,
                }}>
                  {isBullet && (
                    <span style={{ color: palette.primary, flexShrink: 0, marginTop: 1, fontSize: 12 }}>•</span>
                  )}
                  {isNumbered && (
                    <span style={{
                      flexShrink: 0, width: 20, height: 20, borderRadius: '50%',
                      background: palette.wash, color: palette.primary,
                      fontSize: 10, fontWeight: 800, display: 'grid', placeItems: 'center', marginTop: 1,
                    }}>
                      {s.text.match(/^([0-9]+)/)?.[1]}
                    </span>
                  )}
                  <span style={{
                    fontSize: isSection ? 12 : 12.5,
                    fontWeight: isSection ? 700 : 500,
                    color: isSection ? palette.primary : 'var(--ink-2)',
                    lineHeight: 1.65,
                    flex: 1,
                  }}>
                    {isNumbered ? s.text.replace(/^[0-9]+\.\s/, '') : isBullet ? s.text.replace(/^[-•]\s/, '') : s.text}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 답안 입력 */}
      <div style={{ marginBottom: 16 }}>
        <div style={{
          fontSize: 12, fontWeight: 700, color: 'var(--ink-2)',
          marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6,
        }}>
          <span style={{
            width: 18, height: 18, borderRadius: 5,
            background: palette.primary, color: 'var(--paper)',
            fontSize: 10, fontWeight: 800, display: 'grid', placeItems: 'center',
          }}>A</span>
          내 답안
        </div>

        {hasQuestions ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {detail.questions.map((q, idx) => {
              const key = String(q.questionId);
              const tab = getTab(key);
              return (
                <div key={q.questionId}>
                  <div style={{
                    fontSize: 12.5, fontWeight: 700, color: 'var(--ink)', marginBottom: 6,
                    display: 'flex', gap: 6, alignItems: 'flex-start',
                  }}>
                    <span style={{
                      flexShrink: 0, width: 20, height: 20, borderRadius: '50%',
                      background: palette.wash, color: palette.primary,
                      fontSize: 10, fontWeight: 800, display: 'grid', placeItems: 'center',
                    }}>
                      {idx + 1}
                    </span>
                    {q.content}
                  </div>
                  <MarkdownEditor
                    answerKey={key}
                    value={answers[key] ?? ''}
                    tab={tab}
                    onChange={v => handleAnswerChange(key, v)}
                    onTabChange={t => setTab(key, t)}
                    palette={palette}
                    rows={4}
                  />
                </div>
              );
            })}
          </div>
        ) : (
          <MarkdownEditor
            answerKey="free"
            value={answers['free'] ?? ''}
            tab={getTab('free')}
            onChange={v => handleAnswerChange('free', v)}
            onTabChange={t => setTab('free', t)}
            palette={palette}
            rows={6}
          />
        )}
      </div>

      {/* 결과 메시지 */}
      {resultMsg && (
        <div style={{
          marginBottom: 14, padding: '10px 14px', borderRadius: 10, fontSize: 13, fontWeight: 600,
          background: resultMsg.ok ? palette.wash : 'var(--coral-bg)',
          color: resultMsg.ok ? palette.primary : 'var(--coral-primary)',
          border: `1px solid ${resultMsg.ok ? palette.soft : 'var(--coral-soft)'}`,
        }}>
          {resultMsg.text}
        </div>
      )}

      <div style={{ paddingTop: 14, borderTop: '1px solid var(--line)' }}>
        <button
          className="lift"
          onClick={() => void handleSubmit()}
          disabled={submitting || submitted}
          style={{
            padding: '11px 22px', borderRadius: 10, fontSize: 13, fontWeight: 800,
            background: submitting || submitted ? 'var(--ink-3)' : palette.primary,
            color: 'var(--surface)',
            boxShadow: submitting || submitted ? 'none' : `0 8px 18px -10px ${palette.primary}`,
            cursor: submitting || submitted ? 'not-allowed' : 'pointer',
          }}
        >
          {submitted ? '제출 완료 · 채점 중' : submitting ? '제출 중...' : '답안 제출'}
        </button>
      </div>
    </div>
  );
}
