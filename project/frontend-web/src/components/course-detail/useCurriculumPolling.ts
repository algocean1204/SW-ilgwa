// 커리큘럼 생성 폴링 훅.
// generating이 참인 동안 setInterval로 getCourse + getCurriculum을 반복 조회해
// 준비 완료, 실패, 타임아웃 중 하나로 넘긴다. cleanup에서 clearInterval한다.
// 챕터가 하나라도 생기면 hasReadyCurriculum이 참이 되어 바로 상세 화면을 연다.
// 그래서 생성 중 부분 챕터 진행 표시는 나올 일이 없다.
import { useCallback, useEffect, useRef } from 'react';
import { ApiError, getCourse } from '@/apis';
import type { CourseDetailResponse } from '@/apis';
import {
  CURRICULUM_POLL_INTERVAL_MS,
  CURRICULUM_POLL_TIMEOUT_MS,
  isCurriculumFailed,
  tryLoadCurriculum,
  loadCurriculum,
  hasReadyCurriculum,
} from './curriculumState';
import type { CurriculumSnapshot } from './curriculumState';

interface UseCurriculumPollingParams {
  courseId: string;
  generating: boolean;
  generationTimedOut: boolean;
  /** getCourse 응답에서 메타(제목·튜터 등)를 컨트롤러 상태에 반영 */
  applyCurrentCourseMeta: (course: CourseDetailResponse) => void;
  /** 커리큘럼 준비 완료 시 챕터·페이즈를 컨트롤러 상태에 반영하고 폴링 중단 */
  applyReadyCurriculum: (course: CourseDetailResponse, curriculum: CurriculumSnapshot) => void;
  setGenerating: (v: boolean) => void;
  setGenerationTimedOut: (v: boolean) => void;
  setGenerationNotice: (v: string | null) => void;
  setCurriculumFailed: (v: boolean) => void;
  setActiveCourseId: (id: string) => void;
  setLoadError: (v: string | null) => void;
}

/** curriculumState의 loadErrorMessage와 같은 내용을 폴링 훅에서 쓰려고 따로 둔다 */
function toPollingErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) {
    return '선택한 과외를 찾을 수 없습니다. 내 과외 목록을 다시 확인해 주세요.';
  }
  return '';
}

/**
 * generating === true인 동안 CURRICULUM_POLL_INTERVAL_MS 주기로 getCourse + getCurriculum을
 * 조회해 준비, 실패, 타임아웃 중 하나로 끝낸다. 컨트롤러가 멈출 수 있게 clearPoll을 돌려준다.
 */
export function useCurriculumPolling({
  courseId,
  generating,
  generationTimedOut,
  applyCurrentCourseMeta,
  applyReadyCurriculum,
  setGenerating,
  setGenerationTimedOut,
  setGenerationNotice,
  setCurriculumFailed,
  setActiveCourseId,
  setLoadError,
}: UseCurriculumPollingParams): { clearPoll: () => void } {
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const clearPoll = useCallback(() => {
    if (pollRef.current !== null) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  useEffect(() => {
    if (!courseId || !generating || generationTimedOut) {
      clearPoll();
      return clearPoll;
    }

    let mounted = true;
    const startedAt = Date.now();

    // 타임아웃이면 폴링을 멈춘다. 화면은 CurriculumGeneratingView에 남지만
    // "다시 확인 시작" 버튼과 목록 링크가 보여 생성중 화면에 갇히지 않는다.
    const stopAsTimedOut = () => {
      clearPoll();
      setGenerationTimedOut(true);
      setGenerationNotice('5분 넘게 완료 신호가 없습니다. 아래 "다시 확인 시작" 버튼을 누르거나 페이지를 새로고침해 주세요.');
    };

    const pollCourse = async () => {
      // 타임아웃 판정. 멈추기 전 마지막 조회 결과로 준비/실패 전환이 되면 그쪽을 먼저 탄다
      // (status가 CURRICULUM_GENERATING에 멈춰 있어도 챕터가 있으면 화면을 연다).
      const timedOut = Date.now() - startedAt > CURRICULUM_POLL_TIMEOUT_MS;

      try {
        const courseRes = await getCourse(courseId);
        if (!mounted) return;
        if (!courseRes.data) {
          if (timedOut) stopAsTimedOut();
          return;
        }
        const course = courseRes.data;
        applyCurrentCourseMeta(course);

        // CURRICULUM_FAILED면 폴링을 바로 멈추고 실패 안내로 넘긴다
        if (isCurriculumFailed(course.status)) {
          clearPoll();
          setGenerating(false);
          setCurriculumFailed(true);
          setGenerationNotice(null);
          return;
        }

        const curriculum = course.status === 'CURRICULUM_GENERATING'
          ? await tryLoadCurriculum(courseId)
          : await loadCurriculum(courseId);
        // 두 번째 await 뒤 다시 확인한다. 언마운트나 courseId 교체 뒤 온 늦은 응답이
        // 새 코스의 상태(applyReadyCurriculum/notice)를 덮어쓰지 않게 한다
        if (!mounted) return;

        if (course.status === 'CURRICULUM_GENERATING' && !hasReadyCurriculum(curriculum)) {
          // 5분이 지나도 준비된 커리큘럼이 없으면 생성중 화면에 머물지 않게 타임아웃 처리한다
          if (timedOut) {
            stopAsTimedOut();
            return;
          }
          setGenerationNotice('커리큘럼 생성 중입니다. 완료되면 자동으로 화면이 갱신됩니다.');
          return;
        }
        if (!curriculum) return;
        // 챕터가 있으면 status가 CURRICULUM_GENERATING이어도(상태 지연·불일치) 상세 화면을 연다
        applyReadyCurriculum(course, curriculum);
      } catch (error) {
        if (!mounted) return;
        if (error instanceof ApiError && error.status === 404) {
          clearPoll();
          setGenerating(false);
          setActiveCourseId('');
          setLoadError(toPollingErrorMessage(error));
          return;
        }
        // 오류가 5분 넘게 이어지면 무한 재시도하지 않고 타임아웃으로 끝낸다
        if (timedOut) {
          stopAsTimedOut();
          return;
        }
        setGenerationNotice('서버 응답을 다시 확인하고 있습니다. 잠시 뒤 자동으로 재시도합니다.');
      }
    };

    clearPoll();
    pollRef.current = setInterval(() => { void pollCourse(); }, CURRICULUM_POLL_INTERVAL_MS);

    return () => { mounted = false; clearPoll(); };
  }, [
    courseId, generating, generationTimedOut, clearPoll,
    applyCurrentCourseMeta, applyReadyCurriculum,
    setGenerating, setGenerationTimedOut, setGenerationNotice,
    setCurriculumFailed, setActiveCourseId, setLoadError,
  ]);

  return { clearPoll };
}
