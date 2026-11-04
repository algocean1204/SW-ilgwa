// 주제 입력 패널. textarea와 카테고리 칩으로 구성된다.
import type { Palette } from '@/lib';

// 카테고리는 입력 문장에 섞지 않고 별도 상태로만 저장한다
const TOPIC_CATEGORIES = ['자격증', '학교 시험', '언어', '개발', '수학', '취미'] as const;
export type TopicCategory = (typeof TOPIC_CATEGORIES)[number];

interface TopicInputPanelProps {
  topic: string;
  palette: Palette;
  selectedCategory: TopicCategory | null;
  onTopicChange: (v: string) => void;
  onCategoryChange: (v: TopicCategory | null) => void;
}

export function TopicInputPanel({
  topic, palette, selectedCategory, onTopicChange, onCategoryChange,
}: TopicInputPanelProps) {
  return (
    /* 표면 배경이라 var(--paper) 토큰을 쓴다 */
    <div style={{
      padding: '22px 24px', borderRadius: 16, background: 'var(--paper)',
      border: '1px solid var(--line)',
    }}>
      <div style={{ fontSize: 12, fontWeight: 800, color: 'var(--ink)', marginBottom: 10 }}>
        어떤 걸 공부하고 싶어요?
      </div>
      <textarea
        value={topic}
        onChange={e => onTopicChange(e.target.value)}
        placeholder="예) 중학교 3학년 이차함수, 기초부터 심화까지. 수포자 탈출이 목표."
        style={{
          width: '100%', minHeight: 120, resize: 'none',
          padding: '14px 16px', borderRadius: 12, border: '1.5px solid var(--line)',
          fontSize: 14, color: 'var(--ink)', background: 'var(--paper)',
          fontFamily: 'inherit', lineHeight: 1.6, outline: 'none',
        }}
        onFocus={e => (e.target.style.borderColor = palette.primary)}
        onBlur={e => (e.target.style.borderColor = 'var(--line)')}
      />
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6, fontSize: 12, color: 'var(--ink-3)' }}>
        <span>한 줄만 써도 단계와 분량을 맞춰 구체화합니다</span>
        <span>{topic.length} / 2,000</span>
      </div>
      {/* 카테고리 칩. topic 문자열은 사용자 입력 그대로 둔다 */}
      <div style={{ display: 'flex', gap: 6, marginTop: 12, flexWrap: 'wrap' }}>
        {TOPIC_CATEGORIES.map(category => {
          const selected = selectedCategory === category;
          return (
          <button
            key={category}
            type="button"
            // "카테고리 수학" 형태로 카테고리 버튼과 튜터 버튼의 accessible name이 겹치지 않게 구분한다
            aria-label={`카테고리 ${category}`}
            aria-pressed={selected}
            onClick={() => onCategoryChange(selected ? null : category)}
            style={{
              fontSize: 12, padding: '5px 10px', borderRadius: 999,
              background: selected ? palette.wash : 'var(--bg)',
              border: selected ? `1.5px solid ${palette.primary}` : '1px solid var(--line)',
              color: selected ? palette.primary : 'var(--ink-2)', fontWeight: 600,
            }}
            className="lift"
          >
            {category}
          </button>
          );
        })}
      </div>
    </div>
  );
}
