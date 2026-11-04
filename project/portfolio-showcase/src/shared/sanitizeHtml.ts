// HTML 새니타이징. XSS를 막으려고 허용한 태그/속성만 남긴다.
import DOMPurify from 'dompurify';
import { marked } from 'marked';

// 노트 콘텐츠에서 허용할 태그와 속성
const ALLOWED_TAGS = [
  'h1', 'h2', 'h3', 'h4', 'p', 'br', 'strong', 'b', 'em', 'i', 'u',
  'code', 'pre', 'ul', 'ol', 'li', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
  'a', 'span', 'div', 'blockquote', 'hr', 'img', 'sup', 'sub',
];

const ALLOWED_ATTR = ['href', 'target', 'rel', 'src', 'alt', 'class', 'style'];

/**
 * 서버가 준 HTML을 DOMPurify로 새니타이징한다.
 * 허용한 태그·속성만 남기고 스크립트, 이벤트 핸들러 등은 지운다.
 */
export function sanitizeHtml(dirty: string): string {
  return DOMPurify.sanitize(dirty, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
  });
}

/**
 * 마크다운을 HTML로 바꾼 뒤 새니타이징한다. 노트 본문이 마크다운일 때 쓴다.
 */
export function renderMarkdown(md: string): string {
  const html = marked.parse(md, { async: false, breaks: true }) as string;
  return sanitizeHtml(html);
}
