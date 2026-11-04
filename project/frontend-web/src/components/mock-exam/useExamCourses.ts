// 모의고사용 과외 목록과 선택 상태. MyHomework처럼 getMyCourses로 불러온다.
import { useEffect, useState } from 'react';
import { getMyCourses } from '@/apis';
import type { CourseSummaryResponse } from '@/apis';
import type { AppState } from '@/shared/types';

interface UseExamCoursesResult {
  courses: CourseSummaryResponse[];
  selectedCourseId: string;
  setSelectedCourseId: (id: string) => void;
}

/**
 * 강의가 하나 이상(totalLessons > 0)인 과외만 고를 수 있다.
 * 선택 초기값은 appState.activeCourseId, 없으면 첫 번째 과외다.
 */
export function useExamCourses(appState: AppState): UseExamCoursesResult {
  const [courses, setCourses] = useState<CourseSummaryResponse[]>([]);
  // 빈 문자열이면 사용자가 아직 고르지 않은 상태다.
  const [picked, setPicked] = useState<string>('');

  // 과외를 최대 50개 불러온다. 인증 부트스트랩 전(authReady=false)에 부르면 401이 나서 기다린다.
  useEffect(() => {
    if (!appState.authReady) return;
    void getMyCourses({ size: 50 })
      .then((res) => {
        if (res.data?.content) {
          // 강의가 없는 과외(커리큘럼 생성 중 등)는 출제할 수 없어 뺀다.
          const eligible = res.data.content.filter((c) => c.totalLessons > 0);
          setCourses(eligible);
        }
      })
      .catch(() => {
        // 실패하면 빈 목록으로 두고 화면은 "과외 없음" 안내를 보인다.
      });
  }, [appState.authReady]);

  // 사용자 선택 → appState.activeCourseId → 첫 번째 과외 순으로 고른다.
  const selectedCourseId = picked || appState.activeCourseId || courses[0]?.id || '';

  return { courses, selectedCourseId, setSelectedCourseId: setPicked };
}
