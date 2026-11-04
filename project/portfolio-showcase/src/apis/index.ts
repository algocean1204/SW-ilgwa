export type {
  ApiResponse,
  ErrorResponse,
  OffsetPageResponse,
  PageResponse,
} from './types';
export { ApiError } from './types';
export {
  AUTH_EXPIRED_EVENT,
  apiClient,
  clearAccessToken,
  getAccessToken,
  markBootstrapComplete,
  setAccessToken,
} from './client';

export type {
  DevLoginResponse,
  GoogleLoginRequest,
  KakaoLoginRequest,
  LoginResponse,
  SubscriptionStatus,
  TokenRefreshResponse,
} from './auth/auth.type';
export {
  devLogin,
  loginWithGoogle,
  loginWithKakao,
  logout,
  refresh,
  withdraw,
} from './auth/auth.api';

export type {
  NotificationsDto,
  NotificationsResponse,
  OnboardingRequest,
  OnboardingResponse,
  RecommendedTutorDto,
  SettingsResponse,
  TelegramLinkResponse,
  TelegramPreferencesResponse,
  TelegramStatusResponse,
  UpdateProfileRequest,
  UpdateSettingsRequest,
  UpdateTelegramPreferencesRequest,
  UserProfileImageResponse,
  UserProfileResponse,
  UserStatsResponse,
} from './user/user.type';
export {
  completeOnboarding,
  deleteUserProfileImage,
  getMyStats,
  getProfile,
  getTelegramPreferences,
  getTelegramStatus,
  linkTelegram,
  updateProfile,
  updateSettings,
  updateTelegramPreferences,
  uploadUserProfileImage,
} from './user/user.api';

export type {
  ChapterDetailResponse,
  ChapterStatus,
  ChapterSummaryResponse,
  BookmarkInChapterDto,
  CourseDetailResponse,
  CourseSummaryResponse,
  CourseStatus,
  CreateTutoringRequest,
  CurriculumResponse,
  GetMyCoursesParams,
  LessonGenerationStatusResponse,
  RecentTutoringItem,
  RecentTutoringResponse,
  SourcePdfUploadResponse,
  TutoringSourceType,
  UpdateTutoringRequest,
} from './tutoring/tutoring.type';
export {
  confirmCurriculum,
  createTutoring,
  deleteTutoring,
  getChapter,
  getChapters,
  getCourse,
  getCurriculum,
  getLessonGenerationStatus,
  getMyCourses,
  getRecentTutoring,
  updateTutoring,
  uploadTutoringSourcePdf,
} from './tutoring/tutoring.api';

export type {
  ProgressRequest,
  QuizAnswerRequest,
  QuizListResponse,
  QuizResponse,
  QuizResultResponse,
  SingleAnswer,
  SingleResult,
  SlideListResponse,
  SlideResponse,
} from './lesson/lesson.type';
export {
  getQuizAnswers,
  getQuizzes,
  getSlides,
  recordProgress,
  submitQuizAnswers,
} from './lesson/lesson.api';

export type {
  BookmarkResponse,
  CreateBookmarkRequest,
  CreateNoteRequest,
  GetAllNotesParams,
  NoteListResponse,
  NoteResponse,
  TelegramSendResponse,
  UpdateNoteRequest,
} from './note/note.type';
export {
  createBookmark,
  createLessonNote,
  deleteLessonNote,
  getAllNotes,
  getLessonNotes,
  sendNoteToTelegram,
  toggleLessonNoteStar,
  updateLessonNote,
} from './note/note.api';

export type {
  ChatHistoryItem,
  ChatHistoryResponse,
  ChatRequest,
  ChatResponse,
} from './chat/chat.type';
export {
  getChatHistory,
  sendChat,
} from './chat/chat.api';

export type {
  AnswerItem,
  AssignmentDetailResponse,
  AssignmentListResponse,
  GradeResponse,
  QuestionItem,
  SubmissionResponse,
  SubmitAnswerRequest,
} from './assignment/assignment.type';
export {
  getAssignment,
  getAssignments,
  getGrades,
  submitAssignment,
} from './assignment/assignment.api';

export type {
  AttemptDetailResponse,
  AttemptQuestion,
  AttemptStatus,
  AttemptStatusResponse,
  ExamResultResponse,
  GenerateExamRequest,
  GenerateExamResponse,
  GenerationOutcome,
  GenerationOutcomeMeta,
  MockExamDetailResponse,
  MockExamListResponse,
  MockExamQuestionResponse,
  MockExamResultQuestionResponse,
  MockExamResultResponse,
  MockExamStatus,
  QuestionOption,
  QuestionResultItem,
  RubricBreakdownItem,
  SubmitAttemptRequest,
  SubmitExamAnswerItem,
  SubmitExamRequest,
  SubmittedAnswerItem,
} from './mock-exam/mock-exam.type';
export {
  generateExam,
  generateExamForge,
  getAttemptDetail,
  getAttemptResult,
  getAttemptStatus,
  getExamDetail,
  getExamList,
  getExamResult,
  submitAttempt,
  submitExam,
} from './mock-exam/mock-exam.api';

export type {
  CoinBalanceResponse,
  CoinBundleResponse,
  CoinCheckoutResponse,
  CoinConfirmRequest,
  CoinConfirmResponse,
  CoinHistoryItem,
  CoinHistoryResponse,
  CoinPurchaseRequest,
  CoinTransactionType,
  CoinUseExamRequest,
  CoinUseResponse,
  CoinUseSlideRequest,
  GetCoinHistoryParams,
} from './coin/coin.type';
export {
  confirmCoinPayment,
  getCoinBalance,
  getCoinBundles,
  getCoinHistory,
  purchaseCoin,
  useCoinForExam,
  useCoinForSlide,
} from './coin/coin.api';

export type {
  CreateTutorRequest,
  CreateTutorResponse,
  PreviewTutorRequest,
  PreviewTutorResponse,
  ProfileImageResponse,
  TutorListResponse,
  TutorResponse,
  UpdateTutorRequest,
  VoiceSampleResponse,
} from './tutor/tutor.type';
export {
  createTutor,
  deleteAllCustomTutors,
  deleteTutorProfileImage,
  deleteTutor,
  getMyTutors,
  getTutor,
  previewTutor,
  resetTutor,
  updateTutor,
  uploadTutorProfileImage,
  uploadVoiceSample,
} from './tutor/tutor.api';

export type {
  CalendarEventResponse,
  CalendarEventStatus,
  CalendarEventType,
  CalendarResponse,
  CreateCalendarEventRequest,
  MissionItem,
  MissionResponse,
  StreakResponse,
  UpdateCalendarEventRequest,
} from './calendar/calendar.type';
export {
  createCalendarEvent,
  getCalendar,
  getMissionsToday,
  getStreak,
  updateCalendarEvent,
} from './calendar/calendar.api';

export type {
  CancelResponse,
  CheckoutRequest,
  CheckoutResponse,
  ConfirmRequest,
  ConfirmResponse,
  CurrentSubscriptionResponse,
  DowngradeRequest,
  DowngradeResponse,
  GetSubscriptionHistoryParams,
  HistoryResponse,
  PaymentStatus,
  PlanListResponse,
  SubscriptionPlanCode,
  SubscriptionStatusType,
  UpgradeRequest,
  UpgradeResponse,
} from './subscription/subscription.type';
export {
  cancelSubscription,
  checkout,
  confirmSubscription,
  downgradeSubscription,
  getCurrentSubscription,
  getPlans,
  getSubscriptionHistory,
  upgradeSubscription,
} from './subscription/subscription.api';

export type {
  ChapterAccuracy,
  ConceptUnderstandingResponse,
  DailyStat,
  DailyStudyTime,
  ProgressReportResponse,
  StudyTimeReportResponse,
  SubjectProgress,
  WeeklyReportResponse,
} from './report/report.type';
export {
  getConceptUnderstanding,
  getProgressReport,
  getStudyTimeReport,
  getWeeklyReport,
} from './report/report.api';

export type {
  AddRecipientRequest,
  AdminDashboardResponse,
  AdminSubscriptionStatus,
  AdminTelegramResponse,
  AdminUserListResponse,
  AdminUserRole,
  AdminUserStatus,
  AdminUserSuspendResponse,
  AlertEventType,
  AlertRuleListResponse,
  AlertRuleResponse,
  AlertSeverity,
  BroadcastRequest,
  BroadcastResponse,
  GetAdminUsersParams,
  GetTelegramMessagesParams,
  RecipientListResponse,
  RecipientResponse,
  SuspendRequest,
  TelegramChatIdAction,
  TelegramChatIdRequest,
  TelegramCredentialResponse,
  TelegramCredentialUpdateRequest,
  TelegramCredentialUpdateResponse,
  TelegramMessageItem,
  TelegramMessageListResponse,
  TelegramSessionItem,
  TelegramSessionListResponse,
  UpdateAlertRuleRequest,
} from './admin/admin.type';
export {
  addRecipient,
  deleteRecipient,
  getAdminDashboard,
  getAdminUsers,
  getAlertRules,
  getRecipients,
  getTelegramMessages,
  getTelegramSessions,
  getTelegramCredential,
  sendBroadcast,
  setUserTelegramChatId,
  suspendUser,
  updateAlertRule,
  updateTelegramCredential,
} from './admin/admin.api';
