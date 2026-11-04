// 구문 강조와 복사 버튼이 있는 코드 블록
import { type ReactNode } from 'react';
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import { useCopy } from '@/lib';

interface CodeBlockProps {
  code: string;
  filename?: string;
  palette: Palette;
}

// 구문 강조 토큰 맵. 색은 --syn-* CSS 변수를 쓴다.
const SYNTAX_TOKEN_MAP: Record<string, string> = {
  KW:  'var(--syn-keyword)',
  FN:  'var(--syn-fn)',
  STR: 'var(--syn-string)',
  NUM: 'var(--syn-number)',
  CM:  'var(--syn-comment)',
};

/** Python 코드 줄을 구문 강조 JSX 배열로 변환 */
function highlightLine(line: string): ReactNode[] {
  const replaced = line
    .replace(/(import|def|return|print)/g, '§KW§$1§/§')
    .replace(/(np\.[a-z_]+)/g, '§FN§$1§/§')
    .replace(/("[^"]*"|f"[^"]*")/g, '§STR§$1§/§')
    .replace(/(\b\d+\.?\d*\b)/g, '§NUM§$1§/§')
    .replace(/(#.*$)/g, '§CM§$1§/§');
  const tokens = replaced.split(/(§[A-Z]+§|§\/§)/);
  const out: ReactNode[] = [];
  let current: string | null = null;
  tokens.forEach((tok, idx) => {
    if (tok.startsWith('§') && tok.endsWith('§') && tok !== '§/§') {
      current = tok.slice(1, -1);
      return;
    }
    if (tok === '§/§') { current = null; return; }
    // 매칭되지 않은 토큰은 --syn-default 색을 쓴다
    const color = (current ? SYNTAX_TOKEN_MAP[current] : undefined) ?? 'var(--syn-default)';
    out.push(<span key={idx} style={{ color }}>{tok}</span>);
  });
  return out;
}

/**
 * 코드 블록 컴포넌트.
 * - --code-bg: 어두운 배경
 * - --code-lineno: 줄 번호 희미한 회색
 * - --syn-*: 구문 강조 토큰
 */
export function CodeBlock({ code, filename = 'code.py', palette }: CodeBlockProps) {
  const [copied, copy] = useCopy();

  return (
    <div style={{
      borderRadius: 12,
      background: 'var(--code-bg)',
      padding: '10px 18px 16px',
      position: 'relative',
    }}>
      {/* 헤더: macOS 트래픽 라이트, 파일명, 복사 버튼 */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '6px 0 8px',
        borderBottom: '1px solid var(--code-overlay-faint)',
        marginBottom: 10,
      }}>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          {/* 트래픽 라이트 색은 coral-bright / honey / grass-3 토큰을 쓴다 */}
          <span style={{ width: 8, height: 8, borderRadius: 4, background: 'var(--coral-bright)' }} />
          <span style={{ width: 8, height: 8, borderRadius: 4, background: 'var(--honey)' }} />
          <span style={{ width: 8, height: 8, borderRadius: 4, background: 'var(--grass-3)' }} />
          {/* 파일명은 --syn-comment 색(어두운 배경 위 중간 회색)을 쓴다 */}
          <span className="mono" style={{ fontSize: 12, color: 'var(--syn-comment)', marginLeft: 8 }}>
            {filename}
          </span>
        </div>
        <button onClick={() => copy(code)} style={{
          padding: '3px 8px',
          borderRadius: 5,
          background: copied ? palette.primary2 : 'var(--code-overlay-faint)',
          color: 'var(--paper)',
          fontSize: 12,
          fontWeight: 500,
          transition: 'all .2s',
        }}>
          {copied ? <><Icon name="check" size={10} /> 복사됨</> : '복사'}
        </button>
      </div>

      <pre className="mono" style={{
        margin: 0,
        color: 'var(--syn-default)',
        fontSize: 12,
        lineHeight: 1.75,
        whiteSpace: 'pre-wrap',
      }}>
        {code.split('\n').map((line, i) => (
          <div key={i}>
            {/* 줄 번호는 --code-lineno 토큰을 쓴다 */}
            <span style={{
              color: 'var(--code-lineno)',
              display: 'inline-block',
              width: 26,
              userSelect: 'none',
            }}>
              {i + 1}
            </span>
            {highlightLine(line)}
          </div>
        ))}
      </pre>
    </div>
  );
}
