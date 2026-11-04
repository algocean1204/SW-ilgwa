// DB에 저장된 ChapterStudio_V1 슬라이드를 격리된 iframe으로 그린다.
// sandbox="allow-same-origin"으로 부모가 contentDocument에 접근하게 하고,
// allow-scripts는 XSS를 막으려고 일부러 뺐다.
//
// DB의 slide.content는 <iframe srcdoc="HTML엔티티"> 형태로 저장된다.
// 그대로 srcDoc에 넣으면 바깥 body 높이가 내부 iframe 기본 높이(~150px)가 되어
// MIN_HEIGHT(320)에 고정된다.
// 그래서 extractSlideHtml로 srcdoc 안 HTML을 꺼내 디코딩한 뒤 srcDoc에 직접 넣고,
// injectNeutralStyle로 section·html·body의 min-height:100% collapse를 중화한다.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  MIN_HEIGHT,
  extractSlideHtml,
  injectNeutralStyle,
  measureContentHeight,
} from './slideIframeHelpers';

interface SlideIframeProps {
  title: string;
  iframeHtml: string;
}

/** 앱 DOM과 슬라이드 DOM을 격리해 CSS 충돌과 XSS 위험을 낮춘다. */
export function SlideIframe({ title, iframeHtml }: SlideIframeProps) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [height, setHeight] = useState(MIN_HEIGHT);

  // DB content는 <iframe srcdoc="..."> 형식이라 내부 HTML을 꺼내 중첩 iframe을 피한다.
  // iframeHtml이 바뀔 때(슬라이드 교체)만 다시 계산한다.
  const resolvedHtml = useMemo(() => extractSlideHtml(iframeHtml), [iframeHtml]);

  // 여러 cleanup 함수를 ref 하나에 모아 빠짐없이 해제한다
  const cleanupRef = useRef<(() => void)[]>([]);

  /** 높이가 1px 이상 바뀔 때만 setState해 불필요한 리렌더를 줄인다 */
  const syncHeight = useCallback(() => {
    const doc = iframeRef.current?.contentDocument;
    if (!doc) return;
    const next = measureContentHeight(doc);
    setHeight((prev) => (Math.abs(next - prev) >= 1 ? next : prev));
  }, []);

  /** 이전 슬라이드용 Observer·리스너를 모두 해제한다 */
  const teardown = useCallback(() => {
    cleanupRef.current.forEach((fn) => fn());
    cleanupRef.current = [];
  }, []);

  /** onLoad: 보정 스타일 주입 → 높이 측정 → 다중 재측정 트리거 등록 */
  const handleLoad = useCallback(() => {
    teardown();

    const iframe = iframeRef.current;
    const doc = iframe?.contentDocument;
    if (!doc) return;

    // section{min-height:100%} collapse를 중화하는 보정 스타일을 넣는다
    injectNeutralStyle(doc);

    // 주입 직후 첫 높이를 잰다
    syncHeight();

    const fns: (() => void)[] = [];

    // body와 documentElement를 함께 관찰해 크기 변화를 놓치지 않는다
    const ro = new ResizeObserver(syncHeight);
    ro.observe(doc.body);
    ro.observe(doc.documentElement);
    fns.push(() => ro.disconnect());

    // 폰트 로드 전에는 텍스트 높이가 작게 잡혀 로드 후 다시 잰다
    if ('fonts' in doc) {
      let cancelled = false;
      doc.fonts.ready.then(() => {
        if (!cancelled) syncHeight();
      });
      fns.push(() => { cancelled = true; });
    }

    // 이미지 로드·디코드가 끝나면 높이를 다시 잰다
    const imgs = Array.from(doc.querySelectorAll<HTMLImageElement>('img'));
    imgs.forEach((img) => {
      img.addEventListener('load', syncHeight);
      img.decode?.().then(syncHeight).catch(() => {/* 디코드 실패는 무시 */});
    });
    fns.push(() => {
      imgs.forEach((img) => img.removeEventListener('load', syncHeight));
    });

    // 지연 렌더 등 동적으로 들어오는 DOM 변화도 관찰한다
    const mo = new MutationObserver(syncHeight);
    mo.observe(doc.body, { subtree: true, childList: true, attributes: false, characterData: false });
    fns.push(() => mo.disconnect());

    cleanupRef.current = fns;
  }, [syncHeight, teardown]);

  /** 슬라이드가 바뀌면 이전 Observer를 바로 해제한다 */
  useEffect(() => {
    return teardown;
  }, [resolvedHtml, teardown]);

  /** 창 리사이즈 시 높이 재계산 */
  useEffect(() => {
    window.addEventListener('resize', syncHeight);
    return () => window.removeEventListener('resize', syncHeight);
  }, [syncHeight]);

  return (
    <iframe
      ref={iframeRef}
      title={title}
      // allow-same-origin: 부모가 contentDocument를 읽어 scrollHeight를 잰다
      // allow-scripts는 일부러 뺐다. 둘을 함께 주면 sandbox가 풀려 XSS가 가능해진다
      sandbox="allow-same-origin"
      srcDoc={resolvedHtml}
      onLoad={handleLoad}
      style={{
        width: '100%',
        height,
        border: '1px solid var(--line)',
        borderRadius: 14,
        background: 'var(--paper)',
        display: 'block',
        // iframe 외곽은 overflow:hidden이고 내부는 height를 늘려 전체를 보여 준다
        overflow: 'hidden',
      }}
    />
  );
}
