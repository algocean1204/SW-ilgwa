// 새 과외 생성 요청을 만들고 오류를 화면 메시지로 바꾼다
import { ApiError, createTutoring, uploadTutoringSourcePdf } from '@/apis';
import type { AppState, RouteSetter } from '@/shared/types';
import type { CourseExploreMode } from './ModeToggle';
import type { TopicCategory } from './TopicInputPanel';
import type { TeacherItem } from './types';

interface SubmitCourseExploreParams {
  mode: CourseExploreMode;
  topic: string;
  selectedCategory: TopicCategory | null;
  pdfFile: File | null;
  duration: number;
  depth: string;
  /** 사용자가 고른 강의(챕터) 수. AI 커리큘럼 생성 요청에 lessonCount로 넘긴다 */
  lessonCount: number;
  teacher: string;
  teachers: TeacherItem[];
  appState: AppState;
  setRoute: RouteSetter;
  setSubmitError: (message: string | null) => void;
}

function createErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0) {
      return '서버에 연결하지 못했습니다. 백엔드 실행 상태를 확인한 뒤 다시 시도해 주세요.';
    }
    if (error.status === 502) {
      return '커리큘럼 생성 서버 연결에 실패해 과외를 저장하지 못했습니다. 잠시 뒤 다시 시도해 주세요.';
    }
    return error.errorResponse?.message ?? '과외를 생성하지 못했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.';
  }
  return '과외를 생성하지 못했습니다. 잠시 뒤 다시 시도해 주세요.';
}

export async function submitCourseExplore({
  mode, topic, selectedCategory, pdfFile, duration, depth,
  lessonCount,
  teacher, teachers, appState, setRoute, setSubmitError,
}: SubmitCourseExploreParams): Promise<void> {
  try {
    const trimmedTopic = topic.trim();
    if (mode === 'pdf' && !pdfFile) {
      setSubmitError('PDF 파일을 선택해 주세요.');
      return;
    }
    if (mode === 'topic' && !trimmedTopic) {
      setSubmitError('과외 주제를 입력해 주세요.');
      return;
    }

    const selectedTutorId = teachers.find(t => t.id === teacher)?.id;
    if (!selectedTutorId) {
      setSubmitError('실제 튜터 ID를 불러온 뒤 다시 시도해 주세요.');
      return;
    }
    // subject는 백엔드 varchar(100) 컬럼이자 목록 라벨이다. 카테고리가 없을 때 주제 전문이
    // 들어가면 100자를 넘어 생성이 실패하므로 60자로 자른다(전체 주제는 topic으로 보낸다).
    const rawSubject = mode === 'topic'
      ? selectedCategory ?? trimmedTopic
      : pdfFile?.name ?? '일반';
    const subject = rawSubject.length > 60 ? rawSubject.slice(0, 60) : rawSubject;
    const pdfUpload = mode === 'pdf' && pdfFile
      ? await uploadTutoringSourcePdf(pdfFile)
      : null;
    const pdfUrl = pdfUpload?.data?.url;
    if (mode === 'pdf' && !pdfUrl) {
      setSubmitError('PDF 업로드에 실패했습니다. 다시 시도해 주세요.');
      return;
    }

    const res = await createTutoring({
      // title→course_name은 varchar(200)이라 200자로 자른다(전체는 topic으로 보낸다)
      title: (trimmedTopic || pdfFile?.name || '새 과외').slice(0, 200),
      subject,
      sourceType: mode,
      sourceRef: mode === 'pdf' ? pdfUrl : undefined,
      topic: mode === 'topic' ? trimmedTopic : undefined,
      durationDays: duration,
      difficulty: depth,
      tutorId: selectedTutorId,
      // AI가 이 강의 수에 맞춰 챕터를 구성한다
      lessonCount,
    });
    if (res.data) {
      appState.setActiveCourseId(res.data.id);
      appState.setHasCourses(true);
      setRoute('courseDetail');
    }
  } catch (error) {
    setSubmitError(createErrorMessage(error));
  }
}
