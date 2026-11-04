import type { ShowcaseQuestion } from './store';

export interface QuestionResult {
  questionId: string;
  templateId: string | null;
  displayOrder: number;
  stem: string;
  displayOptions?: { label: string; text: string }[];
  myAnswer: string;
  reviewAnswer?: string;
  score: number;
  maxScore: number;
  correct: boolean;
  gradingMode: string;
  feedback: string;
  reviewSolution?: string;
  rubricBreakdown: { criterion: string; score: number; maxScore: number; reason: string }[];
  needsManualReview: boolean;
}

export interface GradedResult {
  totalScore: number;
  maxScore: number;
  percentage: number;
  passed: boolean;
  analysis: string | null;
  weakTopics: string[] | null;
  results: QuestionResult[];
}

type AnswerValue = string | string[] | Record<string, string>;

function normalizeText(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, '');
}

function formatMyAnswer(q: ShowcaseQuestion, answer: AnswerValue): string {
  const tid = q.templateId;
  if (tid === 'ko_multiple_choice_4' || tid === 'ko_multiple_choice_5' || tid === 'ko_true_false') {
    const label = String(answer);
    const opt = q.options.find((o) => o.label === label);
    return opt ? `${label}. ${opt.text}` : label;
  }
  if (typeof answer === 'string') return answer;
  if (Array.isArray(answer)) {
    return tid === 'ko_ordering' ? answer.join(' → ') : answer.join(', ');
  }
  if (typeof answer === 'object') {
    return Object.entries(answer).map(([k, v]) => `${k}→${v}`).join('; ');
  }
  return String(answer);
}

function formatReviewAnswer(q: ShowcaseQuestion): string {
  const ca = q._correctAnswer;
  if (ca == null) return '';
  const tid = q.templateId;
  if (tid === 'ko_multiple_choice_4' || tid === 'ko_multiple_choice_5') {
    const opt = q.options.find((o) => o.label === String(ca));
    return opt ? `${ca}. ${opt.text}` : String(ca);
  }
  if (tid === 'ko_ordering' && typeof ca === 'string') {
    return ca.split('|').map((s) => s.trim()).join(' → ');
  }
  return String(ca);
}

function gradeAnswer(q: ShowcaseQuestion, answer: AnswerValue): { correct: boolean; score: number; feedback: string; gradingMode: string; rubric: QuestionResult['rubricBreakdown'] } {
  const max = q.points;
  const tid = q.templateId;
  const ca = q._correctAnswer;

  if (tid === 'ko_multiple_choice_4' || tid === 'ko_multiple_choice_5' || tid === 'ko_true_false') {
    const correct = String(answer) === String(ca);
    return {
      correct,
      score: correct ? max : 0,
      feedback: correct ? '정답입니다.' : `정답은 ${ca}번입니다. ${q._explanation ?? ''}`.trim(),
      gradingMode: 'DETERMINISTIC',
      rubric: [],
    };
  }

  if (tid === 'ko_short_answer') {
    const correct = normalizeText(String(answer)) === normalizeText(String(ca ?? ''));
    return {
      correct,
      score: correct ? max : 0,
      feedback: correct ? '정답입니다.' : `핵심 답안: ${ca}. ${q._explanation ?? ''}`.trim(),
      gradingMode: 'DETERMINISTIC',
      rubric: [],
    };
  }

  if (tid === 'ko_fill_blank' && Array.isArray(answer)) {
    const meta = q._gradingMetadata ?? {};
    const expected = (meta.blank_answers as string[] | undefined) ?? String(ca ?? '').split('|');
    const correct = expected.every((exp, i) => normalizeText(String(answer[i] ?? '')) === normalizeText(exp));
    return {
      correct,
      score: correct ? max : 0,
      feedback: correct ? '정답입니다.' : `정답: ${expected.join(', ')}. ${q._explanation ?? ''}`.trim(),
      gradingMode: 'DETERMINISTIC',
      rubric: [],
    };
  }

  if (tid === 'ko_ordering' && Array.isArray(answer)) {
    const meta = q._gradingMetadata ?? {};
    const expected = (meta.correct_ordering as string[] | undefined)
      ?? (typeof ca === 'string' ? ca.split('|').map((s) => s.trim()) : []);
    const correct = JSON.stringify(answer) === JSON.stringify(expected);
    return {
      correct,
      score: correct ? max : 0,
      feedback: correct ? '정답입니다.' : `올바른 순서: ${expected.join(' → ')}`,
      gradingMode: 'DETERMINISTIC',
      rubric: [],
    };
  }

  if (tid === 'ko_matching' && typeof answer === 'object' && !Array.isArray(answer)) {
    const meta = q._gradingMetadata ?? {};
    const pairs = (meta.matching_pairs as { left: string; right: string }[] | undefined) ?? [];
    const expected: Record<string, string> = {};
    for (const p of pairs) expected[p.left] = p.right;
    const correct = Object.keys(expected).every((k) => answer[k] === expected[k]);
    return {
      correct,
      score: correct ? max : 0,
      feedback: correct ? '정답입니다.' : '연결 관계를 다시 확인해 보세요.',
      gradingMode: 'DETERMINISTIC',
      rubric: [],
    };
  }

  // 주관식은 데모용 부분 점수와 해설 기반 피드백을 준다
  const text = typeof answer === 'string' ? answer.trim() : '';
  const partial = text.length >= 20;
  const score = partial ? Math.round(max * 0.6) : Math.round(max * 0.2);
  return {
    correct: false,
    score,
    feedback: partial
      ? `데모 채점: 답안을 확인했습니다. 모범 답안과 비교해 보세요. ${q._explanation?.slice(0, 120) ?? ''}`
      : '데모 채점: 답안이 너무 짧습니다. 핵심 개념을 문장으로 풀어 써 보세요.',
    gradingMode: 'AI_RUBRIC',
    rubric: [
      { criterion: '핵심 개념 포함', score: partial ? score * 0.5 : 0, maxScore: max * 0.5, reason: partial ? '일부 포함' : '미흡' },
      { criterion: '논리적 설명', score: partial ? score * 0.5 : score, maxScore: max * 0.5, reason: '데모 모드 간이 채점' },
    ],
  };
}

export function gradeAttempt(
  questions: ShowcaseQuestion[],
  submittedAnswers: { questionId: string; answer: AnswerValue }[],
): GradedResult {
  const answerMap = new Map(submittedAnswers.map((a) => [a.questionId, a.answer]));
  const results: QuestionResult[] = [];
  let totalScore = 0;
  let maxScore = 0;
  const weakTopics: string[] = [];

  for (const q of questions) {
    maxScore += q.points;
    const answer = answerMap.get(q.questionId);
    if (answer === undefined) {
      results.push({
        questionId: q.questionId,
        templateId: q.templateId,
        displayOrder: q.displayOrder,
        stem: q.stem,
        ...(q.options.length ? { displayOptions: q.options } : {}),
        myAnswer: '(미응답)',
        reviewAnswer: formatReviewAnswer(q),
        score: 0,
        maxScore: q.points,
        correct: false,
        gradingMode: 'DETERMINISTIC',
        feedback: '답안이 제출되지 않았습니다.',
        reviewSolution: q._explanation,
        rubricBreakdown: [],
        needsManualReview: false,
      });
      weakTopics.push(q.stem.slice(0, 24));
      continue;
    }

    const graded = gradeAnswer(q, answer);
    totalScore += graded.score;
    if (!graded.correct) weakTopics.push(q.stem.slice(0, 24));

    results.push({
      questionId: q.questionId,
      templateId: q.templateId,
      displayOrder: q.displayOrder,
      stem: q.stem,
      ...(q.options.length ? { displayOptions: q.options } : {}),
      myAnswer: formatMyAnswer(q, answer),
      reviewAnswer: formatReviewAnswer(q),
      score: graded.score,
      maxScore: q.points,
      correct: graded.correct,
      gradingMode: graded.gradingMode,
      feedback: graded.feedback,
      reviewSolution: q._explanation,
      rubricBreakdown: graded.rubric,
      needsManualReview: false,
    });
  }

  const percentage = maxScore > 0 ? Math.round((totalScore / maxScore) * 100) : 0;
  return {
    totalScore,
    maxScore,
    percentage,
    passed: percentage >= 60,
    analysis: `데모 채점 완료: ${results.filter((r) => r.correct).length}/${results.length}문항 정답, 총점 ${totalScore}/${maxScore}점(${percentage}%).`,
    weakTopics: [...new Set(weakTopics)].slice(0, 5),
    results,
  };
}