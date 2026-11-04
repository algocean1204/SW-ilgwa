// 슬라이드가 없거나 커리큘럼이 확정되지 않은 과외에 들어왔을 때의 안내 화면.
// 학습실에 빈 슬라이드를 보여 주지 않고 갈 화면을 안내한다.
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import type { CourseStatus } from '@/apis';
import Icon from '@/Icon';

interface LearningNotReadyProps {
  /** 현재 과외 진행 상태. 안내 문구를 고르는 데 쓴다 */
  courseStatus: CourseStatus | null;
  palette: Palette;
  setRoute: RouteSetter;
}

/** 과외 상태별 안내 문구 매핑 */
function getNotice(status: CourseStatus | null): { title: string; body: string } {
  if (status === 'CURRICULUM_GENERATING') {
    return {
      title: 'AI가 커리큘럼을 만들고 있어요',
      body: '커리큘럼 생성이 완료되면 학습을 시작할 수 있습니다.\n커리큘럼 확인 화면에서 진행 상황을 볼 수 있어요.',
    };
  }
  if (status === 'CURRICULUM_READY') {
    return {
      title: '커리큘럼 확정이 필요해요',
      body: '커리큘럼 검토 후 확정하면 강의 생성이 시작됩니다.\n커리큘럼 확인 화면으로 이동해 주세요.',
    };
  }
  if (status === 'CURRICULUM_FAILED') {
    return {
      title: '커리큘럼 생성에 실패했습니다',
      body: '커리큘럼 확인 화면에서 다시 시도하거나 새 과외를 만들어 주세요.',
    };
  }
  // ACTIVE인데 슬라이드가 0개면 강의 생성 중이다
  return {
    title: 'AI가 강의 슬라이드를 만들고 있어요',
    body: '첫 번째 챕터의 슬라이드가 준비되면 학습을 시작할 수 있어요.\n잠시 후 다시 확인해 주세요.',
  };
}

export function LearningNotReady({ courseStatus, palette, setRoute }: LearningNotReadyProps) {
  const { title, body } = getNotice(courseStatus);
  const isFailed = courseStatus === 'CURRICULUM_FAILED';

  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      minHeight: 320, padding: '48px 24px', textAlign: 'center',
    }}>
      <div style={{
        width: 64, height: 64, borderRadius: '50%',
        background: isFailed ? 'var(--state-danger-bg)' : palette.wash,
        display: 'grid', placeItems: 'center', marginBottom: 20,
      }}>
        <Icon
          name={isFailed ? 'warning' : 'sparkle'}
          size={28}
          color={isFailed ? 'var(--state-danger)' : palette.primary}
        />
      </div>

      <h2 style={{ fontSize: 18, fontWeight: 800, color: 'var(--ink)', margin: '0 0 10px' }}>
        {title}
      </h2>
      <p style={{
        fontSize: 13, color: 'var(--ink-3)', lineHeight: 1.7,
        margin: '0 0 24px', maxWidth: 320, whiteSpace: 'pre-line',
      }}>
        {body}
      </p>

      {/* 커리큘럼 확인 화면으로 이동 */}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'center' }}>
        <button
          onClick={() => setRoute('courseDetail')}
          style={{
            padding: '10px 18px', borderRadius: 10,
            fontSize: 13, fontWeight: 800,
            background: palette.primary, color: 'var(--paper)',
            border: 'none',
          }}
        >
          커리큘럼 확인으로 이동
        </button>
        <button
          onClick={() => setRoute('myCourses')}
          style={{
            padding: '10px 18px', borderRadius: 10,
            fontSize: 13, fontWeight: 700,
            background: palette.wash, color: palette.primary,
            border: `1px solid ${palette.soft}`,
          }}
        >
          내 과외 목록
        </button>
      </div>
    </div>
  );
}
