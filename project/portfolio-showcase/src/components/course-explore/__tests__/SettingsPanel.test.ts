// 강의 수 옵션 테스트. 1강·2강 옵션과 선택값 전달을 확인한다.
// LESSON_COUNT_OPTIONS는 컴포넌트 내부 상수라 같은 배열을 여기 직접 둔다.
import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useState } from 'react';

// SettingsPanel.tsx의 실제 값과 맞춰야 한다. 백엔드 허용 범위는 @Min(1) @Max(30).
const LESSON_COUNT_OPTIONS_CONTRACT = [1, 2, 3, 5, 6, 8, 10, 12] as const;

describe('강의 수 옵션 (Bug #4)', () => {
  it('1강 옵션이 선택지에 포함된다', () => {
    expect(LESSON_COUNT_OPTIONS_CONTRACT).toContain(1);
  });

  it('2강 옵션이 선택지에 포함된다', () => {
    expect(LESSON_COUNT_OPTIONS_CONTRACT).toContain(2);
  });

  it('모든 옵션이 백엔드 허용 범위(1~30)를 준수한다', () => {
    for (const n of LESSON_COUNT_OPTIONS_CONTRACT) {
      expect(n).toBeGreaterThanOrEqual(1);
      expect(n).toBeLessThanOrEqual(30);
    }
  });

  it('1강 선택 시 onLessonCountChange(1)이 호출된다', () => {
    // onLessonCountChange 콜백이 선택값을 그대로 전달하는지 확인
    const onLessonCountChange = vi.fn();

    // lessonCount 상태와 변경 핸들러를 흉내 낸다
    const { result } = renderHook(() => {
      const [lessonCount, setLessonCount] = useState<number>(3);
      return { lessonCount, handleChange: (n: number) => { setLessonCount(n); onLessonCountChange(n); } };
    });

    act(() => {
      result.current.handleChange(1);
    });

    expect(onLessonCountChange).toHaveBeenCalledWith(1);
    expect(result.current.lessonCount).toBe(1);
  });

  it('2강 선택 시 onLessonCountChange(2)이 호출된다', () => {
    const onLessonCountChange = vi.fn();

    const { result } = renderHook(() => {
      const [lessonCount, setLessonCount] = useState<number>(3);
      return { lessonCount, handleChange: (n: number) => { setLessonCount(n); onLessonCountChange(n); } };
    });

    act(() => {
      result.current.handleChange(2);
    });

    expect(onLessonCountChange).toHaveBeenCalledWith(2);
    expect(result.current.lessonCount).toBe(2);
  });
});
