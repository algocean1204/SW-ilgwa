export interface SlideResponse {
  slideId: string;
  slideIdx: number;
  title: string;
  content: string;
  audioUrl: string | null;
  durationSec: number;
}

export interface SlideListResponse {
  slides: SlideResponse[];
  totalSlides: number;
  completedSlides: number;
}

export interface QuizResponse {
  quizId: string;
  quizIdx: number;
  slideId: string;
  questionText: string;
  options: string[];
}

export interface QuizListResponse {
  quizzes: QuizResponse[];
  totalQuizzes: number;
}

export interface SingleResult {
  quizId: string;
  selectedOption: number;
  correctOption: number;
  isCorrect: boolean;
  explanation: string;
}

export interface QuizResultResponse {
  totalQuestions: number;
  correctCount: number;
  score: number;
  results: SingleResult[];
}

export interface ProgressRequest {
  /** 완료한 슬라이드 수이자 다음 재개 슬라이드 인덱스 */
  currentSlideIdx: number;
  playbackSec: number;
  isCompleted: boolean;
}

export interface SingleAnswer {
  quizId: string;
  selectedOption: number;
}

export interface QuizAnswerRequest {
  answers: SingleAnswer[];
}
