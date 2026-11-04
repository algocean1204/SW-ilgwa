// 검색 입력과 주제 필터 pill 목록. 검색어와 주제 상태는 부모가 넘긴다.
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import type { NoteTopic } from '@/shared/types';

interface MemoryTopicFilterProps {
  palette: Palette;
  topics: NoteTopic[];
  topic: string;
  setTopic: (v: string) => void;
  query: string;
  setQuery: (v: string) => void;
}

export function MemoryTopicFilter({
  palette, topics, topic, setTopic, query, setQuery,
}: MemoryTopicFilterProps) {
  return (
    <>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 14 }}>
        <div style={{
          flex: 1, height: 40, background: 'var(--paper)',
          border: `1px solid ${query ? palette.soft : 'var(--line)'}`,
          borderRadius: 11, display: 'flex', alignItems: 'center',
          padding: '0 14px', gap: 8, transition: 'border-color .15s',
        }}>
          <Icon name="search" size={14} color="var(--ink-3)" />
          <input
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="주제, 제목, 내용으로 검색 (예: Self-Attention, CSRF)"
            style={{
              border: 'none', outline: 'none', flex: 1, fontSize: 13,
              fontFamily: 'inherit', color: 'var(--ink)', background: 'transparent',
            }}
          />
          {query && (
            <button
              onClick={() => setQuery('')}
              style={{ color: 'var(--ink-3)', display: 'grid', placeItems: 'center' }}
            >
              <Icon name="x" size={12} />
            </button>
          )}
          <span
            className="mono"
            style={{
              fontSize: 10, padding: '2px 6px', borderRadius: 4,
              background: 'var(--sand)', color: 'var(--ink-3)',
            }}
          >
            ⌘K
          </span>
        </div>
      </div>

      {/* 주제 필터 pill */}
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 18 }}>
        {topics.map(tp => {
          const active = topic === tp.id;
          return (
            <button
              key={tp.id}
              onClick={() => setTopic(tp.id)}
              className="lift"
              style={{
                padding: '7px 13px', borderRadius: 999,
                background: active ? palette.primary : 'var(--paper)',
                color: active ? 'var(--paper)' : 'var(--ink-2)',
                border: `1px solid ${active ? palette.primary : 'var(--line)'}`,
                fontSize: 12, fontWeight: active ? 700 : 600,
                display: 'flex', alignItems: 'center', gap: 6,
              }}
            >
              <span>{tp.label}</span>
              <span style={{
                fontSize: 10, fontWeight: 700, padding: '1px 6px', borderRadius: 999,
                background: active ? 'var(--featured-overlay-badge)' : 'var(--sand)',
                color: active ? 'var(--paper)' : 'var(--ink-3)',
              }}>
                {tp.count}
              </span>
            </button>
          );
        })}
      </div>
    </>
  );
}
