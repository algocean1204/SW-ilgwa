// templateId에 맞는 답안 입력 컴포넌트를 고른다. 새 유형은 여기에만 추가하면 된다.
import type { Palette } from '@/lib';
import type { AttemptQuestion } from '@/apis';
import type { AnswerMap, ExamAnswer } from './mockExam.types';
import { templateToKind } from './mockExam.types';
import MultipleChoiceInput from './MultipleChoiceInput';
import TrueFalseInput from './TrueFalseInput';
import ShortAnswerInput from './ShortAnswerInput';
import ClozeInput from './ClozeInput';
import EssayInput from './EssayInput';
import OrderingInput from './OrderingInput';
import MatchingInput from './MatchingInput';

interface ExamAnswerZoneProps {
  question: AttemptQuestion;
  answers: AnswerMap;
  palette: Palette;
  onAnswer: (questionId: string, answer: ExamAnswer) => void;
}

export default function ExamAnswerZone({
  question,
  answers,
  palette,
  onAnswer,
}: ExamAnswerZoneProps) {
  const { questionId, templateId, options, blankCount, orderingItems,
    matchingLeftItems, matchingRightItems } = question;
  const currentAnswer = answers[questionId];
  const kind = templateToKind(templateId);

  switch (kind) {
    case 'multiple_choice':
      return (
        <MultipleChoiceInput
          options={options}
          answer={currentAnswer?.kind === 'multiple_choice' ? currentAnswer : undefined}
          palette={palette}
          onSelect={(ans) => onAnswer(questionId, ans)}
        />
      );

    case 'true_false':
      return (
        <TrueFalseInput
          answer={currentAnswer?.kind === 'true_false' ? currentAnswer : undefined}
          palette={palette}
          onSelect={(ans) => onAnswer(questionId, ans)}
        />
      );

    case 'short_answer':
      return (
        <ShortAnswerInput
          answer={currentAnswer?.kind === 'short_answer' ? currentAnswer : undefined}
          questionId={questionId}
          onChange={(ans) => onAnswer(questionId, ans)}
        />
      );

    case 'cloze':
      return (
        <ClozeInput
          stem={question.stem}
          blankCount={blankCount}
          questionId={questionId}
          answer={currentAnswer?.kind === 'cloze' ? currentAnswer : undefined}
          onChange={(ans) => onAnswer(questionId, ans)}
        />
      );

    case 'essay':
      return (
        <EssayInput
          answer={currentAnswer?.kind === 'essay' ? currentAnswer : undefined}
          questionId={questionId}
          onChange={(ans) => onAnswer(questionId, ans)}
        />
      );

    case 'ordering':
      return (
        <OrderingInput
          items={orderingItems}
          answer={currentAnswer?.kind === 'ordering' ? currentAnswer : undefined}
          palette={palette}
          onChange={(ans) => onAnswer(questionId, ans)}
        />
      );

    case 'matching':
      return (
        <MatchingInput
          leftItems={matchingLeftItems}
          rightItems={matchingRightItems}
          answer={currentAnswer?.kind === 'matching' ? currentAnswer : undefined}
          palette={palette}
          onChange={(ans) => onAnswer(questionId, ans)}
        />
      );
  }
}
