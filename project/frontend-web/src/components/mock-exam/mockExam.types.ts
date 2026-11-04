// 모의고사 응시 흐름에서 함께 쓰는 타입
import type {
  AttemptDetailResponse,
  ExamResultResponse,
  GenerateExamRequest,
  GenerationOutcomeMeta,
  MockExamListResponse,
} from '@/apis';

// 응시 단계: overview(개요) → generating(출제 중) → taking(응시) → result(결과)
export type ExamPhase = 'overview' | 'generating' | 'taking' | 'result';

// 유형별 답안 형태(kind로 구분하는 판별 유니온)

/** 객관식 (ko_multiple_choice_4/5). 보기 label 문자열 */
export interface MultipleChoiceAnswer {
  kind: 'multiple_choice';
  label: string; // "A", "B", "C" ...
}

/** OX 퀴즈 (ko_true_false). "O" 또는 "X" */
export interface TrueFalseAnswer {
  kind: 'true_false';
  label: 'O' | 'X';
}

/** 단답형 (ko_short_answer). 텍스트 */
export interface ShortAnswerAnswer {
  kind: 'short_answer';
  text: string;
}

/** 빈칸 채우기 (ko_fill_blank). blankCount 순서 배열 */
export interface ClozeAnswer {
  kind: 'cloze';
  blanks: string[]; // 길이 = blankCount
}

/** 서술형 (ko_descriptive). 텍스트 */
export interface EssayAnswer {
  kind: 'essay';
  text: string;
}

/** 순서 배열 (ko_ordering). 정렬된 항목 배열 */
export interface OrderingAnswer {
  kind: 'ordering';
  order: string[]; // orderingItems의 정렬 결과
}

/** 연결하기 (ko_matching). 좌측→우측 매핑 */
export interface MatchingAnswer {
  kind: 'matching';
  pairs: Record<string, string>; // { "좌측항목": "우측항목" }
}

export type ExamAnswer =
  | MultipleChoiceAnswer
  | TrueFalseAnswer
  | ShortAnswerAnswer
  | ClozeAnswer
  | EssayAnswer
  | OrderingAnswer
  | MatchingAnswer;

/** 답안 맵: questionId → ExamAnswer */
export type AnswerMap = Record<string, ExamAnswer>;

// templateId 문자열 → ExamAnswer.kind
export type ExamAnswerKind = ExamAnswer['kind'];

export function templateToKind(templateId: string): ExamAnswerKind {
  if (templateId.includes('multiple_choice')) return 'multiple_choice';
  if (templateId.includes('true_false')) return 'true_false';
  if (templateId.includes('short_answer')) return 'short_answer';
  if (templateId.includes('fill_blank')) return 'cloze';
  if (templateId.includes('ordering')) return 'ordering';
  if (templateId.includes('matching')) return 'matching';
  if (templateId.includes('descriptive')) return 'essay';
  // 알 수 없는 templateId는 단답으로 폴백
  return 'short_answer';
}

// useMockExam 반환 타입
export interface UseMockExamResult {
  phase: ExamPhase;
  examList: MockExamListResponse[];
  listLoading: boolean;
  attemptId: string | null;
  detail: AttemptDetailResponse | null;
  result: ExamResultResponse | null;
  error: string | null;
  /**
   * CON_004(미완강)이면 true. 강의를 먼저 끝내야 하므로 생성 버튼을 끄고 안내를 보인다.
   */
  courseNotCompleted: boolean;
  /**
   * 폴링에서 FAILED를 받으면 true. ExamGeneratingView가 5분 타임아웃을 기다리지 않고
   * 바로 오류 화면을 보인다.
   */
  isFailed: boolean;
  /**
   * status·결과 응답에서 받은 생성 메타(generationOutcome/requestedQuestionCount/
   * actualQuestionCount/generationFailureReason). 없으면 null이다.
   * generating에서 needs_more_source면 행동 제안 화면을, taking에서 partial이면
   * "N문항 생성됨" 배지를 보인다.
   */
  generationMeta: GenerationOutcomeMeta | null;
  startGenerate: (req: GenerateExamRequest) => Promise<void>;
  openAttempt: (attemptId: string) => Promise<void>;
  /** COMPLETED 시험의 제출 결과를 불러와 result 단계로 간다. */
  viewResult: (attemptId: string) => Promise<void>;
  finishExam: (answers: AnswerMap, timeTaken: number) => Promise<void>;
  reset: () => void;
}
