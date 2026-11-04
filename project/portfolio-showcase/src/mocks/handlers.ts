import { http, HttpResponse } from 'msw';
import { DEMO_CHAT_DISABLED_MSG } from '@/lib/demoMode';
import { gradeAttempt } from './grading';
import { apiWrap, findChapter, findCourse, store } from './store';

type ExamBundle = (typeof store.courses)[number]['exams'][number];

// 데모는 로그인 단계 없이 바로 메인 화면을 열기 때문에 세션이 열린 상태로 시작한다.
let demoSessionActive = true;

let telegramPrefs = {
  noteEnabled: true,
  quizEnabled: true,
  friendEnabled: false,
};

const DEMO_TOSS_CLIENT_KEY = 'demo_toss_client_key';

const COIN_BUNDLES = [
  { bundleId: 'COIN_5', displayAmount: 5, bonusAmount: 0, totalAmount: 5, priceKrw: 4_900 },
  { bundleId: 'COIN_20', displayAmount: 20, bonusAmount: 5, totalAmount: 25, priceKrw: 14_900 },
  { bundleId: 'COIN_60', displayAmount: 60, bonusAmount: 15, totalAmount: 75, priceKrw: 39_900 },
] as const;

function recentDates(n: number): string[] {
  const out: string[] = [];
  const t = new Date();
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(t);
    d.setDate(t.getDate() - i);
    out.push(d.toISOString().slice(0, 10));
  }
  return out;
}

const WEEKLY_STUDY_MINUTES = [30, 25, 0, 45, 20, 35, 25];
const WEEKLY_QUIZ_COUNTS = [4, 3, 0, 6, 3, 5, 3];
const WEEKLY_AVERAGE_SCORES = [90, 88, 0, 95, 92, 93, 94];

function buildWeeklyDailyStats() {
  return recentDates(7).map((date, i) => ({
    date,
    studyMinutes: WEEKLY_STUDY_MINUTES[i],
    quizCount: WEEKLY_QUIZ_COUNTS[i],
    averageScore: WEEKLY_AVERAGE_SCORES[i],
  }));
}

function buildMonthlyDailyData() {
  const dates = recentDates(30);
  const minutes = dates.map((date) => {
    const d = new Date(`${date}T12:00:00`);
    const isWeekend = d.getDay() === 0 || d.getDay() === 6;
    const dayNum = d.getDate();
    if (isWeekend) return dayNum % 3 === 0 ? 0 : 10 + (dayNum % 11);
    return 30 + (dayNum * 7) % 61;
  });
  const totalMinutes = minutes.reduce((a, b) => a + b, 0);
  return {
    dailyData: dates.map((date, i) => ({ date, minutes: minutes[i] })),
    totalMinutes,
    averageMinutesPerDay: Math.round(totalMinutes / 30),
  };
}

function buildStreakHeatmap(): Record<string, number> {
  const map: Record<string, number> = {};
  const today = new Date();
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  let filled = 0;
  for (let i = 0; i < 49; i++) {
    const d = new Date(today);
    d.setDate(today.getDate() - i);
    const key = fmt(d);
    if (i < 7) {
      map[key] = ([45, 35, 50, 40, 25, 30, 55] as const)[i] ?? 30;
      filled++;
    } else if (filled < 42 && i % 8 !== 0) {
      map[key] = 20 + ((i * 3) % 41);
      filled++;
    }
  }
  return map;
}

const SUBSCRIPTION_PLANS = [
  {
    code: 'free',
    priceKrw: 0,
    periodDays: null,
    maxCourses: 1,
    maxSlidesPerCourse: 2,
    canUseTelegram: false,
    canCustomizeTutor: false,
    features: [
      '과외 1개 생성 — 부담 없이 시작',
      '슬라이드 2개 미리보기 (전체 X)',
      '웹에서 튜터 Q&A',
      '기본 튜터 2명 (올빼미·냥)',
      '생성권 구매 가능 (무기한)',
    ],
  },
  {
    code: 'pro',
    priceKrw: 14_900,
    periodDays: 30,
    maxCourses: 2,
    maxSlidesPerCourse: null,
    canUseTelegram: true,
    canCustomizeTutor: true,
    features: [
      '과외 2개 — 강의자료 전체 무제한 해제',
      '코인 5개 즉시 지급 (약 15,000원 상당)',
      '텔레그램 암기노트·과제·채점·총평',
      '나만의 튜터 커스터마이징',
      '학습 리포트로 약점 파악',
      '완강 시 모의고사 1회 무료 (과외별)',
    ],
  },
  {
    code: 'premium',
    priceKrw: 49_900,
    periodDays: 30,
    maxCourses: 10,
    maxSlidesPerCourse: null,
    canUseTelegram: true,
    canCustomizeTutor: true,
    features: [
      '과외 10개 — Pro의 모든 기능 포함',
      '코인 22개 즉시 지급 (약 50,000원 상당)',
      '모의고사 추가 2회 더 (총 3회)',
      '우선 지원 — 가장 빠른 응답',
    ],
  },
] as const;

const SUBSCRIPTION_HISTORY = [
  {
    paymentId: 'pay_demo_001',
    planCode: 'pro' as const,
    amount: 14_900,
    status: 'paid' as const,
    paidAt: '2026-05-01T09:00:00+09:00',
    tossOrderId: 'demo_order_pro_001',
  },
  {
    paymentId: 'pay_demo_002',
    planCode: 'pro' as const,
    amount: 14_900,
    status: 'paid' as const,
    paidAt: '2026-04-01T09:00:00+09:00',
    tossOrderId: 'demo_order_pro_002',
  },
];

function getExamBundle(attemptId: string): ExamBundle | undefined {
  for (const course of store.courses) {
    const exam = course.exams.find((e) => e.attempt.attemptId === attemptId);
    if (exam) return exam;
  }
  return undefined;
}

function courseSummary(c: (typeof store.courses)[number]) {
  return {
    id: c.id,
    title: c.title,
    subject: c.subject,
    tutorName: c.tutorName,
    tutorAvatarUrl: c.tutorAvatarUrl,
    status: c.status,
    progress: c.progress,
    totalLessons: c.totalLessons,
    completedLessons: c.completedLessons,
    lastStudiedAt: c.lastStudiedAt,
    createdAt: c.createdAt,
  };
}

function chapterSummary(ch: (typeof store.courses)[number]['chapters'][number]) {
  return {
    id: ch.id,
    order: ch.order,
    title: ch.title,
    description: ch.description,
    estimatedMinutes: ch.estimatedMinutes,
    status: ch.status,
    slideCount: ch.slideCount,
    completedSlides: ch.completedSlides,
    completedAt: ch.completedAt,
  };
}

function chapterDetail(ch: (typeof store.courses)[number]['chapters'][number]) {
  return {
    ...chapterSummary(ch),
    keyTopics: ch.keyTopics,
    bookmarks: ch.bookmarks,
    xpEarned: ch.xpEarned,
  };
}

const profile = () => ({
  id: store.user.id,
  email: store.user.email,
  nickname: store.user.nickname,
  profileImageUrl: null,
  grade: '대학생',
  major: '컴퓨터공학',
  education: '학사',
  interests: ['개발', '알고리즘'],
  learningGoal: '시스템 프로그래밍 마스터',
  preferredTone: 'friendly',
  subscriptionStatus: store.user.subscriptionStatus,
  subscriptionExpiresAt: null,
  xp: 2400,
  level: 5,
  points: 120,
  telegramLinked: false,
  onboardingCompleted: store.user.onboardingCompleted,
  createdAt: store.generatedAt,
  settings: {
    highContrast: false,
    keyboardNavigation: false,
    dailyStudyGoalMinutes: 60,
    notifications: { lesson: true, assignment: true, community: false, marketing: false },
  },
});

export const handlers = [
  // Auth
  http.post('/api/auth/dev-login', () => {
    demoSessionActive = true;
    return HttpResponse.json(apiWrap({
      accessToken: store.accessToken,
      userId: store.user.id,
      email: store.user.email,
      nickname: store.user.nickname,
      onboardingCompleted: store.user.onboardingCompleted,
      subscriptionStatus: store.user.subscriptionStatus,
    }));
  }),
  http.post('/api/auth/refresh', () => {
    if (!demoSessionActive) {
      return HttpResponse.json(apiWrap(null));
    }
    return HttpResponse.json(apiWrap({ accessToken: store.accessToken }));
  }),
  http.post('/api/auth/logout', () => {
    demoSessionActive = false;
    return HttpResponse.json(apiWrap(null));
  }),
  http.post('/api/auth/login/google', () => new HttpResponse(null, { status: 501 })),
  http.post('/api/auth/login/kakao', () => new HttpResponse(null, { status: 501 })),

  // User
  http.get('/api/users/me', () => HttpResponse.json(apiWrap(profile()))),
  http.get('/api/users/me/stats', () =>
    HttpResponse.json(apiWrap({ totalStudyMinutes: 420, completedLessons: 30, streakDays: 7 })),
  ),
  http.get('/api/users/telegram/status', () =>
    HttpResponse.json(apiWrap({ linked: false, telegramChatId: null, telegramUsername: null, linkedAt: null, lastActiveAt: null })),
  ),
  http.get('/api/users/telegram/preferences', () =>
    HttpResponse.json(apiWrap({ ...telegramPrefs })),
  ),
  http.put('/api/users/telegram/preferences', async ({ request }) => {
    const body = (await request.json()) as Partial<typeof telegramPrefs>;
    telegramPrefs = { ...telegramPrefs, ...body };
    return HttpResponse.json(apiWrap({ ...telegramPrefs }));
  }),

  // Tutoring / Courses
  http.get('/api/tutoring', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 50);
    const content = store.courses.map(courseSummary);
    return HttpResponse.json(apiWrap({
      content,
      page,
      size,
      totalElements: content.length,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    }));
  }),
  http.get('/api/tutoring/recent', () =>
    HttpResponse.json(apiWrap({
      items: store.courses.slice(0, 3).map((c) => ({
        courseId: c.id,
        courseName: c.title,
        subject: c.subject,
        status: c.status,
        progressPercent: c.progress,
        lastAccessedAt: c.lastStudiedAt,
      })),
    })),
  ),
  http.get('/api/tutoring/:courseId', ({ params }) => {
    const course = findCourse(String(params.courseId));
    if (!course) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap({
      ...courseSummary(course),
      tutorId: course.tutorId,
      sourceType: course.sourceType,
      mode: course.mode,
    }));
  }),
  http.get('/api/tutoring/:courseId/lessons', ({ params }) => {
    const course = findCourse(String(params.courseId));
    if (!course) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap(course.chapters.map(chapterSummary)));
  }),
  http.get('/api/tutoring/:courseId/lessons/:chapterId', ({ params }) => {
    const ch = findChapter(String(params.courseId), String(params.chapterId));
    if (!ch) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap(chapterDetail(ch)));
  }),
  http.get('/api/tutoring/:courseId/lessons/:chapterId/slides', ({ params }) => {
    const ch = findChapter(String(params.courseId), String(params.chapterId));
    if (!ch) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap({
      slides: ch.slides,
      totalSlides: ch.slideCount,
      completedSlides: ch.completedSlides,
    }));
  }),
  http.post('/api/tutoring/:courseId/lessons/:chapterId/progress', () =>
    HttpResponse.json(apiWrap(null)),
  ),
  http.get('/api/tutoring/:courseId/lessons/:chapterId/quizzes', ({ params }) => {
    const ch = findChapter(String(params.courseId), String(params.chapterId));
    if (!ch) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap({
      quizzes: ch.quizzes,
      totalQuizzes: ch.quizzes.length,
    }));
  }),
  http.get('/api/tutoring/:courseId/lessons/:chapterId/quiz-answers', ({ params }) => {
    const ch = findChapter(String(params.courseId), String(params.chapterId));
    if (!ch) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap(ch.quizResult));
  }),
  http.post('/api/tutoring/:courseId/lessons/:chapterId/quiz-answers', ({ params, request }) =>
    request.json().then(() => {
      const ch = findChapter(String(params.courseId), String(params.chapterId));
      if (!ch) return new HttpResponse(null, { status: 404 });
      return HttpResponse.json(apiWrap(ch.quizResult));
    }),
  ),
  http.get('/api/tutoring/:courseId/lessons/:chapterId/notes', ({ params }) => {
    const ch = findChapter(String(params.courseId), String(params.chapterId));
    if (!ch) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap(ch.notes));
  }),
  http.get('/api/tutoring/:courseId/curriculum', ({ params }) => {
    const course = findCourse(String(params.courseId));
    if (!course) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap({
      courseId: course.id,
      status: 'CONFIRMED',
      chapters: course.chapters.map(chapterSummary),
    }));
  }),

  // Chat
  http.get('/api/tutoring/:courseId/chat/history', () =>
    HttpResponse.json(apiWrap({ messages: [] })),
  ),
  http.post('/api/tutoring/:courseId/chat', () =>
    HttpResponse.json(apiWrap({
      responseText: DEMO_CHAT_DISABLED_MSG,
      citations: [],
      sessionId: 'demo_chat_session',
    })),
  ),

  // Assignments
  http.get('/api/tutoring/:courseId/assignments', ({ params }) => {
    const course = findCourse(String(params.courseId));
    if (!course) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap(course.assignments.map((a) => a.listItem)));
  }),
  http.get('/api/tutoring/:courseId/assignments/:assignmentId', ({ params }) => {
    const course = findCourse(String(params.courseId));
    const a = course?.assignments.find((x) => x.id === params.assignmentId);
    if (!a) return new HttpResponse(null, { status: 404 });
    const { listItem: _l, chapterId: _c, ...detail } = a;
    return HttpResponse.json(apiWrap(detail));
  }),
  http.get('/api/tutoring/:courseId/assignments/:assignmentId/grades', () =>
    HttpResponse.json(apiWrap({
      submissionId: 'sub_demo',
      assignmentId: 'asg_demo',
      score: null,
      maxScore: 100,
      feedback: '데모 모드에서는 과제 채점이 제공되지 않습니다.',
      aiConfidence: null,
      ocrFailedPages: null,
      totalPages: null,
      gradedAt: store.generatedAt,
      submittedAt: store.generatedAt,
    })),
  ),

  // Notes (global)
  http.get('/api/notes', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 20);
    const all = store.courses.flatMap((c) => c.notes);
    const slice = all.slice(page * size, page * size + size);
    return HttpResponse.json(apiWrap({
      content: slice,
      totalElements: all.length,
      totalPages: Math.ceil(all.length / size) || 1,
      size,
      number: page,
      first: page === 0,
      last: page * size + size >= all.length,
      empty: all.length === 0,
      numberOfElements: slice.length,
      pageable: {
        pageNumber: page,
        pageSize: size,
        sort: { sorted: false, empty: true, unsorted: true },
        offset: page * size,
        paged: true,
        unpaged: false,
      },
      sort: { sorted: false, empty: true, unsorted: true },
    }));
  }),

  // Mock exams
  http.get('/api/tutoring/:courseId/mock-exams', ({ params }) => {
    const course = findCourse(String(params.courseId));
    if (!course) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap(course.exams.map((e) => e.mockExam)));
  }),
  http.get('/api/exam-forge/attempts/:attemptId/status', ({ params }) => {
    const bundle = getExamBundle(String(params.attemptId));
    if (!bundle) return new HttpResponse(null, { status: 404 });
    const status = bundle.attempt.status === 'GRADED' ? 'GRADED' : 'READY';
    return HttpResponse.json(apiWrap({ attemptId: bundle.attempt.attemptId, status }));
  }),
  http.get('/api/exam-forge/submissions/attempts/:attemptId', ({ params }) => {
    const bundle = getExamBundle(String(params.attemptId));
    if (!bundle) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(apiWrap({
      attemptId: bundle.attempt.attemptId,
      questions: bundle.attempt.publicQuestions,
    }));
  }),
  http.get('/api/exam-forge/submissions/attempts/:attemptId/result', ({ params }) => {
    const bundle = getExamBundle(String(params.attemptId));
    if (!bundle) return new HttpResponse(null, { status: 404 });
    if (bundle.gradedResult) {
      return HttpResponse.json(apiWrap(bundle.gradedResult));
    }
    return new HttpResponse(null, { status: 404 });
  }),
  http.post('/api/exam-forge/submissions/attempts/:attemptId/submit', async ({ params, request }) => {
    const bundle = getExamBundle(String(params.attemptId));
    if (!bundle) return new HttpResponse(null, { status: 404 });
    if (bundle.attempt.status === 'GRADED') {
      return new HttpResponse(null, { status: 409 });
    }
    const body = (await request.json()) as { submittedAnswers?: { questionId: string; answer: string | string[] | Record<string, string> }[] };
    const graded = gradeAttempt(bundle.attempt.questions, body.submittedAnswers ?? []);
    bundle.gradedResult = graded;
    bundle.attempt.status = 'GRADED';
    bundle.mockExam.status = 'COMPLETED';
    return HttpResponse.json(apiWrap(null));
  }),

  // Tutor
  http.get('/api/tutors', () =>
    HttpResponse.json(apiWrap({
      tutors: [{
        tutorId: store.tutor.tutorId,
        name: store.tutor.name,
        tagline: store.tutor.tagline,
        profileImageUrl: store.tutor.profileImageUrl,
        voiceSampleUrl: store.tutor.voiceSampleUrl,
        isDefault: true,
        toneSlider: 50,
        speedSlider: 50,
        depthSlider: 50,
        questionStyleSlider: 50,
        useEmoji: false,
        useFormalSpeech: false,
        createdAt: store.generatedAt,
        updatedAt: store.generatedAt,
      }],
    })),
  ),

  // Coin / Subscription
  http.get('/api/coins/balance', () =>
    HttpResponse.json(apiWrap({
      balance: store.coins,
      totalPurchased: 500,
      totalUsed: 80,
    })),
  ),
  http.get('/api/coins/bundles', () =>
    HttpResponse.json(apiWrap([...COIN_BUNDLES])),
  ),
  http.get('/api/coins/history', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 20);
    return HttpResponse.json(apiWrap({
      content: [],
      page,
      size,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    }));
  }),
  http.post('/api/coins/purchase', async ({ request }) => {
    const body = (await request.json()) as { bundleId?: string };
    const bundle = COIN_BUNDLES.find((b) => b.bundleId === body.bundleId) ?? COIN_BUNDLES[1];
    return HttpResponse.json(apiWrap({
      orderId: `demo_coin_${Date.now()}`,
      amount: bundle.priceKrw,
      coinAmount: bundle.totalAmount,
      tossPaymentsClientKey: DEMO_TOSS_CLIENT_KEY,
      orderName: `생성권 ${bundle.displayAmount}개`,
    }));
  }),
  http.post('/api/coins/confirm', async ({ request }) => {
    const body = (await request.json()) as { amount?: number };
    const added = body.amount ? Math.max(1, Math.round(body.amount / 1000)) : 5;
    store.coins += added;
    return HttpResponse.json(apiWrap({
      coinAmount: added,
      balance: store.coins,
      paymentKey: 'demo_payment_key',
    }));
  }),
  http.get('/api/subscription/current', () =>
    HttpResponse.json(apiWrap({
      subscriptionId: 'sub_demo_portfolio',
      planCode: 'pro',
      status: 'active',
      startedAt: '2026-05-01T00:00:00+09:00',
      endsAt: '2026-07-01T00:00:00+09:00',
      autoRenew: true,
      nextBillingDate: '2026-07-01T00:00:00+09:00',
      cancelledAt: null,
    })),
  ),
  http.get('/api/subscription/plans', () =>
    HttpResponse.json(apiWrap([...SUBSCRIPTION_PLANS])),
  ),
  http.get('/api/subscription/history', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 10);
    const content = SUBSCRIPTION_HISTORY.slice(page * size, page * size + size);
    return HttpResponse.json(apiWrap({
      content,
      page,
      size,
      totalElements: SUBSCRIPTION_HISTORY.length,
      totalPages: Math.ceil(SUBSCRIPTION_HISTORY.length / size) || 1,
      hasNext: (page + 1) * size < SUBSCRIPTION_HISTORY.length,
      hasPrevious: page > 0,
    }));
  }),
  http.post('/api/subscription/checkout', async ({ request }) => {
    const body = (await request.json()) as { planCode?: string };
    const plan = SUBSCRIPTION_PLANS.find((p) => p.code === body.planCode) ?? SUBSCRIPTION_PLANS[1];
    return HttpResponse.json(apiWrap({
      orderId: `demo_sub_${Date.now()}`,
      tossPaymentsClientKey: DEMO_TOSS_CLIENT_KEY,
      amount: plan.priceKrw,
      orderName: `${plan.code.toUpperCase()} 구독`,
      customerEmail: store.user.email,
      customerName: store.user.nickname,
    }));
  }),
  http.post('/api/subscription/confirm', async ({ request }) => {
    const body = (await request.json()) as { orderId?: string };
    const planCode = body.orderId?.includes('premium') ? 'premium' : 'pro';
    store.subscription = { status: 'active', planCode };
    return HttpResponse.json(apiWrap({
      subscriptionId: 'sub_demo_portfolio',
      planCode,
      status: 'active',
      startedAt: new Date().toISOString(),
      endsAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      receiptUrl: '',
    }));
  }),

  // Calendar / Report
  http.get('/api/calendar', ({ request }) => {
    const url = new URL(request.url);
    const year = Number(url.searchParams.get('year') ?? new Date().getFullYear());
    const month = Number(url.searchParams.get('month') ?? new Date().getMonth() + 1);
    return HttpResponse.json(apiWrap({
      year,
      month,
      events: [],
      studyDays: [1, 3, 5, 8, 12, 15, 18, 22, 25, 28],
      streakDays: 7,
    }));
  }),
  http.get('/api/calendar/missions/today', () =>
    HttpResponse.json(apiWrap({
      date: new Date().toISOString().slice(0, 10),
      missions: [
        { id: 'm1', title: '오늘의 학습', description: '슬라이드 1개 완료', type: 'lesson', current: 1, target: 1, xpReward: 10, isCompleted: true },
      ],
    })),
  ),
  http.get('/api/calendar/streak', () =>
    HttpResponse.json(apiWrap({
      currentStreak: 7,
      longestStreak: 14,
      totalStudyDays: 42,
      heatmap: buildStreakHeatmap(),
    })),
  ),
  http.get('/api/reports/weekly', () =>
    HttpResponse.json(apiWrap({
      weekLabel: '이번 주',
      totalStudyMinutes: 180,
      completedLessons: 3,
      streakDays: 7,
      totalQuizCount: 24,
      averageScorePercent: 92,
      dailyStats: buildWeeklyDailyStats(),
      dailyStudyGoalMinutes: 60,
      completedAssignmentCount: 2,
      assignmentAvgScore: 85,
    })),
  ),
  http.get('/api/reports/progress', () =>
    HttpResponse.json(apiWrap({
      totalCourses: store.courses.length,
      completedCourses: store.courses.length,
      subjects: store.courses.map((c) => ({
        subject: c.subject,
        totalChapters: c.totalLessons,
        completedChapters: c.completedLessons,
        progressPercent: c.progress,
      })),
    })),
  ),
  http.get('/api/reports/study-time', () => {
    const { dailyData, totalMinutes, averageMinutesPerDay } = buildMonthlyDailyData();
    return HttpResponse.json(apiWrap({
      period: 'week',
      totalMinutes,
      averageMinutesPerDay,
      totalQuizCount: 80,
      averageScorePercent: 90,
      dailyData,
    }));
  }),
  http.get('/api/reports/concept-understanding', ({ request }) => {
    const url = new URL(request.url);
    const courseId = url.searchParams.get('courseId') ?? store.courses[0]?.id;
    const course = findCourse(courseId ?? '');
    const chapters = course?.chapters.map((ch) => ({
      chapterId: ch.id,
      chapterTitle: ch.title,
      totalQuizCount: ch.quizzes.length,
      correctCount: ch.quizzes.length,
      accuracyPercent: 100,
    })) ?? [];
    return HttpResponse.json(apiWrap({
      overallAccuracy: 95,
      chapters,
    }));
  }),
];