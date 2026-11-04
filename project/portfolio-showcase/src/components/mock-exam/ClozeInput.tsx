// 빈칸 채우기 입력(ko_fill_blank). stem의 ___ 마커를 Input으로 바꾸거나 별도 입력란으로 둔다.
// 빈칸 수는 blankCount를 따르고 stem 파싱은 보조로만 쓴다.
import type { ClozeAnswer } from './mockExam.types';

interface ClozeInputProps {
  stem: string;
  blankCount: number;
  questionId: string;
  answer: ClozeAnswer | undefined;
  onChange: (answer: ClozeAnswer) => void;
}

/**
 * stem에서 ___ 마커를 분리해 [텍스트, 빈칸, 텍스트...] 배열로 반환한다.
 * blankCount와 파싱 결과가 다르면 blankCount를 기준으로 보정한다.
 */
function parseClozeStem(
  stem: string,
  blankCount: number,
): Array<{ type: 'text'; value: string } | { type: 'blank'; index: number }> {
  const MARKER = '___';
  const parts = stem.split(MARKER);
  // 파싱 결과와 달라도 빈칸 수는 blankCount를 따른다.
  const actualBlanks = Math.max(blankCount, parts.length - 1);
  const result: Array<
    { type: 'text'; value: string } | { type: 'blank'; index: number }
  > = [];

  for (let i = 0; i < parts.length; i++) {
    const text = parts[i];
    if (text !== undefined && text.length > 0) {
      result.push({ type: 'text', value: text });
    }
    // 마지막 부분 뒤에는 빈칸이 없다
    if (i < actualBlanks) {
      result.push({ type: 'blank', index: i });
    }
  }
  return result;
}

export default function ClozeInput({
  stem,
  blankCount,
  questionId,
  answer,
  onChange,
}: ClozeInputProps) {
  const blanks = answer?.blanks ?? Array.from({ length: blankCount }, () => '');
  const segments = parseClozeStem(stem, blankCount);

  const updateBlank = (idx: number, value: string) => {
    const next = [...blanks];
    next[idx] = value;
    onChange({ kind: 'cloze', blanks: next });
  };

  // stem에 ___ 마커가 없으면 별도 입력란을 늘어놓는다.
  const hasMarkers = segments.some((s) => s.type === 'blank');

  if (!hasMarkers) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {Array.from({ length: blankCount }, (_, idx) => (
          <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{
              flexShrink: 0, width: 20, height: 20, borderRadius: 10,
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 11, fontWeight: 800,
              background: 'var(--surface-subtle)', color: 'var(--text-tertiary)',
            }}>
              {idx + 1}
            </span>
            <input
              type="text"
              id={`cloze-${questionId}-${idx}`}
              value={blanks[idx] ?? ''}
              onChange={(e) => updateBlank(idx, e.target.value)}
              placeholder={`빈칸 ${idx + 1}`}
              aria-label={`빈칸 ${idx + 1} 입력`}
              style={{
                flex: 1, padding: '9px 12px', borderRadius: 8,
                fontSize: 13, fontWeight: 500, color: 'var(--text-primary)',
                background: 'var(--surface-page)',
                border: '1px solid var(--border-default)', outline: 'none',
              }}
            />
          </div>
        ))}
      </div>
    );
  }

  // 마커가 있으면 문장 안에 입력란을 넣는다.
  return (
    <div style={{ lineHeight: 2.2, fontSize: 14 }}>
      {segments.map((seg, i) => {
        if (seg.type === 'text') {
          return <span key={i} style={{ color: 'var(--text-primary)', fontWeight: 500 }}>{seg.value}</span>;
        }
        const idx = seg.index;
        return (
          <input
            key={i}
            type="text"
            id={`cloze-${questionId}-${idx}`}
            value={blanks[idx] ?? ''}
            onChange={(e) => updateBlank(idx, e.target.value)}
            placeholder={`(${idx + 1})`}
            aria-label={`빈칸 ${idx + 1} 입력`}
            style={{
              display: 'inline-block', width: 100, margin: '0 4px',
              padding: '4px 8px', borderRadius: 6,
              fontSize: 13, fontWeight: 600, color: 'var(--text-primary)',
              background: 'var(--surface-page)',
              border: '1px solid var(--border-default)',
              outline: 'none', verticalAlign: 'middle',
            }}
          />
        );
      })}
    </div>
  );
}
