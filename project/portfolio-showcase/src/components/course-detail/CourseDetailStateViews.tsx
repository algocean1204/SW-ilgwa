// 로딩·오류·생성중 상태 화면을 한 경로로 묶어 내보낸다.
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import Icon from '@/Icon';
import { Breadcrumb } from './Breadcrumb';

// 생성중 진행률 화면은 파일이 커서 별도 모듈로 뺐다
export { CurriculumGeneratingView } from './CurriculumGeneratingView';
export type { CurriculumGeneratingViewProps } from './CurriculumGeneratingView';

interface LoadErrorViewProps {
  palette: Palette;
  message: string;
  setRoute: RouteSetter;
}

export function CourseLoadErrorView({ palette, message, setRoute }: LoadErrorViewProps) {
  return (
    <div style={{
      maxWidth: 'var(--page-max-w)', margin: '0 auto',
      padding: 'var(--page-py) var(--page-px) 56px', textAlign: 'center',
    }}>
      <Breadcrumb setRoute={setRoute} />
      <div style={{ padding: '60px 20px' }}>
        <h2 style={{ fontSize: 20, fontWeight: 800, color: 'var(--text-primary)', margin: 0 }}>
          과외 정보를 불러오지 못했습니다
        </h2>
        <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginTop: 8, lineHeight: 1.6 }}>
          {message}
        </p>
        <div style={{ marginTop: 20, display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap' }}>
          <button
            onClick={() => setRoute('explore')}
            style={{
              padding: '10px 18px', borderRadius: 10,
              fontSize: 13, fontWeight: 800,
              background: palette.primary, color: 'var(--paper)',
            }}
          >
            새 과외 만들기
          </button>
          <button
            onClick={() => setRoute('myCourses')}
            style={{
              padding: '10px 18px', borderRadius: 10,
              fontSize: 13, fontWeight: 800,
              background: palette.wash, color: palette.primary,
              border: `1px solid ${palette.soft}`,
            }}
          >
            내 과외 보기
          </button>
        </div>
      </div>
    </div>
  );
}

interface FailedViewProps {
  palette: Palette;
  onRetry: () => void;
  setRoute: RouteSetter;
}

/** 커리큘럼 생성 실패(CURRICULUM_FAILED) 안내와 재시도 버튼 */
export function CurriculumFailedView({ palette, onRetry, setRoute }: FailedViewProps) {
  return (
    <div style={{
      maxWidth: 'var(--page-max-w)', margin: '0 auto',
      padding: 'var(--page-py) var(--page-px) 56px', textAlign: 'center',
    }}>
      <Breadcrumb setRoute={setRoute} />
      <div style={{ padding: '60px 20px' }}>
        <div style={{
          width: 56, height: 56, borderRadius: '50%',
          background: 'var(--state-danger-bg)', margin: '0 auto',
          display: 'grid', placeItems: 'center',
        }}>
          <Icon name="warning" size={28} color="var(--state-danger)" />
        </div>
        <h2 style={{
          fontSize: 20, fontWeight: 800, color: 'var(--text-primary)',
          marginTop: 16, margin: '16px 0 0',
        }}>
          커리큘럼 생성에 실패했습니다
        </h2>
        <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginTop: 8, lineHeight: 1.6 }}>
          AI가 커리큘럼을 생성하는 도중 오류가 발생했습니다.<br />
          잠시 후 다시 시도하거나, 새 과외를 만들어 주세요.
        </p>
        <div style={{ marginTop: 20, display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap' }}>
          <button
            onClick={onRetry}
            style={{
              padding: '10px 18px', borderRadius: 10,
              fontSize: 13, fontWeight: 800,
              background: palette.primary, color: 'var(--paper)',
              border: 'none',
            }}
          >
            다시 시도
          </button>
          <button
            onClick={() => setRoute('explore')}
            style={{
              padding: '10px 18px', borderRadius: 10,
              fontSize: 13, fontWeight: 800,
              background: palette.wash, color: palette.primary,
              border: `1px solid ${palette.soft}`,
            }}
          >
            새 과외 만들기
          </button>
          <button
            onClick={() => setRoute('myCourses')}
            style={{
              padding: '10px 18px', borderRadius: 10,
              fontSize: 13, fontWeight: 800,
              background: 'var(--surface-card)', color: 'var(--text-secondary)',
              border: '1px solid var(--border-default)',
            }}
          >
            내 과외 목록
          </button>
        </div>
      </div>
    </div>
  );
}
