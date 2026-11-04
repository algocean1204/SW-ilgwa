// 보완 필요 개념(warn)과 잘 이해한 개념(ok) 두 카드를 2열로 놓는다.
// API 분석 데이터가 없으면 빈 상태를 보여준다.
import type { Palette } from '@/lib';
import type { ConceptItem } from './types';
import type { SubjectProgress, ChapterAccuracy } from '@/apis';
import Icon from '@/Icon';

interface ConceptCardProps {
  title: string;
  subtitle: string;
  tone: 'warn' | 'ok';
  items: ConceptItem[];
  palette: Palette;
}

interface ConceptCardsProps {
  palette: Palette;
  subjects?: SubjectProgress[];
  // 챕터별 퀴즈 정답률. 있으면 진행률 대신 정답률로 개념 이해도를 나눈다.
  chapterAccuracies?: ChapterAccuracy[];
}

// 개념 카드 하나. tone이 warn이면 var(--coral-*) 토큰, ok면 성공 색을 쓴다.
function ConceptCard({ title, subtitle, tone, items, palette }: ConceptCardProps) {
  const warn = tone === 'warn';
  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
        <div style={{
          width: 30, height: 30, borderRadius: 8,
          background: warn ? 'var(--coral-wash)' : palette.wash,
          color: warn ? 'var(--coral-primary)' : palette.primary,
          display: 'grid', placeItems: 'center', fontSize: 14,
        }}>{warn ? <Icon name="flag" size={12} /> : <Icon name="check" size={12} />}</div>
        <div>
          <div style={{ fontSize: 13, fontWeight: 800, color: 'var(--ink)' }}>{title}</div>
          <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{subtitle}</div>
        </div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {items.map(it => (
          <div key={it.k} style={{ padding: '12px 14px', borderRadius: 10, background: 'var(--bg)', border: '1px solid var(--line-2)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink)', flex: 1 }}>{it.k}</div>
              <div style={{ fontSize: 12, fontWeight: 800, color: warn ? 'var(--coral-primary)' : palette.primary }}>{it.v}%</div>
            </div>
            {/* 진행 바. 그라데이션 없이 단색으로 그린다 */}
            <div style={{ marginTop: 8, height: 5, borderRadius: 3, background: 'var(--sand-2)', overflow: 'hidden' }}>
              <div style={{
                height: '100%', width: it.v + '%',
                background: warn ? 'var(--coral-primary)' : palette.primary,
              }} />
            </div>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 6 }}>{it.desc}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function toConceptItem(s: SubjectProgress): ConceptItem {
  return {
    k: s.subject,
    v: s.progressPercent,
    desc: `${s.completedChapters} / ${s.totalChapters} 챕터 완료`,
  };
}

function chapterToConceptItem(c: ChapterAccuracy): ConceptItem {
  return {
    k: c.chapterTitle,
    v: c.accuracyPercent,
    desc: `${c.correctCount}/${c.totalQuizCount} 문제 정답`,
  };
}

function ConceptCards({ palette, subjects, chapterAccuracies }: ConceptCardsProps) {
  // chapterAccuracies가 있으면 정답률로, 없으면 subjects(진행률)로 나눈다. 둘 다 없으면 빈 상태다.
  let warnItems: ConceptItem[];
  let okItems: ConceptItem[];
  let warnSubtitle: string;
  let okSubtitle: string;

  if (chapterAccuracies && chapterAccuracies.length > 0) {
    warnItems = chapterAccuracies.filter(c => c.accuracyPercent < 70).map(chapterToConceptItem);
    okItems = chapterAccuracies.filter(c => c.accuracyPercent >= 70).map(chapterToConceptItem);
    warnSubtitle = '정답률 70% 미만';
    okSubtitle = '정답률 70% 이상';
  } else if (subjects && subjects.length > 0) {
    warnItems = subjects.filter(s => s.progressPercent < 70).map(toConceptItem);
    okItems = subjects.filter(s => s.progressPercent >= 70).map(toConceptItem);
    warnSubtitle = '진행률 70% 미만';
    okSubtitle = '진행률 70% 이상';
  } else {
    return (
      <div style={{ padding: '36px 24px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
        아직 분석할 데이터가 없어요
      </div>
    );
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr' }}>
      <div style={{ borderRight: '1px solid var(--line-2)' }}>
        <ConceptCard
          title="보완이 필요한 개념"
          subtitle={warnSubtitle}
          tone="warn"
          palette={palette}
          items={warnItems}
        />
      </div>
      <ConceptCard
        title="잘 이해한 개념"
        subtitle={okSubtitle}
        tone="ok"
        palette={palette}
        items={okItems}
      />
    </div>
  );
}

export { ConceptCards };
