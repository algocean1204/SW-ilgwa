// 새 과외 생성 페이지 상단 헤더. 타이포는 PageIntro 규칙을 따른다.
import type { Palette } from '@/lib';
import PageIntro from '@/components/ui/PageIntro';

interface ExploreHeaderProps {
  palette: Palette;
}

export function ExploreHeader({ palette }: ExploreHeaderProps) {
  return (
    <div style={{ marginBottom: 22 }}>
      <PageIntro
        eyebrow="NEW TUTORING"
        title={
          <>
            새{' '}
            <span className="script" style={{ fontSize: 34, color: palette.primary }}>
              과외
            </span>{' '}
            만들기
          </>
        }
        description={
          <>
            교재가 있으면 PDF를 올리고, 없으면{' '}
            <b style={{ color: 'var(--text-primary)' }}>주제만 써주세요</b>. 커리큘럼은 자동으로 정리됩니다.
          </>
        }
      />
    </div>
  );
}
