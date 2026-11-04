// 범용 훅
import { useState, useEffect } from 'react';

// 타이핑 효과 훅
// deps를 JSON.stringify로 문자열 키로 바꿔서, 매 렌더 새 배열을 넘겨도 내용이 같으면 리셋하지 않는다
export function useTypewriter(fullText: string, speed = 22, deps: React.DependencyList = []) {
  const [out, setOut] = useState("");
  const depsKey = JSON.stringify(deps);
  useEffect(() => {
    let i = 0;
    let t: ReturnType<typeof setTimeout>;
    setOut("");
    const step = () => {
      i++;
      setOut(fullText.slice(0, i));
      if (i < fullText.length) { t = setTimeout(step, speed + Math.random() * speed * .6); }
    };
    t = setTimeout(step, 180);
    return () => clearTimeout(t);
  // depsKey는 원시 문자열이라 React 비교와 exhaustive-deps 규칙을 그대로 통과한다
  }, [fullText, speed, depsKey]);
  return out;
}

// 클립보드 복사와 flash 상태 훅
export function useCopy(): [boolean, (text: string) => void] {
  const [flash, setFlash] = useState(false);
  const copy = (text: string) => {
    try { navigator.clipboard.writeText(text); } catch (_e) { /* 복사 실패 시 무시 */ }
    setFlash(true);
    setTimeout(() => setFlash(false), 1400);
  };
  return [flash, copy];
}
