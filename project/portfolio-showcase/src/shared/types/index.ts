// 도메인별 파일에 정의한 공용 타입을 다시 export한다.
export type {
  ApiEnvelope,
  CurriculumDto,
  CurriculumLessonDto,
  CurriculumStatus,
  LessonAssignmentDto,
  LessonGenerationStatus,
  LessonNoteDto,
  LessonQuizDto,
  LessonSlideDto,
  LessonSlidesDto,
} from './chapter-studio';

export type { Course, HomeworkStatus, Chapter, Bookmark } from './course';
export type {
  Tutor,
  TutorAvatarKind,
  PersonaBase,
} from './tutor';
export type {
  MemoNote,
  NoteKind,
  CompareRow,
  NoteTopic,
} from './memo-note';
export type {
  Assignment,
  AssignmentStatus,
  RubricItem,
} from './assignment';
export type { User, SubscriptionPlan } from './user';
export type { AppState, TweaksState } from './app-state';
export type { RouteKey, RouteSetter } from './route';
export type { PageContextFeature, PageContextEntry, PageContextFeatureType } from './page-context';
export { PAGE_CONTEXT_MANIFEST } from './page-context';
