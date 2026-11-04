// 새 AI 과외 생성 페이지. 입력 패널, 설정, 생성 요청을 묶는다.
import { useState } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import type { AppState, RouteSetter } from '@/shared/types';
import { ExploreHeader }   from '@/components/course-explore/ExploreHeader';
import { ModeToggle }      from '@/components/course-explore/ModeToggle';
import type { CourseExploreMode } from '@/components/course-explore/ModeToggle';
import { PdfUploadPanel }  from '@/components/course-explore/PdfUploadPanel';
import { TopicInputPanel } from '@/components/course-explore/TopicInputPanel';
import type { TopicCategory } from '@/components/course-explore/TopicInputPanel';
import { SettingsPanel }   from '@/components/course-explore/SettingsPanel';
import { ExploreAside }    from '@/components/course-explore/ExploreAside';
import { submitCourseExplore } from '@/components/course-explore/courseExploreSubmit';
import { useCourseExploreTutors } from '@/components/course-explore/useCourseExploreTutors';

interface CourseExploreProps {
  setRoute: RouteSetter;
  appState: AppState;
}

function CourseExplore({ setRoute, appState }: CourseExploreProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;

  const [mode,     setMode]     = useState<CourseExploreMode>('pdf');
  const [topic,    setTopic]    = useState('');
  const [selectedCategory, setSelectedCategory] = useState<TopicCategory | null>(null);
  const [pdfFile, setPdfFile] = useState<File | null>(null);
  const [duration, setDuration] = useState(30);
  const [depth,    setDepth]    = useState('normal');
  // 사용자가 고른 강의(챕터) 수. AI 생성 요청에 반영한다.
  const [lessonCount, setLessonCount] = useState(6);
  const { teacher, setTeacher, defaultTutors, customTutors, teachers, tutorLoadError } = useCourseExploreTutors();
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  return (
    <div style={{ maxWidth: 'var(--page-max-w)', margin: '0 auto', padding: 'var(--page-py) var(--page-px) 56px' }}>

      {/* 상단 헤더 */}
      <ExploreHeader palette={palette} />

      {/* 2컬럼 그리드: 좌측 입력, 우측 안내 카드 */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.3fr .68fr', gap: 18 }}>

        {/* 좌측 입력 영역. 각 id는 PAGE_CONTEXT_MANIFEST feature id와 같다 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>

          {/* PDF 시작 섹션: ModeToggle과 PDF 업로드 패널 */}
          <div id="pdf-start">
            <ModeToggle mode={mode} palette={palette} onModeChange={setMode} />
          </div>

          {/* 모드별 입력 패널 분기 */}
          {mode === 'pdf' ? (
            <PdfUploadPanel
              file={pdfFile}
              palette={palette}
              disabled={submitting}
              onFileSelect={setPdfFile}
              onFileClear={() => setPdfFile(null)}
            />
          ) : (
            <div id="topic-start">
              <TopicInputPanel
                topic={topic}
                palette={palette}
                selectedCategory={selectedCategory}
                onTopicChange={setTopic}
                onCategoryChange={setSelectedCategory}
              />
            </div>
          )}

          {/* 세부 설정 카드. 튜터 선택과 기간 조정이 함께 있어 사이드바의 */}
          {/* tutor-choose, duration-adjust 항목 모두 id="tutor-choose"로 스크롤한다 */}
          <div id="tutor-choose">
            <div id="duration-adjust">
              <SettingsPanel
                duration={duration}
                depth={depth}
                teacher={teacher}
                lessonCount={lessonCount}
                defaultTutors={defaultTutors}
                customTutors={customTutors}
                palette={palette}
                setRoute={setRoute}
                onDurationChange={setDuration}
                onDepthChange={setDepth}
                onTeacherChange={setTeacher}
                onLessonCountChange={setLessonCount}
              />
              {tutorLoadError && (
                <div
                  role="alert"
                  style={{
                    marginTop: 8, padding: '10px 12px', borderRadius: 10,
                    background: 'var(--coral-wash)', border: '1px solid var(--coral-bright)',
                    color: 'var(--danger)', fontSize: 13, fontWeight: 700, lineHeight: 1.5,
                  }}
                >
                  {tutorLoadError}
                </div>
              )}
            </div>
          </div>

          {/* 과외 생성 버튼 */}
          <button
            disabled={submitting}
            onClick={async () => {
              setSubmitting(true);
              setSubmitError(null);
              try {
                await submitCourseExplore({
                  mode, topic, selectedCategory, pdfFile, duration, depth,
                  lessonCount,
                  teacher, teachers, appState, setRoute, setSubmitError,
                });
              } finally { setSubmitting(false); }
            }}
            className="lift"
            style={{
              padding: '16px 20px', borderRadius: 12, fontSize: 14, fontWeight: 800,
              background: palette.primary, color: 'var(--paper)',
              boxShadow: `0 14px 32px -14px ${palette.primary}`,
              opacity: submitting ? 0.6 : 1,
            }}
          >
            {submitting ? '생성 중...' : '맞춤 커리큘럼 만들기'}
          </button>
          {submitError && (
            <div
              role="alert"
              style={{
                padding: '12px 14px',
                borderRadius: 10,
                background: 'var(--coral-wash)',
                border: '1px solid var(--coral-bright)',
                color: 'var(--danger)',
                fontSize: 13,
                fontWeight: 700,
                lineHeight: 1.5,
              }}
            >
              {submitError}
            </div>
          )}
        </div>

        {/* 우측 안내 카드(sticky) */}
        <ExploreAside palette={palette} />
      </div>
    </div>
  );
}

export default CourseExplore;
