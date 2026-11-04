// 메인 콘텐츠(좌)와 보조 aside(우) 레이아웃.
// main은 1fr, aside는 고정폭(asideWidth px, 기본 300)이고 gap은 --section-gap 토큰이다.
// 1023px 이하에서는 aside를 main 아래로 스택 배치한다.
// 페이지별 허용 폭: StudentHome 280, Report 300, MemoryNotes 320, StudyCalendar 260
import type { ReactNode, CSSProperties } from 'react';

interface PageContentWithAsideProps {
  // 좌측 주요 콘텐츠 영역
  main: ReactNode;
  // 우측 부가 정보 영역
  aside: ReactNode;
  // aside 상단에 표시할 선택적 제목
  asideTitle?: string;
  // aside 고정 폭(px). 기본 300px.
  asideWidth?: number;
}

// aside는 보조 영역이라 약한 배경과 작은 글자를 쓴다
function PageContentWithAside({
  main,
  aside,
  asideTitle,
  asideWidth = 300,
}: PageContentWithAsideProps) {
  // aside 폭은 --aside-w로 넘기고 lg:grid-cols-[1fr_var(--aside-w)]가 읽는다.
  // 1023px 이하에서는 단일 컬럼 grid라 aside가 main 아래로 내려간다.
  const containerStyle: CSSProperties = {
    ['--aside-w' as string]: `${asideWidth}px`,
  };

  // sticky top을 CSS 변수로 둬 사이드바 높이가 바뀌어도 따라간다
  const asideStyle: CSSProperties = {
    top: 'var(--aside-top-offset)',
  };

  // aside 제목은 약한 색으로 보조 영역임을 드러낸다
  const asideTitleStyle: CSSProperties = {
    fontSize: 10,
    fontWeight: 700,
    color: 'var(--text-tertiary)',
    letterSpacing: '.12em',
    textTransform: 'uppercase',
    marginBottom: 8,
  };

  return (
    // 1023px 이하는 단일 컬럼이고 lg(1024px+)부터 aside 고정폭 컬럼이 생긴다.
    // gridTemplateColumns를 lg: 클래스로만 줘서 모바일에서는 세로로 쌓인다.
    <div
      className="grid gap-[var(--section-gap)] lg:grid-cols-[1fr_var(--aside-w)]"
      style={containerStyle}
    >
      <div>{main}</div>

      {/* 보조 정보. lg 이상에서만 sticky로 붙는다 */}
      <aside
        className="flex flex-col gap-[14px] lg:sticky"
        style={asideStyle}
      >
        {asideTitle && (
          <div style={asideTitleStyle}>{asideTitle}</div>
        )}
        {aside}
      </aside>
    </div>
  );
}

export default PageContentWithAside;
