import type { ChapterSummaryResponse } from '@/apis';

function findSelectableChapter(
  chapters: ChapterSummaryResponse[],
  chapterId: string,
): ChapterSummaryResponse | undefined {
  if (!chapterId) return undefined;
  return chapters.find(ch => ch.id === chapterId && ch.status !== 'LOCKED');
}

export function resolveActiveChapterId(
  chapters: ChapterSummaryResponse[],
  selectedChapterId: string,
  previousActiveChapterId: string,
): string {
  const selected = findSelectableChapter(chapters, selectedChapterId);
  if (selected) return selected.id;

  const previous = findSelectableChapter(chapters, previousActiveChapterId);
  if (previous) return previous.id;

  return chapters.find(ch => ch.status === 'IN_PROGRESS')?.id
    ?? chapters.find(ch => ch.status === 'AVAILABLE')?.id
    ?? '';
}
