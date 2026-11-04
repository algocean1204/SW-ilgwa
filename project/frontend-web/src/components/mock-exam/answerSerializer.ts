// AnswerMap → SubmittedAnswerItem[]. 유형별 백엔드 answer 형식:
//   객관식/OX  → label 문자열
//   단답/서술  → text 문자열
//   빈칸       → string[] (blankCount 순서)
//   순서       → string[] (정렬된 항목 배열)
//   연결       → Record<string, string> (좌→우 맵)
import type { SubmittedAnswerItem } from '@/apis';
import type { AnswerMap, ExamAnswer } from './mockExam.types';

/**
 * ExamAnswer 하나를 백엔드 answer 형식으로 바꾼다. 끝의 반환은 exhaustive 체크용이다.
 */
function serializeAnswer(
  answer: ExamAnswer,
): string | string[] | Record<string, string> {
  switch (answer.kind) {
    case 'multiple_choice':
      return answer.label;
    case 'true_false':
      return answer.label;
    case 'short_answer':
      return answer.text;
    case 'essay':
      return answer.text;
    case 'cloze':
      return answer.blanks;
    case 'ordering':
      return answer.order;
    case 'matching':
      return answer.pairs;
  }
}

/**
 * 서버가 null 답안을 거절할 수 있어 미답(undefined) 항목은 빼고 보낸다.
 */
export function serializeAnswers(answers: AnswerMap): SubmittedAnswerItem[] {
  return Object.entries(answers).map(([questionId, answer]) => ({
    questionId,
    answer: serializeAnswer(answer),
  }));
}
