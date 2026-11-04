// 많이 틀린 챕터의 암기노트로 가는 바로가기 카드.
// getConceptUnderstanding에서 정답률이 가장 낮은 챕터를 고르고, 데이터가 없으면 암기노트 목록으로 간다.
import Icon from '@/Icon';
import type { ChapterAccuracy } from '@/apis';
import type { AppState, RouteSetter } from '@/shared/types';

interface HomeWrongQuizCardProps {
  setRoute: RouteSetter;
  appState: AppState;
  /** 정답률이 가장 낮은 챕터. 없으면 일반 안내를 보여준다. */
  weakChapter: ChapterAccuracy | null;
}

function HomeWrongQuizCard({ setRoute, appState, weakChapter }: HomeWrongQuizCardProps) {
  const hasWeakChapter = weakChapter != null && weakChapter.totalQuizCount > 0;

  const handleClick = () => {
    if (hasWeakChapter) {
      appState.setSelectedChapterId(weakChapter.chapterId);
    }
    setRoute('notes');
  };

  return (
    <section className="sh-split-card sh-split-card--wrong-quiz" aria-label="많이 틀린 챕터 복습">
      <div className="sh-split-card__header">
        <span className="sh-split-card__title">
          <Icon name="brain" size={15} />
          {hasWeakChapter ? '많이 틀린 챕터 복습' : '암기노트로 복습하기'}
        </span>
      </div>

      {hasWeakChapter ? (
        <p className="sh-split-card__desc">
          <strong>{weakChapter.chapterTitle}</strong> 챕터 정답률이{' '}
          {Math.round(weakChapter.accuracyPercent)}%예요. 암기노트에서 핵심 개념을 다시 확인해보세요.
        </p>
      ) : (
        <p className="sh-split-card__desc">
          저장해둔 암기노트에서 핵심 개념을 다시 확인해보세요.
        </p>
      )}

      <button
        className="sh-split-card__cta"
        onClick={handleClick}
        title={hasWeakChapter ? `${weakChapter.chapterTitle} 암기노트로 이동` : '암기노트로 이동'}
      >
        암기노트로 이동
        <Icon name="arrowRight" size={14} />
      </button>
    </section>
  );
}

export default HomeWrongQuizCard;
