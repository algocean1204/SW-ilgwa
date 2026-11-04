// 모의고사 생성 옵션 폼. 문항수·난이도·시험유형·제한시간·집중주제를 GenerateExamRequest로 묶는다.
// 생성권이 부족하면 생성 요청 없이 onNeedCoins()로 충전 페이지로 보낸다.
import { useState } from 'react';
import { ShimmerButton } from '@/components/animated';
import type { Palette } from '@/lib';
import type { GenerateExamRequest } from '@/apis';
import {
  DEFAULT_QUESTION_COUNT, DEFAULT_TIME_LIMIT,
  DIFFICULTY_OPTIONS, EXAM_TYPE_OPTIONS, QUESTION_COUNT_OPTIONS,
} from './examOptions';

interface ExamGenerateOptionsProps {
  palette: Palette;
  coins: number;
  cost: number;
  onGenerate: (req: GenerateExamRequest) => void;
  onCancel: () => void;
  onNeedCoins: () => void;
}

export default function ExamGenerateOptions({
  palette, coins, cost, onGenerate, onCancel, onNeedCoins,
}: ExamGenerateOptionsProps) {
  const [questionCount, setQuestionCount] = useState<number>(DEFAULT_QUESTION_COUNT);
  const [difficulty, setDifficulty] = useState<string>('medium');
  const [examType, setExamType] = useState<string>(EXAM_TYPE_OPTIONS[0]!.value);
  // 집중 주제는 쉼표로 구분해 입력한다.
  const [topicsRaw, setTopicsRaw] = useState<string>('');

  const insufficient = coins < cost;

  const handleGenerate = () => {
    const focusTopics = topicsRaw
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
    onGenerate({
      examType,
      questionCount,
      difficulty,
      timeLimit: DEFAULT_TIME_LIMIT,
      // 빈 배열은 보내지 않는다.
      ...(focusTopics.length > 0 ? { focusTopics } : {}),
    });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <Field label="문항 수">
        <div style={{ display: 'flex', gap: 8 }}>
          {QUESTION_COUNT_OPTIONS.map((n) => (
            <Choice key={n} active={questionCount === n} palette={palette} onClick={() => setQuestionCount(n)}>
              {n}문항
            </Choice>
          ))}
        </div>
      </Field>

      <Field label="난이도">
        <div style={{ display: 'flex', gap: 8 }}>
          {DIFFICULTY_OPTIONS.map((d) => (
            <Choice key={d.value} active={difficulty === d.value} palette={palette} onClick={() => setDifficulty(d.value)} stacked>
              <span style={{ fontSize: 13, fontWeight: 800 }}>{d.label}</span>
              <span style={{ fontSize: 10, color: 'var(--text-tertiary)', marginTop: 2 }}>{d.desc}</span>
            </Choice>
          ))}
        </div>
      </Field>

      <Field label="시험 유형">
        <div style={{ display: 'flex', gap: 8 }}>
          {EXAM_TYPE_OPTIONS.map((e) => (
            <Choice key={e.value} active={examType === e.value} palette={palette} onClick={() => setExamType(e.value)}>
              {e.label}
            </Choice>
          ))}
        </div>
      </Field>

      <Field label="집중 주제 (선택)">
        <input
          value={topicsRaw}
          onChange={(e) => setTopicsRaw(e.target.value)}
          placeholder="예: 정규화, 트랜잭션 (쉼표로 구분)"
          style={{
            width: '100%', boxSizing: 'border-box',
            padding: '10px 12px', borderRadius: 10, fontSize: 13,
            background: 'var(--surface-page)', border: '1px solid var(--border-soft)',
            color: 'var(--text-primary)', outline: 'none',
          }}
        />
      </Field>

      {/* 제한 시간은 60분 고정이라 안내만 한다 */}
      <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
        제한 시간 {DEFAULT_TIME_LIMIT}분 · 생성권 {cost}개 소모
      </div>

      <div style={{ display: 'flex', gap: 8 }}>
        <button
          onClick={onCancel}
          style={{
            padding: '12px 18px', borderRadius: 10, fontSize: 13, fontWeight: 700,
            background: 'var(--surface-card)', border: '1px solid var(--border-default)',
            color: 'var(--text-secondary)', cursor: 'pointer',
          }}
        >
          취소
        </button>
        {/* 생성권이 부족하면 요청을 보내지 않고 충전 페이지로 보낸다 */}
        {insufficient ? (
          <ShimmerButton
            onClick={onNeedCoins}
            className="lift"
            style={{
              flex: 1, padding: '12px 18px', borderRadius: 10,
              background: 'var(--ink)',
              color: 'var(--paper)', fontSize: 13, fontWeight: 800,
              boxShadow: 'var(--shadow-md)',
            }}
          >
            생성권 부족 · 구매하러 가기
          </ShimmerButton>
        ) : (
          <ShimmerButton
            onClick={handleGenerate}
            className="lift"
            style={{
              flex: 1, padding: '12px 18px', borderRadius: 10,
              background: palette.primary,
              color: 'var(--paper)', fontSize: 13, fontWeight: 800,
              boxShadow: 'var(--shadow-md)',
            }}
          >
            모의고사 생성하기
          </ShimmerButton>
        )}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-tertiary)', letterSpacing: '.04em', marginBottom: 8 }}>
        {label}
      </div>
      {children}
    </div>
  );
}

function Choice({
  active, palette, onClick, children, stacked = false,
}: {
  active: boolean; palette: Palette; onClick: () => void; children: React.ReactNode; stacked?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      className="lift"
      style={{
        flex: 1, padding: '10px 12px', borderRadius: 10, cursor: 'pointer',
        display: 'flex', flexDirection: stacked ? 'column' : 'row',
        alignItems: 'center', justifyContent: 'center',
        background: active ? palette.wash : 'var(--surface-page)',
        border: active ? `2px solid ${palette.primary}` : '1px solid var(--border-soft)',
        color: active ? palette.primary : 'var(--text-secondary)',
        fontSize: 13, fontWeight: active ? 800 : 600,
      }}
    >
      {children}
    </button>
  );
}
