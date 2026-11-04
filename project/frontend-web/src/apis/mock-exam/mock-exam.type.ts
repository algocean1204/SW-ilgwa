// examforge API 계약 타입.
// templateId 목록: ko_multiple_choice_4/5, ko_true_false, ko_short_answer,
//   ko_fill_blank, ko_ordering, ko_matching, ko_descriptive

// 시험 목록/상태
export type MockExamStatus = 'GENERATING' | 'READY' | 'COMPLETED';

/**
 * 생성 결과 코드. 자료가 적어 일부만 생성된 경우를 화면에 알리는 데 쓴다.
 * FastAPI ExamForge 파이프라인의 pipeline_outcome 값과 같다.
 *   - 'passed'                   : 요청 수만큼 생성
 *   - 'passed_partial'           : 일부 문항만 생성(응시는 가능)
 *   - 'failed_minimum_threshold' : 최소 문항 수 미달, 자료 부족(needs_more_source)
 *   - 'failed_quality_gate'      : 품질 게이트 미통과
 *   - 'exhausted'                : 재시도 소진(최선 결과)
 * 백엔드가 이 필드를 보내지 않으면 undefined이고 기존 동작대로 처리한다.
 * 'PARTIAL_SUCCESS'/'NEEDS_MORE_SOURCE' 같은 대문자 표기도 정규화 단계에서 받는다.
 */
export type GenerationOutcome = string;

/**
 * 자료 부족 결과를 표시하는 데 쓰는 공용 메타 필드.
 * 목록/상태/결과 응답이 같은 키로 보낼 수 있게 한곳에 모았다.
 * 모두 옵셔널이라 백엔드가 보내지 않으면 undefined로 받고 기존 동작대로 처리한다.
 */
export interface GenerationOutcomeMeta {
  /** 생성 결과 코드 (passed/passed_partial/failed_minimum_threshold 등) */
  generationOutcome?: GenerationOutcome | null;
  /** 요청한 문항 수 (예: 20). 없으면 questionCount를 쓴다. */
  requestedQuestionCount?: number | null;
  /** 실제로 생성돼 응시 가능한 문항 수 (예: 12). requested보다 작으면 partial이다. */
  actualQuestionCount?: number | null;
  /** 실패 사유 코드 (needs_more_source 등). FAILED 안내 문구를 고르는 데 쓴다. */
  generationFailureReason?: string | null;
}

export interface MockExamListResponse extends GenerationOutcomeMeta {
  id: string;
  title: string;
  examType: string;
  questionCount: number;
  difficulty: string;
  timeLimit: number | null;
  status: MockExamStatus;
  totalPoints: number | null;
  createdAt: string;
  /**
   * examgrading 경로로 전환된 경우 non-null 값이 들어온다.
   * null이면 레거시 응시 경로(getExamDetail)를 사용한다.
   */
  attemptId?: string | null;
}

// 시험 생성 요청/응답
export interface GenerateExamRequest {
  examType: string;
  questionCount: number;
  difficulty: string;
  focusTopics?: string[];
  timeLimit?: number;
}

// 생성 응답. attemptId가 non-null이면 examforge 응시 경로를 쓴다.
export interface GenerateExamResponse {
  attemptId: string;
}

// 응시 상태 폴링
// 백엔드 AttemptStatus enum 값과 1:1로 맞춘다.
// 폴링이 FAILED 같은 비정상 상태도 처리하려면 GENERATING/READY 외 값도 적어야 한다.
export type AttemptStatus =
  | 'GENERATING'
  | 'READY'
  | 'FAILED'
  | 'CREATED'
  | 'IN_PROGRESS'
  | 'SUBMITTED'
  | 'GRADED'
  | 'MANUAL_REVIEW_REQUIRED';

export interface AttemptStatusResponse extends GenerationOutcomeMeta {
  attemptId: string;
  status: AttemptStatus;
// generationOutcome/requestedQuestionCount/actualQuestionCount/generationFailureReason는
  // GenerationOutcomeMeta에서 상속한다. 모두 옵셔널이다.
}

// 응시 상세: GET /api/exam-forge/submissions/attempts/{attemptId}
export interface QuestionOption {
  label: string; // "A", "B", "C" ...
  text: string;
}

export interface AttemptQuestion {
  questionId: string;
  templateId: string;
  displayOrder: number;
  stem: string;
  points: number;
  // 객관식/OX 전용
  options: QuestionOption[];
  // 빈칸 전용 (___) 마커 개수
  blankCount: number;
  // 순서 배열 전용
  orderingItems: string[];
  // 연결하기 전용
  matchingLeftItems: string[];
  matchingRightItems: string[];
}

export interface AttemptDetailResponse {
  attemptId: string;
  questions: AttemptQuestion[];
}

// 제출: POST .../submit
// answer 유형별 직렬화 포맷 (백엔드 계약)
//   객관식/OX  → label 문자열 ("C", "O")
//   단답/서술  → text 문자열
//   빈칸       → ["a","b"] (blankCount 순서)
//   순서       → ["항목정렬배열"]
//   연결       → {"좌":"우", ...}
export interface SubmittedAnswerItem {
  questionId: string;
  answer: string | string[] | Record<string, string>;
}

export interface SubmitAttemptRequest {
  submittedAnswers: SubmittedAnswerItem[];
}

// 결과: GET .../result
export interface RubricBreakdownItem {
  criterion: string;
  score: number;
  maxScore: number;
  reason: string;
}

export interface QuestionResultItem {
  questionId: string;
  /**
   * 채점 완료 전이거나 레거시 경로로 받으면 백엔드가 null을 보낼 수 있다.
   * 컴포넌트에서 null을 확인해야 한다.
   */
  templateId: string | null;
  /** 문항 순번 (Spring 결과 DTO 필드) */
  displayOrder: number;
  /** 문제 본문 텍스트 (Spring 결과 DTO 필드) */
  stem: string;
  /**
   * 객관식/매칭 보기 목록. Spring QuestionGradeResponse.displayOptions에 대응한다.
   * MC·매칭 문형에만 있고, 없으면 undefined다.
   * GradingLeakGuard 보안상 Spring DTO는 'options' 대신 'displayOptions' 키를 사용한다.
   */
  displayOptions?: { label: string; text: string }[];
  /** 내가 제출한 답, 사람이 읽는 형태 (Spring 결과 DTO 필드) */
  myAnswer: string;
  /**
   * 정답. Spring QuestionGradeResponse.reviewAnswer에 대응한다.
   * GradingLeakGuard 보안상 'correctAnswer' 대신 'reviewAnswer' 키를 사용한다.
   * GRADED 복습 상태일 때만 수신된다. 미채점이면 undefined.
   */
  reviewAnswer?: string;
  score: number;
  maxScore: number;
  correct: boolean;
  gradingMode: string;
  feedback: string;
  /**
   * 해설. Spring QuestionGradeResponse.reviewSolution에 대응한다.
   * GradingLeakGuard 보안상 'explanation' 대신 'reviewSolution' 키를 사용한다.
   * GRADED 복습 상태일 때만 수신된다. 미채점이면 undefined.
   */
  reviewSolution?: string;
  rubricBreakdown: RubricBreakdownItem[];
  needsManualReview: boolean;
}

export interface ExamResultResponse extends GenerationOutcomeMeta {
  totalScore: number;
  maxScore: number;
  percentage: number;
  passed: boolean;
  results: QuestionResultItem[];
  // generationOutcome/requestedQuestionCount/actualQuestionCount 는
  // GenerationOutcomeMeta에서 상속하며(모두 옵셔널) partial 결과 안내 배지에 쓴다.
  /**
   * AI 총평 본문. Spring AttemptResultResponse.analysis에 대응한다.
   * 분석 실패나 미생성이면 null 또는 undefined로 오므로 null을 확인한다.
   */
  analysis?: string | null;
  /** AI 진단 약점 주제 목록. 분석 실패나 만점이면 빈 배열 또는 null이다. */
  weakTopics?: string[] | null;
}

// 레거시 호환 타입 (기존 참조 유지)
/** @deprecated AttemptDetailResponse를 사용한다 */
export interface MockExamQuestionResponse {
  id: string;
  questionIdx: number;
  questionText: string;
  questionType: string;
  options: string;
  points: number;
}

/** @deprecated AttemptDetailResponse를 사용한다 */
export interface MockExamDetailResponse {
  id: string;
  title: string;
  examType: string;
  questionCount: number;
  difficulty: string;
  timeLimit: number | null;
  status: MockExamStatus;
  totalPoints: number | null;
  createdAt: string;
  questions: MockExamQuestionResponse[];
}

/** @deprecated SubmitAttemptRequest를 사용한다 */
export interface SubmitExamAnswerItem {
  questionId: string;
  selectedOption: string;
}

/** @deprecated SubmitAttemptRequest를 사용한다 */
export interface SubmitExamRequest {
  answers: SubmitExamAnswerItem[];
  timeTaken?: number;
}

/** @deprecated ExamResultResponse를 사용한다 */
export interface MockExamResultQuestionResponse {
  questionId: string;
  questionIdx: number;
  questionText: string;
  options: string;
  selectedOption: string;
  correctOption: string;
  correct: boolean;
  explanation: string;
  points: number;
}

/** @deprecated ExamResultResponse를 사용한다 */
export interface MockExamResultResponse {
  examId: string;
  title: string;
  score: number;
  totalPoints: number;
  grade: string;
  timeTaken: number | null;
  submittedAt: string;
  analysis: string;
  questionResults: MockExamResultQuestionResponse[];
}

/** @deprecated 기존 오타 타입명 호환용 */
export type MockExamStatus_Legacy = MockExamResultResponse;
