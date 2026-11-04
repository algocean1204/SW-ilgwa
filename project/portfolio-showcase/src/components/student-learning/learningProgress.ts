/**
 * completedSlides는 "완료한 슬라이드 수이자 다음 재개 인덱스"다.
 * 강의를 이미 완료한 경우 값이 totalSlides와 같아지므로, 다시 보기에서는 첫 슬라이드부터 연다.
 */
export function resolveResumeSlideIndex(completedSlides: number, totalSlides: number): number {
  if (totalSlides <= 0) return 0;
  if (completedSlides >= totalSlides) return 0;
  return Math.min(Math.max(completedSlides, 0), totalSlides - 1);
}
