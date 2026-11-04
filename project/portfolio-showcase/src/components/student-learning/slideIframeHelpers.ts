// SlideIframe의 높이 측정·스타일 주입 헬퍼.
// SlideIframe.tsx에서 DOM 헬퍼만 분리했다.

/** iframe 높이 계산 시 사용할 최솟값 */
export const MIN_HEIGHT = 320;

/**
 * DB의 slide.content는 <iframe srcdoc="HTML엔티티"> 형태로 저장된다.
 * SlideIframe이 이를 그대로 srcDoc으로 쓰면 중첩 iframe이 생겨
 * 바깥 body의 scrollHeight가 내부 iframe의 기본 높이(~150px)로 collapse된다.
 *
 * srcdoc 속성값을 꺼내 HTML 엔티티를 디코딩한 실제 슬라이드 HTML을 돌려준다.
 * srcdoc 속성이 없으면 원본 문자열을 그대로 돌려준다(raw HTML 호환).
 */
export function extractSlideHtml(rawContent: string): string {
  // srcdoc=" ... " 패턴 추출 (큰따옴표 내부의 HTML 엔티티 인코딩된 값)
  const match = rawContent.match(/srcdoc="([\s\S]*?)"\s*(?:style=|>)/);
  // srcdoc 포맷이 아닌 raw HTML이면 첫 헤딩만 지우고 돌려준다
  if (!match) return stripFirstHeading(rawContent);

  // HTML 엔티티는 브라우저 내장 파서로 디코딩한다.
  const parser = new DOMParser();
  const doc = parser.parseFromString(
    `<!doctype html><body><span>${match[1]}</span></body>`,
    'text/html',
  );
  const html = doc.querySelector('span')?.textContent ?? rawContent;

  // 슬라이드 HTML 내부의 첫 번째 헤딩은 LearningContent의 slide.title과 중복된다.
  // 헤딩 레벨(h1~h3)에 상관없이 body의 첫 번째 헤딩 요소를 제거한다.
  return stripFirstHeading(html);
}

/**
 * HTML 문자열에서 body 내 첫 번째 헤딩 요소(h1~h3)를 제거한다.
 * 슬라이드 생성 방식에 따라 타이틀이 h1 또는 h2로 저장될 수 있어 레벨을 고정하지 않는다.
 */
function stripFirstHeading(html: string): string {
  if (typeof window === 'undefined') return html;
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const heading = doc.body.querySelector('h1, h2, h3');
  if (heading) heading.remove();
  return doc.documentElement.outerHTML;
}

/**
 * iframe contentDocument의 전체 콘텐츠 높이를 여러 기준으로 측정해 최댓값을 반환한다.
 * extractSlideHtml로 중첩 iframe을 해소한 뒤 이 함수를 호출한다.
 * body.scrollHeight, documentElement.scrollHeight, 루트 자식 요소의
 * getBoundingClientRect().bottom 세 기준 중 최댓값을 사용한다.
 */
export function measureContentHeight(doc: Document): number {
  const de = doc.documentElement;
  const body = doc.body;

  // 콘텐츠 루트(body 첫 번째 자식 요소, 주로 <section>)의 실제 렌더 하단
  const firstChild = body.firstElementChild;
  const childBottom = firstChild
    ? firstChild.getBoundingClientRect().bottom
    : 0;

  const measured = Math.max(
    MIN_HEIGHT,
    body.scrollHeight,
    de.scrollHeight,
    Math.ceil(childBottom),
  );

  return Math.ceil(measured);
}

/**
 * 슬라이드 HTML이 section{min-height:100%}·html,body{height:100%}를 선언해
 * iframe scrollHeight를 현재 iframe 높이로 collapse시키는 문제를 중화한다.
 *
 * !important 없이 specificity가 높은 선택자를 head 끝에 넣어 덮어쓴다.
 * sandbox="allow-same-origin"이 있어야 contentDocument 접근이 허용된다.
 */
export function injectNeutralStyle(doc: Document): void {
  const id = '__slide-neutral-style__';
  // 이미 주입됐으면 다시 넣지 않는다
  if (doc.getElementById(id)) return;

  const style = doc.createElement('style');
  style.id = id;
  // html, body, section의 고정 높이 지정을 자연 높이로 중화한다.
  // height:auto, min-height:0으로 scrollHeight가 실제 콘텐츠를 반영하게 한다.
  // 슬라이드의 패딩·배경·타이포그래피 스타일은 건드리지 않는다.
  style.textContent = `
    html, body { margin: 0; padding: 0; height: auto; min-height: 0; }
    section, section.visual-slide { min-height: 0; height: auto; }
  `;
  doc.head.appendChild(style);
}
