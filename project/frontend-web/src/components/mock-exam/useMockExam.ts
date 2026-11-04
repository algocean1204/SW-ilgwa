// 모의고사 응시 흐름 상태. generate → 폴링(status) → detail → submit → result 순서다.
//  - generateExam은 Spring /tutoring/{courseId}/mock-exams/generate로 가며 코인 차감과
//    소유권 확인을 한다. 응답(MockExamListResponse)에 attemptId가 온다.
//  - 제출 때 AnswerMap을 유형별 SubmittedAnswerItem[]로 바꾸고, 결과는 getAttemptResult로 받는다.
// 오류 코드:
//  - CON_004(미완강): 가격 페이지로 보내지 않고 안내를 띄우고 생성 버튼을 끈다.
//  - CON_001(코인 부족): 충전 페이지로 보낸다.
// 폴링에서 FAILED를 받으면 5분 타임아웃을 기다리지 않고 바로 실패 화면을 보인다.
// 백엔드 handleGenerationFailed()는 attempt를 FAILED로만 바꾸고 코인을 돌려주지 않아,
// 실패 안내에 고객센터 문의를 넣었다.
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  generateExam,
  getAttemptDetail,
  getAttemptResult,
  getExamDetail,
  getExamList,
  getExamResult,
  submitAttempt,
  submitExam,
  getCoinBalance,
  ApiError,
} from '@/apis';
import type {
  AttemptDetailResponse,
  AttemptStatusResponse,
  ExamResultResponse,
  GenerateExamRequest,
  GenerationOutcomeMeta,
  MockExamDetailResponse,
  MockExamListResponse,
  MockExamResultResponse,
} from '@/apis';
import type { AppState } from '@/shared/types';
import type {
  AnswerMap,
  ExamPhase,
  UseMockExamResult,
} from './mockExam.types';
import { serializeAnswers } from './answerSerializer';
import { EXAM_COIN_COST } from './examConstants';
import { parseOptions, POLL_INTERVAL_MS } from './examOptions';
import { useExamPolling } from './useExamPolling';
import { resolveGenerationDisplay } from './generationOutcome';

/**
 * status·result 등 GenerationOutcomeMeta를 상속한 응답에서 생성 메타 필드만 추린다.
 * 모두 비어 있으면 null이다.
 */
function extractGenerationMeta(
  source?: GenerationOutcomeMeta | null,
): GenerationOutcomeMeta | null {
  if (!source) return null;
  const meta: GenerationOutcomeMeta = {
    generationOutcome: source.generationOutcome ?? null,
    requestedQuestionCount: source.requestedQuestionCount ?? null,
    actualQuestionCount: source.actualQuestionCount ?? null,
    generationFailureReason: source.generationFailureReason ?? null,
  };
  const hasAny =
    meta.generationOutcome != null ||
    meta.requestedQuestionCount != null ||
    meta.actualQuestionCount != null ||
    meta.generationFailureReason != null;
  return hasAny ? meta : null;
}

const OPTION_LABELS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'];

function isLegacyExamId(id: string): boolean {
  return !id.startsWith('eat_');
}

function labelToLegacyIndex(label: string): string {
  const index = OPTION_LABELS.indexOf(label.trim().toUpperCase());
  return index >= 0 ? String(index) : label;
}

function legacyIndexToLabel(value: string | null | undefined): string {
  if (!value) return '';
  const index = Number(value);
  if (Number.isInteger(index) && index >= 0 && index < OPTION_LABELS.length) {
    return OPTION_LABELS[index] ?? value;
  }
  return value;
}

function legacyOptions(raw: string): { label: string; text: string }[] {
  return parseOptions(raw).map((text, index) => ({
    label: OPTION_LABELS[index] ?? String(index + 1),
    text,
  }));
}

function legacyDetailToAttemptDetail(detail: MockExamDetailResponse): AttemptDetailResponse {
  return {
    attemptId: detail.id,
    questions: detail.questions.map((q) => ({
      questionId: q.id,
      templateId: 'ko_multiple_choice_5',
      displayOrder: q.questionIdx,
      stem: q.questionText,
      points: q.points,
      options: legacyOptions(q.options),
      blankCount: 0,
      orderingItems: [],
      matchingLeftItems: [],
      matchingRightItems: [],
    })),
  };
}

function legacyResultToExamResult(result: MockExamResultResponse): ExamResultResponse {
  const maxScore = result.totalPoints || 0;
  const percentage = maxScore > 0 ? Math.round((result.score / maxScore) * 100) : 0;

  return {
    totalScore: result.score,
    maxScore,
    percentage,
    passed: percentage >= 60,
    analysis: result.analysis || null,
    weakTopics: null,
    results: result.questionResults.map((q) => {
      const options = legacyOptions(q.options);
      const selectedLabel = legacyIndexToLabel(q.selectedOption);
      const correctLabel = legacyIndexToLabel(q.correctOption);
      const selectedText = options.find((opt) => opt.label === selectedLabel)?.text;
      const correctText = options.find((opt) => opt.label === correctLabel)?.text;

      return {
        questionId: q.questionId,
        templateId: 'ko_multiple_choice_5',
        displayOrder: q.questionIdx,
        stem: q.questionText,
        displayOptions: options,
        myAnswer: selectedText ? `${selectedLabel}. ${selectedText}` : selectedLabel,
        reviewAnswer: correctText ? `${correctLabel}. ${correctText}` : correctLabel,
        score: q.correct ? q.points : 0,
        maxScore: q.points,
        correct: q.correct,
        gradingMode: 'EXACT',
        feedback: q.correct ? '정답입니다.' : '선택한 답이 정답과 달라요.',
        reviewSolution: q.explanation || undefined,
        rubricBreakdown: [],
        needsManualReview: false,
      };
    }),
  };
}

function toLegacySubmitAnswers(answers: AnswerMap) {
  return Object.entries(answers).map(([questionId, answer]) => ({
    questionId,
    selectedOption:
      answer.kind === 'multiple_choice'
        ? labelToLegacyIndex(answer.label)
        : '',
  }));
}

// 일부 컴포넌트가 './useMockExam'에서 이 타입을 가져온다.
export type { AnswerMap, ExamPhase } from './mockExam.types';

interface UseMockExamArgs {
  courseId: string;
  appState: AppState;
  onNeedCoins: () => void;
}

export function useMockExam({
  courseId,
  appState,
  onNeedCoins,
}: UseMockExamArgs): UseMockExamResult {
  const [phase, setPhase] = useState<ExamPhase>('overview');
  const [examList, setExamList] = useState<MockExamListResponse[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [attemptId, setAttemptId] = useState<string | null>(null);
  const [legacyExamId, setLegacyExamId] = useState<string | null>(null);
  const [detail, setDetail] = useState<AttemptDetailResponse | null>(null);
  const [result, setResult] = useState<ExamResultResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  // CON_004(미완강)이면 true
  const [courseNotCompleted, setCourseNotCompleted] = useState(false);
  // 폴링에서 FAILED를 받으면 true
  const [isFailed, setIsFailed] = useState(false);
  // status·결과 응답에서 받은 생성 메타. 없으면 null
  const [generationMeta, setGenerationMeta] = useState<GenerationOutcomeMeta | null>(null);
  const legacyPollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const clearLegacyPoll = useCallback(() => {
    if (legacyPollRef.current !== null) {
      clearInterval(legacyPollRef.current);
      legacyPollRef.current = null;
    }
  }, []);

  // COMPLETED 시험을 누르면 부른다. openAttempt는 taking 단계로만 가서 따로 둔다.
  const viewResult = useCallback(
    async (id: string) => {
      setError(null);
      try {
        if (isLegacyExamId(id)) {
          const res = await getExamResult(courseId, id);
          if (res.data) {
            setAttemptId(null);
            setLegacyExamId(id);
            setResult(legacyResultToExamResult(res.data));
            setPhase('result');
          } else {
            setError('결과를 불러올 수 없어요. 다시 시도해주세요.');
            setPhase('overview');
          }
          return;
        }

        const res = await getAttemptResult(id);
        if (res.data) {
          setAttemptId(id);
          setLegacyExamId(null);
          setResult(res.data);
          // 결과 응답에 생성 메타가 있을 때만 덮어쓴다.
          const resultMeta = extractGenerationMeta(res.data);
          if (resultMeta) setGenerationMeta(resultMeta);
          setPhase('result');
        } else {
          setError('결과를 불러올 수 없어요. 다시 시도해주세요.');
          setPhase('overview');
        }
      } catch {
        setError('결과를 불러오는 중 오류가 발생했어요.');
        setPhase('overview');
      }
    },
    [courseId],
  );

  // READY일 때만 부른다. status에 생성 메타가 있으면 응시 화면 배지에 쓴다.
  const openAttempt = useCallback(
    async (id: string, status?: AttemptStatusResponse) => {
      setError(null);
      // 목록에서 바로 들어오면 status가 없다.
      const polledMeta = extractGenerationMeta(status);
      if (polledMeta) setGenerationMeta(polledMeta);
      try {
        if (isLegacyExamId(id)) {
          const res = await getExamDetail(courseId, id);
          if (res.data) {
            setAttemptId(null);
            setLegacyExamId(id);
            setDetail(legacyDetailToAttemptDetail(res.data));
            setPhase('taking');
          } else {
            setError('시험을 불러올 수 없어요. 다시 시도해주세요.');
            setPhase('overview');
          }
          return;
        }

        const res = await getAttemptDetail(id);
        if (res.data) {
          setAttemptId(id);
          setLegacyExamId(null);
          setDetail(res.data);
          setPhase('taking');
        } else {
          setError('시험을 불러올 수 없어요. 다시 시도해주세요.');
          setPhase('overview');
        }
      } catch {
        setError('시험을 불러오는 중 오류가 발생했어요.');
        setPhase('overview');
      }
    },
    [courseId],
  );

  // 폴링에서 FAILED를 받았을 때. 자료 부족(needs_more_source)이면 ExamGeneratingView가
  // 행동 제안 화면을 보인다. 백엔드가 코인을 돌려주지 않아 일반 실패 문구에 고객센터를 넣었다.
  const handlePollingFailed = useCallback(
    (_failedAttemptId: string, status?: AttemptStatusResponse) => {
      const failedMeta = extractGenerationMeta(status);
      const display = resolveGenerationDisplay(failedMeta);
      if (failedMeta) setGenerationMeta(failedMeta);

      setIsFailed(true);
      if (display.kind === 'needs_more_source') {
        // 상단 배너는 짧게 두고 행동 제안은 ExamGeneratingView가 보인다.
        setError('현재 자료로는 충분한 문항을 만들기 어려워요.');
      } else {
        setError(
          'AI 서버에서 문제 생성에 실패했어요. 다시 시도하거나 고객센터로 문의해 주세요.',
        );
      }
    },
    [],
  );

  const { pollUntilReady, clearPoll } = useExamPolling({
    onReady: openAttempt,
    onFailed: handlePollingFailed,
  });

  const pollLegacyUntilReady = useCallback(
    (examId: string) => {
      clearLegacyPoll();
      legacyPollRef.current = setInterval(() => {
        void getExamList(courseId)
          .then((res) => {
            const list = res.data ?? [];
            setExamList(list);
            const item = list.find((exam) => exam.id === examId);
            if (!item || item.status === 'GENERATING') return;

            clearLegacyPoll();
            if (item.status === 'READY') {
              void openAttempt(examId);
            } else if (item.status === 'COMPLETED') {
              void viewResult(examId);
            }
          })
          .catch(() => {
            // 일시적 목록 조회 실패는 다음 폴링 주기에 재시도한다.
          });
      }, POLL_INTERVAL_MS);
    },
    [clearLegacyPoll, courseId, openAttempt, viewResult],
  );

  const loadList = useCallback(async () => {
    if (!courseId) return;
    setListLoading(true);
    try {
      const res = await getExamList(courseId);
      setExamList(res.data ?? []);
    } catch {
      setExamList([]);
    } finally {
      setListLoading(false);
    }
  }, [courseId]);

  // 과외가 바뀌면 미완강·실패 플래그까지 초기화하고 목록을 다시 불러온다.
  useEffect(() => {
    setPhase('overview');
    setAttemptId(null);
    setLegacyExamId(null);
    setDetail(null);
    setResult(null);
    setError(null);
    setCourseNotCompleted(false);
    setIsFailed(false);
    setGenerationMeta(null);
    clearPoll();
    clearLegacyPoll();
    void loadList();
    return () => {
      clearPoll();
      clearLegacyPoll();
    };
  }, [courseId, loadList, clearPoll, clearLegacyPoll]);

  // 잔액 확인 → generateExam → attemptId 수신 → 폴링
  const startGenerate = useCallback(
    async (req: GenerateExamRequest) => {
      if (!courseId) return;
      setError(null);

      // 잔액이 모자라면 API를 부르지 않고 충전 페이지로 보낸다.
      if (appState.coins < EXAM_COIN_COST) {
        onNeedCoins();
        return;
      }

      setPhase('generating');
      try {
        // 서버가 코인을 차감하고 AI 파이프라인을 시작한 뒤 attemptId를 돌려준다.
        const genRes = await generateExam(courseId, req);
        const generatedExamId = genRes.data?.id ?? null;
        const newAttemptId = genRes.data?.attemptId ?? null;
        if (!newAttemptId && !generatedExamId) {
          setError('모의고사 생성을 시작할 수 없어요.');
          setPhase('overview');
          return;
        }

        // 차감 후 잔액을 서버에서 다시 받아 전역 상태에 넣는다.
        try {
          const balRes = await getCoinBalance();
          if (typeof balRes.data?.balance === 'number') {
            appState.setCoins(balRes.data.balance);
          }
        } catch {
          // 잔액 조회가 실패해도 생성은 계속한다. 다음 페이지 이동 때 다시 받는다.
        }

        // 새 생성이라 직전 생성 메타를 지우고 READY나 FAILED까지 폴링한다.
        setIsFailed(false);
        setGenerationMeta(null);
        if (newAttemptId) {
          setLegacyExamId(null);
          pollUntilReady(newAttemptId);
        } else if (generatedExamId) {
          setAttemptId(null);
          setLegacyExamId(generatedExamId);
          pollLegacyUntilReady(generatedExamId);
        }
      } catch (err) {
        setPhase('overview');
        if (err instanceof ApiError && err.status === 400) {
          const code = err.errorResponse?.code ?? '';
          if (code === 'CON_004') {
            // 가격 페이지로 보내지 않고 안내만 띄운다. 버튼은 courseNotCompleted로 꺼진다.
            setCourseNotCompleted(true);
            setError('강의를 먼저 완료해야 모의고사를 만들 수 있어요.');
            return;
          }
          if (code === 'CON_001') {
            setError(null);
            onNeedCoins();
            return;
          }
        }
        const isNetworkError = err instanceof ApiError && err.status === 0;
        const isAiError =
          err instanceof ApiError && err.status >= 500;
        if (isNetworkError) {
          setError('네트워크 연결을 확인하고 다시 시도해주세요.');
        } else if (isAiError) {
          setError(
            'AI 서버에 일시적인 문제가 발생했어요. 잠시 후 다시 시도해주세요.',
          );
        } else {
          setError('모의고사 생성에 실패했어요. 다시 시도해주세요.');
        }
      }
    },
    [courseId, appState, onNeedCoins, pollUntilReady, pollLegacyUntilReady],
  );

  // AnswerMap 직렬화 → submitAttempt → 결과 조회
  const finishExam = useCallback(
    async (answers: AnswerMap, timeTaken: number) => {
      if (!attemptId && !legacyExamId) return;
      setError(null);
      try {
        if (legacyExamId) {
          const res = await submitExam(courseId, legacyExamId, {
            answers: toLegacySubmitAnswers(answers),
            timeTaken,
          });
          if (res.data) {
            setResult(legacyResultToExamResult(res.data));
            setPhase('result');
            void loadList();
          } else {
            setError('채점에 실패했어요. 다시 제출해주세요.');
          }
          return;
        }

        if (!attemptId) return;
        const submittedAnswers = serializeAnswers(answers);
        await submitAttempt(attemptId, { submittedAnswers });

        const resResult = await getAttemptResult(attemptId);
        if (resResult.data) {
          setResult(resResult.data);
          const resultMeta = extractGenerationMeta(resResult.data);
          if (resultMeta) setGenerationMeta(resultMeta);
          setPhase('result');
          void loadList();
        } else {
          setError('채점에 실패했어요. 다시 제출해주세요.');
        }
      } catch {
        setError('제출 중 오류가 발생했어요. 다시 시도해주세요.');
      }
    },
    [attemptId, legacyExamId, courseId, loadList],
  );

  const reset = useCallback(() => {
    clearPoll();
    clearLegacyPoll();
    setAttemptId(null);
    setLegacyExamId(null);
    setDetail(null);
    setResult(null);
    setError(null);
    setCourseNotCompleted(false);
    setIsFailed(false);
    setGenerationMeta(null);
    setPhase('overview');
    void loadList();
    // 응시나 결과 화면을 떠날 때 생성권 잔액을 다시 받는다.
    void getCoinBalance().then((res) => {
      if (typeof res.data?.balance === 'number') appState.setCoins(res.data.balance);
    }).catch(() => {});
  }, [clearPoll, clearLegacyPoll, loadList, appState]);

  return {
    phase,
    examList,
    listLoading,
    attemptId,
    detail,
    result,
    error,
    courseNotCompleted,
    isFailed,
    generationMeta,
    startGenerate,
    openAttempt,
    viewResult,
    finishExam,
    reset,
  };
}
