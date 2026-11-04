// CourseDetail의 API 조회와 편집 상태를 갖는 훅. 폴링은 useCurriculumPolling에 있다.
import { useCallback, useEffect, useState } from 'react';
import { ApiError, confirmCurriculum, getCourse, getMyTutors, updateTutoring } from '@/apis';
import type { CourseDetailResponse, CourseStatus } from '@/apis';
import type { Chapter } from './types';
import {
  applyCourseMeta,
  hasReadyCurriculum,
  isCurriculumFailed,
  loadCurriculum,
  loadErrorMessage,
  phaseFromStatus,
  tryLoadCurriculum,
  type CurriculumSnapshot,
  type CurriculumPhase,
} from './curriculumState';
import { refreshCourseDetailNow } from './courseDetailManualRefresh';
import { useCurriculumPolling } from './useCurriculumPolling';

interface UseCourseDetailControllerParams {
  courseId: string;
  setActiveCourseId: (id: string) => void;
}

export function useCourseDetailController({ courseId, setActiveCourseId }: UseCourseDetailControllerParams) {
  const [phase, setPhase] = useState<CurriculumPhase>('draft');
  // 코스 원본 상태(CourseStatus). 생성 진행 배너를 띄울지 판단하는 데 쓴다
  const [courseStatus, setCourseStatus] = useState<CourseStatus | undefined>(undefined);
  const [sourceType, setSourceType] = useState<string>('');
  const [title, setTitle] = useState(''); const [tutorId, setTutorId] = useState('');
  const [tutorName, setTutorName] = useState(''); const [tutorAvatarUrl, setTutorAvatarUrl] = useState('');
  // 코스에 튜터가 아직 없을 때(커리큘럼 DRAFT) 말투 미리보기에 쓸 기본 튜터
  const [fallbackTutor, setFallbackTutor] = useState<{ id: string; name: string; avatar: string } | null>(null);
  const [editingTitle, setEditingTitle] = useState(false); const [chapters, setChapters] = useState<Chapter[]>([]);
  const [loaded, setLoaded] = useState(false); const [loadedCourseId, setLoadedCourseId] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false); const [generationTimedOut, setGenerationTimedOut] = useState(false);
  const [generationNotice, setGenerationNotice] = useState<string | null>(null);
  // 커리큘럼 생성 실패(CURRICULUM_FAILED). 폴링을 멈추고 실패 안내 화면을 띄운다
  const [curriculumFailed, setCurriculumFailed] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  // 커리큘럼 확정 API 실패 시 보여줄 에러 메시지. null이면 정상이다
  const [confirmError, setConfirmError] = useState<string | null>(null);
  // 제목 저장 API 실패 시 보여줄 에러 메시지. null이면 정상이다
  const [titleSaveError, setTitleSaveError] = useState<string | null>(null);

  const applyCurrentCourseMeta = useCallback((course: CourseDetailResponse) => {
    // sourceType은 applyCourseMeta 내부 세터 목록과 무관하게 여기서 직접 반영한다
    setSourceType(course.sourceType ?? '');
    // 생성 배너 가시성 판단에 쓰도록 원본 코스 상태도 넣는다
    setCourseStatus(course.status);
    applyCourseMeta(course, { setTitle, setTutorId, setTutorName, setTutorAvatarUrl });
  }, []);

  const applyReadyCurriculum = useCallback((course: CourseDetailResponse, curriculum: CurriculumSnapshot) => {
    setGenerating(false);
    setGenerationTimedOut(false);
    setGenerationNotice(null);
    setPhase(phaseFromStatus(course.status, curriculum.status));
    setChapters(curriculum.chapters);
  }, []);

  // generating 동안 getCourse + getCurriculum 반복 조회는 폴링 훅이 한다
  const { clearPoll } = useCurriculumPolling({
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
  });

  useEffect(() => {
    if (!courseId) return;
    let mounted = true;

    void (async () => {
      try {
        const courseRes = await getCourse(courseId);
        if (!mounted || !courseRes.data) return;
        const course = courseRes.data;
        applyCurrentCourseMeta(course);
        setLoadError(null);

        // CURRICULUM_FAILED면 폴링하지 않고 바로 실패 상태로 바꾼다
        if (isCurriculumFailed(course.status)) {
          setCurriculumFailed(true);
          setGenerating(false);
          setChapters([]);
          return;
        }

        if (course.status === 'CURRICULUM_GENERATING') {
          const curriculum = await tryLoadCurriculum(courseId);
          if (!mounted) return;
          if (hasReadyCurriculum(curriculum)) {
            applyReadyCurriculum(course, curriculum);
            return;
          }
          setGenerating(true);
          setCurriculumFailed(false);
          setGenerationTimedOut(false);
          setGenerationNotice('완료 여부를 자동으로 확인하고 있습니다.');
          setPhase('draft');
          setChapters([]);
          return;
        }

        const curriculum = await loadCurriculum(courseId);
        if (!mounted) return;
        applyReadyCurriculum(course, curriculum);
      } catch (error) {
        if (!mounted) return;
        if (error instanceof ApiError && error.status === 404) setActiveCourseId('');
        setTitle('');
        setTutorId('');
        setChapters([]);
        setGenerating(false);
        setGenerationTimedOut(false);
        setGenerationNotice(null);
        clearPoll();
        setLoadError(loadErrorMessage(error));
      } finally {
        if (mounted) {
          setLoadedCourseId(courseId);
          setLoaded(true);
        }
      }
    })();

    return () => { mounted = false; clearPoll(); };
  }, [courseId, setActiveCourseId, clearPoll, applyCurrentCourseMeta, applyReadyCurriculum]);

  const handleManualPoll = useCallback(async () => {
    if (!courseId) return;
    await refreshCourseDetailNow({
      courseId, applyCurrentCourseMeta, applyReadyCurriculum,
      setGenerating, setGenerationNotice, setGenerationTimedOut, setCurriculumFailed,
    });
  }, [courseId, applyCurrentCourseMeta, applyReadyCurriculum]);

  const handleTitleSave = useCallback((newTitle: string) => {
    if (!courseId || newTitle === title) return;
    // 이전 에러를 지우고 저장한다. 실패는 삼키지 않고 상태에 남긴다
    setTitleSaveError(null);
    void updateTutoring(courseId, { courseName: newTitle }).catch((err: unknown) => {
      // 제목 저장 실패는 치명적이지 않아 warn으로 남기고, 상태를 바꿔 UI에 알린다
      console.warn('[CourseDetail] 제목 저장 실패', err);
      setTitleSaveError('제목 저장에 실패했습니다. 다시 시도해 주세요.');
    });
  }, [courseId, title]);

  const handleConfirm = useCallback(async () => {
    if (!courseId) return;
    setConfirmError(null);
    try {
      await confirmCurriculum(courseId);
      // API가 성공했을 때만 phase를 confirmed로 바꾼다.
      // 실패 후 phase를 올리면 성공으로 오인하므로 try 블록 안에서만 호출한다
      setPhase('confirmed');
      // 확정 직후 코스는 ACTIVE가 되므로 생성 배너가 바로 뜨게 미리 반영한다
      setCourseStatus('ACTIVE');
    } catch (err: unknown) {
      // 확정 API가 실패하면 phase를 그대로 두고 재시도 안내를 띄운다
      console.warn('[CourseDetail] 커리큘럼 확정 실패', err);
      setConfirmError('커리큘럼 확정에 실패했습니다. 다시 시도해 주세요.');
    }
  }, [courseId]);

  const removeChapter = useCallback((id: string) => {
    setChapters(prev => prev.filter(c => c.id !== id));
  }, []);

  const moveChapterUp = useCallback((id: string) => {
    setChapters(prev => {
      const idx = prev.findIndex(c => c.id === id);
      if (idx <= 0) return prev;
      const next = [...prev];
      // idx >= 1이면 두 원소가 모두 있다. 위 항목과 바꾼다
      const moved = next[idx]!;
      next[idx] = next[idx - 1]!;
      next[idx - 1] = moved;
      return next;
    });
  }, []);

  const moveChapterDown = useCallback((id: string) => {
    setChapters(prev => {
      const idx = prev.findIndex(c => c.id === id);
      if (idx < 0 || idx >= prev.length - 1) return prev;
      const next = [...prev];
      // idx <= length-2이면 두 원소가 모두 있다. 아래 항목과 바꾼다
      const moved = next[idx]!;
      next[idx] = next[idx + 1]!;
      next[idx + 1] = moved;
      return next;
    });
  }, []);

  const totalMin = chapters.reduce((a, c) => a + c.m, 0);
  const totalSlides = chapters.reduce((a, c) => a + c.s, 0);
  const readOnly = phase === 'confirmed';

  // 코스 튜터가 비어 있으면(DRAFT) 유저 기본 튜터를 불러와 말투 미리보기에 쓴다
  useEffect(() => {
    if (tutorId) return;
    let mounted = true;
    void getMyTutors()
      .then((res) => {
        if (!mounted) return;
        const tutors = res.data?.tutors ?? [];
        const t = tutors.find((x) => x.isDefault) ?? tutors[0];
        if (t) setFallbackTutor({ id: t.tutorId, name: t.name, avatar: t.profileImageUrl });
      })
      .catch(() => {});
    return () => {
      mounted = false;
    };
  }, [tutorId]);

  // 표시용 튜터명. 코스 튜터가 우선이고 없으면 기본 튜터를 쓴다(CourseHero에서 사용).
  const displayTutorName = tutorName || fallbackTutor?.name || '';

  return {
    phase, courseStatus, sourceType, title, setTitle, tutorId, tutorName, tutorAvatarUrl,
    displayTutorName, editingTitle,
    setEditingTitle, chapters, loaded, loadedCourseId, generating, generationTimedOut,
    generationNotice, curriculumFailed, loadError, totalMin, totalSlides, readOnly,
    confirmError,
    titleSaveError,
    removeChapter, moveChapterUp, moveChapterDown, handleManualPoll, handleTitleSave, handleConfirm,
  };
}
