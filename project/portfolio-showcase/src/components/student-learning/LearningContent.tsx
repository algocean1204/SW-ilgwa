// 슬라이드와 퀴즈 학습 플레이어
import { useMemo, useState, useEffect, useCallback, useRef } from 'react';
import type { Palette } from '@/lib';
import { getSlides, getQuizzes, getQuizAnswers, submitQuizAnswers, recordProgress } from '@/apis';
import type { SlideResponse, QuizResponse, SingleResult } from '@/apis';
import Icon from '@/Icon';
import { AudioPlayerBar } from './AudioPlayerBar';
import { SlideIframe } from './SlideIframe';
import { resolveResumeSlideIndex } from './learningProgress';

export interface LearningSlideProgress {
  slideTitle: string;
  slideIndex: number;
  totalSlides: number;
  completedSlides: number;
  // AI 채팅 컨텍스트로 넘기는 슬라이드 식별자. 채팅 훅에서 쓴다.
  slideId: string;
  chapterId: string;
  /** 현재 슬라이드 본문. HTML 태그를 뺀 텍스트, 최대 2000자 */
  slideContent: string;
}

/**
 * 슬라이드 iframeHtml에서 순수 텍스트를 추출한다.
 * DOMParser를 사용해 HTML을 파싱 후 textContent를 가져오고,
 * 연속 공백/개행을 정규화해 2000자로 잘라 반환한다.
 * 브라우저 환경에서만 동작하며, SSR 환경에서는 빈 문자열을 반환한다.
 */
function extractSlideText(html: string): string {
  if (typeof window === 'undefined' || !html) return '';
  try {
    const doc = new DOMParser().parseFromString(html, 'text/html');
    const text = (doc.body?.textContent ?? '').replace(/\s+/g, ' ').trim();
    // AI 컨텍스트 토큰을 아끼려고 2000자에서 자른다
    return text.slice(0, 2000);
  } catch {
    return '';
  }
}

interface LearningContentProps {
  courseId: string;
  chapterId: string;
  palette: Palette;
  slidesCollapsed: boolean;
  setSlidesCollapsed: (v: boolean) => void;
  rightInset: number;
  onSlideProgressChange: (progress: LearningSlideProgress | null) => void;
  /** 마지막 슬라이드 완료 시 호출한다. 부모가 챕터 목록을 갱신한다 */
  onChapterComplete?: () => void;
  /** "다음 강의 보기" 버튼 클릭 시 호출 */
  onNextChapter?: () => void;
}

export function LearningContent({
  courseId, chapterId, palette, slidesCollapsed, setSlidesCollapsed, rightInset,
  onSlideProgressChange, onChapterComplete, onNextChapter,
}: LearningContentProps) {
  const [slides, setSlides] = useState<SlideResponse[]>([]);
  const [quizzes, setQuizzes] = useState<QuizResponse[]>([]);
  const [completedSlides, setCompletedSlides] = useState(0);
  const [slideIndex, setSlideIndex] = useState(0);
  // 슬라이드 fetch 상태. 로딩·준비 중·에러마다 안내를 다르게 보여 준다.
  const [slidesFetchStatus, setSlidesFetchStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');

  // 슬라이드 진입 시각. 체류 시간 계산에 쓴다.
  // react-hooks/purity 때문에 렌더 중 Date.now()를 부르지 않는다.
  // 아래 [slideIndex] effect가 마운트 때도 돌아 값을 채운다.
  const slideEntryTimeRef = useRef<number>(0);
  // 언마운트 클린업에서 최신 값을 읽기 위한 ref.
  // react-hooks/refs 때문에 렌더 중에 쓰지 않고 effect에서 맞춘다.
  // 언마운트 클린업은 마지막 렌더의 effect 뒤에 돌아 최신 값을 읽는다.
  const slideIndexRef = useRef(slideIndex);
  const slidesRef = useRef(slides);
  const completedSlidesRef = useRef(completedSlides);
  useEffect(() => {
    slideIndexRef.current = slideIndex;
    slidesRef.current = slides;
    completedSlidesRef.current = completedSlides;
  }, [completedSlides, slideIndex, slides]);
  const [audioAutoPlayToken, setAudioAutoPlayToken] = useState(0);
  const [hasAudioStarted, setHasAudioStarted] = useState(false);
  const [audioPlaying, setAudioPlaying] = useState(false);
  const [selectedOption, setSelectedOption] = useState<number | null>(null);
  const [quizResult, setQuizResult] = useState<SingleResult | null>(null);
  const [existingResults, setExistingResults] = useState<Map<string, SingleResult>>(new Map());

  useEffect(() => {
    if (!courseId || !chapterId) {
      onSlideProgressChange(null);
      return;
    }
    slidesRef.current = [];
    completedSlidesRef.current = 0;
    slideIndexRef.current = 0;
    setSlides([]);
    setQuizzes([]);
    setExistingResults(new Map());
    setSelectedOption(null);
    setQuizResult(null);
    setCompletedSlides(0);
    setSlideIndex(0);
    setAudioPlaying(false);
    setHasAudioStarted(false);
    setSlidesFetchStatus('loading');
    let cancelled = false;
    void getSlides(courseId, chapterId).then((res) => {
      if (cancelled) return;
      if (res.data) {
        const slideList = res.data.slides;
        const resumeIdx = resolveResumeSlideIndex(res.data.completedSlides, slideList.length);
        slidesRef.current = slideList;
        completedSlidesRef.current = res.data.completedSlides;
        slideIndexRef.current = resumeIdx;
        setSlides(slideList);
        setCompletedSlides(res.data.completedSlides);
        // 마지막 학습 위치로 복원한다. 완료한 강의는 다시 보기 의도로 보고 첫 슬라이드에서 시작한다.
        setSlideIndex(resumeIdx);
        // 슬라이드가 0개면 "준비 중", 있으면 "준비 완료"
        setSlidesFetchStatus(slideList.length > 0 ? 'ready' : 'idle');
      }
    }).catch((err) => {
      if (!cancelled) {
        console.warn('[LearningContent] 슬라이드 조회 실패 — courseId:', courseId, 'chapterId:', chapterId, err);
        setSlidesFetchStatus('error');
      }
    });

    return () => {
      cancelled = true;
      onSlideProgressChange(null);
    };
  }, [courseId, chapterId, onSlideProgressChange]);

  useEffect(() => {
    if (!courseId || !chapterId) return;
    let cancelled = false;
    void getQuizzes(courseId, chapterId).then((res) => {
      if (!cancelled && res.data) setQuizzes(res.data.quizzes);
    }).catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [courseId, chapterId]);

  useEffect(() => {
    if (!courseId || !chapterId) return;
    let cancelled = false;
    void getQuizAnswers(courseId, chapterId).then((res) => {
      if (!cancelled && res.data) {
        const map = new Map<string, SingleResult>();
        for (const r of res.data.results) map.set(r.quizId, r);
        setExistingResults(map);
      }
    }).catch(() => {});
    return () => { cancelled = true; };
  }, [courseId, chapterId]);

  const totalSlides = slides.length;
  const slide = slides[slideIndex];

  // 퀴즈는 slideId로 연결되므로 현재 슬라이드의 slideId로 찾는다.
  // 퀴즈가 없으면 undefined를 돌려 빈 상태를 보여 준다.
  const quiz = useMemo(() => {
    if (!slide) return undefined;
    return quizzes.find(q => q.slideId === slide.slideId) ?? undefined;
  }, [quizzes, slide]);


  // 슬라이드를 바꾸면 퀴즈 상태를 바로 복원하거나 비운다.
  // effect 안 동기 setState는 react-hooks/set-state-in-effect에 걸려서
  // 렌더 중 조건부 setState(이전 렌더 값 보관 패턴)로 깜빡임 없이 복원한다.
  const currentQuizId = quiz?.quizId ?? null;
  const existingForQuiz = quiz ? existingResults.get(quiz.quizId) ?? null : null;
  const [prevQuizSync, setPrevQuizSync] = useState<{ quizId: string | null; existing: SingleResult | null }>(
    { quizId: currentQuizId, existing: existingForQuiz },
  );
  if (prevQuizSync.quizId !== currentQuizId || prevQuizSync.existing !== existingForQuiz) {
    setPrevQuizSync({ quizId: currentQuizId, existing: existingForQuiz });
    setQuizResult(existingForQuiz);
    setSelectedOption(existingForQuiz ? existingForQuiz.selectedOption : null);
  }

  // 슬라이드 변경 시 진입 시각 갱신
  useEffect(() => {
    slideEntryTimeRef.current = Date.now();
  }, [slideIndex]);

  const saveProgress = useCallback((completedSlideCount: number, completed: boolean) => {
    if (!courseId || !chapterId) return;
    const totalLen = slidesRef.current.length;
    const safeCompletedSlides = Math.min(
      Math.max(completed ? totalLen : completedSlideCount, 0),
      totalLen,
    );
    // 현재 슬라이드 체류 시간(초). 오디오가 없어도 읽은 시간이 잡힌다.
    const elapsedSec = Math.round((Date.now() - slideEntryTimeRef.current) / 1000);
    void recordProgress(courseId, chapterId, {
      currentSlideIdx: safeCompletedSlides,
      playbackSec: elapsedSec,
      isCompleted: completed,
    }).catch(() => {});
    completedSlidesRef.current = Math.max(completedSlidesRef.current, safeCompletedSlides);
    setCompletedSlides(prev => Math.max(prev, safeCompletedSlides));
    // 챕터 완료 시점에 부모에게 알려 챕터 목록을 갱신한다
    if (completed) onChapterComplete?.();
  }, [courseId, chapterId, onChapterComplete]);

  // 페이지 이탈 시 마지막 슬라이드 체류 시간 저장
  useEffect(() => {
    return () => {
      if (!courseId || !chapterId) return;
      const idx = slideIndexRef.current;
      const totalLen = slidesRef.current.length;
      if (totalLen === 0) return;
      const elapsedSec = Math.round((Date.now() - slideEntryTimeRef.current) / 1000);
      if (elapsedSec < 1) return;
      const alreadyCompleted = completedSlidesRef.current >= totalLen;
      const safeCompletedSlides = alreadyCompleted
        ? totalLen
        : Math.min(Math.max(completedSlidesRef.current, idx), totalLen);
      void recordProgress(courseId, chapterId, {
        currentSlideIdx: safeCompletedSlides,
        playbackSec: elapsedSec,
        isCompleted: alreadyCompleted,
      }).catch(() => {});
    };
  // courseId/chapterId가 바뀔 때만 다시 등록한다
  }, [courseId, chapterId]);

  const requestAutoPlay = useCallback((target: SlideResponse | undefined) => {
    if (target?.audioUrl) setAudioAutoPlayToken(token => token + 1);
  }, []);

  // 현재 슬라이드의 퀴즈가 미제출이면 앞으로 이동 차단
  const isQuizBlocking = useMemo(() => {
    if (!quiz) return false;
    return !existingResults.has(quiz.quizId) && quizResult == null;
  }, [quiz, existingResults, quizResult]);

  const moveSlide = useCallback((offset: number) => {
    if (slides.length === 0) return;
    if (offset > 0 && isQuizBlocking) return;
    const next = Math.min(Math.max(slideIndex + offset, 0), slides.length - 1);
    if (next === slideIndex) return;
    const nextCompletedSlides = offset > 0
      ? Math.max(completedSlides, next)
      : completedSlides;
    saveProgress(nextCompletedSlides, false);
    setSlideIndex(next);
    if (audioPlaying) requestAutoPlay(slides[next]);
  }, [audioPlaying, completedSlides, isQuizBlocking, requestAutoPlay, saveProgress, slideIndex, slides]);

  const handleAudioPlayingChange = useCallback((next: boolean) => {
    setAudioPlaying(next);
    if (next) setHasAudioStarted(true);
  }, []);

  const handleAudioEnded = useCallback(() => {
    if (!hasAudioStarted || slides.length === 0) return;
    if (isQuizBlocking) return;
    if (slideIndex >= slides.length - 1) {
      saveProgress(slides.length, true);
      return;
    }
    const next = slideIndex + 1;
    saveProgress(Math.max(completedSlides, next), false);
    setSlideIndex(next);
    requestAutoPlay(slides[next]);
  // isQuizBlocking이 deps에 없으면 stale 클로저로 차단이 풀린 뒤에도 자동 진행이 막힌다
  }, [completedSlides, hasAudioStarted, isQuizBlocking, requestAutoPlay, saveProgress, slideIndex, slides]);

  const handleNextChapterClick = useCallback(() => {
    saveProgress(totalSlides, true);
    onNextChapter?.();
  }, [onNextChapter, saveProgress, totalSlides]);

  useEffect(() => {
    if (!slide) {
      onSlideProgressChange(null);
      return;
    }
    // 상위 컴포넌트가 AI 채팅 컨텍스트로 쓰도록 slideId, chapterId, 본문 텍스트를 넘긴다
    onSlideProgressChange({
      slideTitle: slide.title,
      slideIndex,
      totalSlides,
      completedSlides,
      slideId: slide.slideId,
      chapterId,
      slideContent: extractSlideText(slide.content),
    });
  }, [chapterId, completedSlides, onSlideProgressChange, slide, slideIndex, totalSlides]);

  const handleQuizSubmit = () => {
    if (selectedOption == null || !quiz || !courseId || !chapterId) return;
    if (existingResults.has(quiz.quizId)) return;
    void submitQuizAnswers(courseId, chapterId, {
      answers: [{ quizId: quiz.quizId, selectedOption }],
    }).then((res) => {
      if (res.data) {
        const result = res.data.results.find(r => r.quizId === quiz.quizId);
        if (result) {
          setQuizResult(result);
          setExistingResults(prev => new Map(prev).set(quiz.quizId, result));
        }
      }
    }).catch(() => {});
  };

  // 슬라이드가 없거나 로딩 중이면 로딩·준비 중·에러를 나눠 안내한다
  if (!slide) {
    // chapterId가 없으면 마운트만 된 상태라 아무것도 그리지 않는다
    if (!chapterId) return null;

    // 슬라이드 fetch 실패(백엔드 에러 포함)면 재시도 안내를 보여 준다
    if (slidesFetchStatus === 'error') {
      return (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center',
          padding: '48px 24px', textAlign: 'center',
        }}>
          <div style={{
            width: 48, height: 48, borderRadius: '50%',
            background: 'var(--coral-wash, #FFF0F0)', display: 'grid', placeItems: 'center', marginBottom: 16,
          }}>
            <Icon name="warning" size={22} color="var(--coral-ink, #C0392B)" />
          </div>
          <p style={{ fontSize: 14, color: 'var(--ink-3)', margin: 0, lineHeight: 1.6 }}>
            슬라이드를 불러오지 못했습니다.<br />
            잠시 후 새로고침 해 주세요.
          </p>
        </div>
      );
    }

    // 로딩 중이거나 슬라이드가 아직 없으면(빈 배열) 생성 대기 안내를 보여 준다
    return (
      <div style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center',
        padding: '48px 24px', textAlign: 'center',
      }}>
        <div style={{
          width: 48, height: 48, borderRadius: '50%',
          background: 'var(--sand)', display: 'grid', placeItems: 'center', marginBottom: 16,
        }}>
          <Icon name="sparkle" size={22} color={palette.primary} />
        </div>
        <p style={{ fontSize: 14, color: 'var(--ink-3)', margin: 0, lineHeight: 1.6 }}>
          {slidesFetchStatus === 'loading'
            ? '슬라이드를 불러오고 있습니다...'
            : '이 챕터의 슬라이드를 준비하고 있습니다.\n잠시 후 다시 확인해 주세요.'
          }
        </p>
      </div>
    );
  }

  return (
    <div>
      <div style={{ padding: '24px 0 0' }}>
        {slidesCollapsed ? (
          <div style={{
            padding: '16px 18px', borderRadius: 12, background: 'var(--sand)',
            border: '1px dashed var(--line)', display: 'flex', alignItems: 'center',
            gap: 10, fontSize: 13, color: 'var(--ink-3)',
          }}>
            <Icon name="eye" size={14} />
            <span>슬라이드 프레임이 접혀 있습니다.</span>
            <div style={{ flex: 1 }} />
            <button onClick={() => setSlidesCollapsed(false)} style={{ fontSize: 12, fontWeight: 800, color: palette.primary }}>
              펼치기
            </button>
          </div>
        ) : (
          <SlideIframe title={slide.title} iframeHtml={slide.content} />
        )}

        {quiz && (
          <section style={{
            marginTop: 18, padding: '16px 18px', borderRadius: 12,
            border: '1px solid var(--line)', background: 'var(--bg)',
          }}>
            <div style={{ fontSize: 12, fontWeight: 800, color: palette.primary, marginBottom: 10 }}>퀴즈</div>
            <div style={{ fontSize: 15, fontWeight: 800, color: 'var(--ink)', marginBottom: 10 }}>
              {quiz.questionText}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 8 }}>
              {quiz.options.map((opt, idx) => {
                const isSelected = selectedOption === idx;
                const submitted = quizResult != null;
                const isCorrectAnswer = submitted && quizResult.correctOption === idx;
                const isWrongPick = submitted && isSelected && !quizResult.isCorrect;
                let bg = 'var(--paper)';
                let color = 'var(--ink-2)';
                let weight = 600;
                if (isCorrectAnswer) { bg = palette.wash; color = palette.primary; weight = 800; }
                else if (isWrongPick) { bg = 'var(--coral-wash)'; color = 'var(--coral-ink)'; weight = 800; }
                else if (isSelected && !submitted) { bg = palette.wash; color = palette.primary; weight = 800; }
                return (
                  <button key={idx} onClick={() => { if (!submitted) setSelectedOption(idx); }} disabled={submitted} style={{
                    padding: '10px 12px', borderRadius: 10, border: '1px solid var(--line)',
                    background: bg, color, fontSize: 13, fontWeight: weight, textAlign: 'left',
                    cursor: submitted ? 'default' : 'pointer',
                  }}>
                    {idx + 1}. {opt}
                  </button>
                );
              })}
            </div>
            {!quizResult ? (
              <button onClick={handleQuizSubmit} disabled={selectedOption == null} style={{
                marginTop: 10, padding: '8px 18px', borderRadius: 8, border: 'none',
                background: selectedOption != null ? palette.primary : 'var(--sand-2)',
                color: selectedOption != null ? 'var(--paper)' : 'var(--ink-3)',
                fontSize: 13, fontWeight: 700,
              }}>
                제출하기
              </button>
            ) : (
              <div style={{ marginTop: 10, fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                <strong>{quizResult.isCorrect ? '정답입니다!' : '오답입니다.'}</strong>
                {quizResult.explanation && (
                  <div style={{ marginTop: 6, padding: '10px 12px', borderRadius: 8, background: 'var(--sand)', fontSize: 12 }}>
                    {quizResult.explanation}
                  </div>
                )}
              </div>
            )}
          </section>
        )}

        <div style={{ display: 'flex', gap: 10, marginTop: 22, paddingTop: 18, borderTop: '1px solid var(--line)' }}>
          <button
            className="lift learning-slide-nav learning-slide-nav--prev"
            onClick={() => moveSlide(-1)} disabled={slideIndex === 0}
            aria-label={slideIndex === 0 ? '이전 슬라이드 없음' : '이전 슬라이드로 이동'}
            style={{ flex: 1, padding: '12px 14px', borderRadius: 10, background: 'var(--paper)', border: '1px solid var(--line)', textAlign: 'left' }}>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', fontWeight: 500, display: 'flex', alignItems: 'center', gap: 4 }}>
              <Icon name="arrowRight" size={12} style={{ transform: 'scaleX(-1)' }} /> 이전 슬라이드
            </div>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-2)', marginTop: 2 }}>
              {slideIndex === 0 ? '첫 슬라이드입니다' : slides[slideIndex - 1]?.title}
            </div>
          </button>
          {slideIndex === totalSlides - 1 && !isQuizBlocking && onNextChapter ? (
            <button
              className="lift"
              onClick={handleNextChapterClick}
              aria-label="다음 강의로 이동"
              style={{ flex: 1, padding: '12px 14px', borderRadius: 10, background: palette.primary, border: 'none', textAlign: 'right', color: 'var(--paper)', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', justifyContent: 'center' }}>
              <div style={{ fontSize: 12, opacity: 0.75, fontWeight: 500, display: 'flex', alignItems: 'center', gap: 4 }}>
                다음 강의 <Icon name="arrowRight" size={12} />
              </div>
              <div style={{ fontSize: 13, fontWeight: 600, marginTop: 2 }}>
                다음 강의 보기
              </div>
            </button>
          ) : (
            <button
              className="lift learning-slide-nav learning-slide-nav--next"
              onClick={() => moveSlide(1)} disabled={slideIndex === totalSlides - 1}
              aria-label={slideIndex === totalSlides - 1 ? '다음 슬라이드 없음' : '다음 슬라이드로 이동'}
              style={{ flex: 1, padding: '12px 14px', borderRadius: 10, background: palette.primary, border: 'none', textAlign: 'right', color: 'var(--paper)' }}>
              <div style={{ fontSize: 12, opacity: 0.75, fontWeight: 500, display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 4 }}>
                다음 슬라이드 <Icon name="arrowRight" size={12} />
              </div>
              <div style={{ fontSize: 13, fontWeight: 600, marginTop: 2 }}>
                {slideIndex === totalSlides - 1 ? '마지막 슬라이드입니다' : slides[slideIndex + 1]?.title}
              </div>
            </button>
          )}
        </div>
      </div>
      <AudioPlayerBar
        palette={palette}
        rightInset={rightInset}
        audioUrl={slide.audioUrl}
        totalSeconds={slide.durationSec}
        autoPlayToken={audioAutoPlayToken}
        onEnded={handleAudioEnded}
        onPlayingChange={handleAudioPlayingChange}
      />
    </div>
  );
}
