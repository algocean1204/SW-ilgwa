// 세부 설정 카드. 학습 기간, 난이도, 강의 수, 튜터를 고른다.
import type { ReactNode } from 'react';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import { TutorSection } from './TutorSection';
import type { TeacherItem } from './types';

// 학습 기간 선택지 (일 단위)
const DURATION_OPTIONS = [14, 30, 60, 90];

// 난이도 선택지 [key, label]
const DEPTH_OPTIONS: [string, string][] = [
  ['basic',  '기초 중심'],
  ['normal', '표준'],
  ['deep',   '심화·예외까지'],
];

// 강의(챕터) 수 선택지. 고른 값을 AI에 그대로 넘겨 분량을 맞춘다.
// 백엔드 허용 범위는 @Min(1) @Max(30). 1강·2강은 짧은 집중 학습용이다.
const LESSON_COUNT_OPTIONS = [1, 2, 3, 5, 6, 8, 10, 12] as const;

// 설정 행 레이블과 콘텐츠를 묶는 레이아웃
interface Row2Props {
  label: string;
  children: ReactNode;
}
function Row2({ label, children }: Row2Props) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{
        fontSize: 12, fontWeight: 700, color: 'var(--ink-3)',
        letterSpacing: '.05em', marginBottom: 8, textTransform: 'uppercase',
      }}>
        {label}
      </div>
      {children}
    </div>
  );
}

interface SettingsPanelProps {
  duration: number;
  depth: string;
  teacher: string;
  /** 강의(챕터) 수. AI 커리큘럼 생성 요청에 lessonCount로 넘긴다 */
  lessonCount: number;
  defaultTutors: TeacherItem[];
  customTutors: TeacherItem[];
  palette: Palette;
  setRoute: RouteSetter;
  onDurationChange: (d: number) => void;
  onDepthChange: (d: string) => void;
  onTeacherChange: (id: string) => void;
  onLessonCountChange: (n: number) => void;
  hideCustomTutorLink?: boolean;
}

export function SettingsPanel({
  duration, depth, teacher, lessonCount, defaultTutors, customTutors, palette, setRoute,
  onDurationChange, onDepthChange, onTeacherChange, onLessonCountChange, hideCustomTutorLink,
}: SettingsPanelProps) {
  return (
    /* 표면 배경이라 var(--paper) 토큰을 쓴다 */
    <div style={{
      padding: '22px 24px', borderRadius: 16, background: 'var(--paper)',
      border: '1px solid var(--line)',
    }}>
      <div style={{ fontSize: 12, fontWeight: 800, color: 'var(--ink)', marginBottom: 14 }}>세부 설정</div>

      {/* 학습 기간 선택 */}
      <Row2 label="학습 기간">
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {DURATION_OPTIONS.map(d => (
            <button
              key={d}
              onClick={() => onDurationChange(d)}
              style={{
                padding: '8px 14px', borderRadius: 9, fontSize: 12, fontWeight: 700,
                background: duration === d ? palette.primary : 'var(--bg)',
                color: duration === d ? 'var(--paper)' : 'var(--ink-2)',
                border: duration === d ? 'none' : '1px solid var(--line)',
              }}
            >
              {d}일
            </button>
          ))}
        </div>
      </Row2>

      {/* 난이도·깊이 선택 */}
      <Row2 label="난이도 · 깊이">
        <div style={{ display: 'flex', gap: 6 }}>
          {DEPTH_OPTIONS.map(([k, l]) => (
            <button
              key={k}
              onClick={() => onDepthChange(k)}
              style={{
                padding: '8px 12px', borderRadius: 9, fontSize: 12, fontWeight: 700,
                background: depth === k ? palette.primary : 'var(--bg)',
                color: depth === k ? 'var(--paper)' : 'var(--ink-2)',
                border: depth === k ? 'none' : '1px solid var(--line)',
              }}
            >
              {l}
            </button>
          ))}
        </div>
      </Row2>

      {/* 강의 수 선택 */}
      <Row2 label="강의 수 (챕터)">
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {LESSON_COUNT_OPTIONS.map((n) => (
            <button
              key={n}
              onClick={() => onLessonCountChange(n)}
              style={{
                padding: '8px 14px', borderRadius: 9, fontSize: 12, fontWeight: 700,
                background: lessonCount === n ? palette.primary : 'var(--bg)',
                color: lessonCount === n ? 'var(--paper)' : 'var(--ink-2)',
                border: lessonCount === n ? 'none' : '1px solid var(--line)',
              }}
            >
              {n}강
            </button>
          ))}
        </div>
        <div style={{ marginTop: 5, fontSize: 11, color: 'var(--ink-3)' }}>
          AI가 이 수에 맞춰 챕터를 구성합니다
        </div>
      </Row2>

      {/* 튜터 선택 */}
      <Row2 label="튜터">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <TutorSection
            title="기본 튜터"
            tutors={defaultTutors}
            teacher={teacher}
            palette={palette}
            emptyText="기본 튜터를 불러오는 중입니다."
            onTeacherChange={onTeacherChange}
          />
          {customTutors.length > 0 && (
            <TutorSection
              title="내 튜터"
              tutors={customTutors}
              teacher={teacher}
              palette={palette}
              onTeacherChange={onTeacherChange}
            />
          )}
        </div>
        {!hideCustomTutorLink && (
          <button
            onClick={() => setRoute('tutor')}
            style={{
              marginTop: 8, fontSize: 12, color: palette.primary, fontWeight: 700,
              display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 2, textAlign: 'left',
            }}
          >
            <span>+ 나만의 튜터 만들기</span>
            <span style={{ fontSize: 12, color: 'var(--ink-3)', fontWeight: 500 }}>
              이름, 말투, 목소리까지 직접 정할 수 있어요
            </span>
          </button>
        )}
      </Row2>
    </div>
  );
}
