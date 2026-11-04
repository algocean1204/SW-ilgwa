export interface AssignmentListResponse {
  id: string;
  title: string;
  status: string;
  dueDate: string;
  totalQuestions: number;
  maxScore: number | null;
  createdAt: string;
}

export interface QuestionItem {
  questionId: number;
  content: string;
}

export interface AssignmentDetailResponse {
  id: string;
  title: string;
  description: string;
  status: string;
  dueDate: string;
  totalQuestions: number;
  maxScore: number | null;
  questions: QuestionItem[];
  previousAnswer: string | null;
  createdAt: string;
}

export interface SubmissionResponse {
  submissionId: string;
  assignmentId: string;
  status: string;
  submittedAt: string;
}

export interface GradeResponse {
  submissionId: string;
  assignmentId: string;
  score: number | null;
  maxScore: number | null;
  feedback: string;
  aiConfidence: number | null;
  ocrFailedPages: number | null;
  totalPages: number | null;
  gradedAt: string;
  submittedAt: string;
}

export interface AnswerItem {
  questionId: number;
  answer: string;
}

export interface SubmitAnswerRequest {
  answers: AnswerItem[];
}
