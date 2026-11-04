// 강의 생성 중인지 판단하는 훅.
// courseStatus·chapters로 슬라이드 생성 중 여부를 계산하고
// 첫 챕터가 준비되면 챕터 목록을 새로 고치는 콜백을 준다
import { useCallback } from 'react';
import { getChapters } from '@/apis';
import type { ChapterSummaryResponse, CourseStatus } from '@/apis';

interface ChapterState {
  courseId: string;
  chapters: ChapterSummaryResponse[];
  activeChapterId: string;
}

interface UseLessonGenerationGuardParams {
  courseId: string;
  courseStatus: CourseStatus | null;
  /** 현재 렌더에 쓰는 챕터 상태. courseId가 맞는 것만 넘긴다 */
  chapters: ChapterSummaryResponse[];
  chapterCourseId: string;
  setChapterState: (state: ChapterState) => void;
}

interface UseLessonGenerationGuardResult {
  /** true면 강의 슬라이드 생성 중이라 진행률 화면을 보여 준다 */
  isLessonGenerating: boolean;
  /** 첫 챕터 슬라이드가 준비되면 호출한다. 챕터 목록을 다시 불러 학습 화면으로 넘어간다 */
  handleFirstChapterReady: () => void;
}

/**
 * courseStatus === 'ACTIVE' 이면서 챕터 목록이 비어 있거나
 * 모든 챕터가 LOCKED 인 경우를 "생성 중" 상태로 판단한다.
 * slideCount는 커리큘럼의 예정 슬라이드 수일 수 있으므로 생성 완료 신호로 쓰지 않는다.
 */
export function useLessonGenerationGuard({
  courseId,
  courseStatus,
  chapters,
  chapterCourseId,
  setChapterState,
}: UseLessonGenerationGuardParams): UseLessonGenerationGuardResult {
  // courseStatus가 정해지고 챕터 목록이 현재 courseId 것일 때만 판단한다
  const isLessonGenerating =
    courseStatus === 'ACTIVE' &&
    chapterCourseId === courseId &&
    (
      chapters.length === 0 ||
      chapters.every(ch => ch.status === 'LOCKED')
    );

  // 첫 챕터 슬라이드가 준비되면 챕터 목록을 새로 고쳐 학습 화면으로 넘어간다
  const handleFirstChapterReady = useCallback(() => {
    if (!courseId) return;
    void getChapters(courseId).then((res) => {
      if (res.data) {
        const active = res.data.find(c => c.status === 'IN_PROGRESS')
          ?? res.data.find(c => c.status === 'AVAILABLE');
        setChapterState({
          courseId,
          chapters: res.data,
          activeChapterId: active?.id ?? '',
        });
      }
    }).catch((err) => { console.warn('[useLessonGenerationGuard] 챕터 목록 새로고침 실패', err); });
  }, [courseId, setChapterState]);

  return { isLessonGenerating, handleFirstChapterReady };
}
