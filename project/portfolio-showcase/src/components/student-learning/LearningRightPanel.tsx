// 우측 패널: 탭 스위처와 탭별 내용.
// 음성 모드 props는 LearningChatTab으로 넘긴다.
import { type RefObject } from 'react';
import type { Palette } from '@/lib';
import type { AppState } from '@/shared/types';
import type { VADState } from '@/shared/audio';
import Icon from '@/Icon';
import { LearningChatTab } from './LearningChatTab';
import { LearningCurriculumTab } from './LearningCurriculumTab';
import { LearningNoteTab } from './LearningNoteTab';
import type { ChatMessage } from './useLearningChat';
import type { Course } from '@/shared/types';
import type { ChapterSummaryResponse } from '@/apis';

interface TabDef {
  key: string;
  label: string;
  icon: string;
}

const TABS: TabDef[] = [
  { key: 'chat', label: '튜터', icon: 'message' },
  { key: 'note', label: 'AI노트', icon: 'note' },
  { key: 'curriculum', label: '커리큘럼', icon: 'map' },
];

// 하단 fixed AudioPlayerBar와 우측 패널 입력창이 겹치지 않도록 예약하는 영역
const AUDIO_BAR_HEIGHT = 48;
const AUDIO_BAR_GAP = 12;
const RIGHT_PANEL_AUDIO_SAFE_AREA = AUDIO_BAR_HEIGHT + AUDIO_BAR_GAP;
const RIGHT_PANEL_BASE_OFFSET = 100;

interface LearningRightPanelProps {
  rightTab: string;
  setRightTab: (tab: string) => void;
  chat: ChatMessage[];
  typing: boolean;
  input: string;
  setInput: (v: string) => void;
  send: () => void;
  /** 전송 실패 메시지 재시도 핸들러. 실패 메시지 인덱스를 넘긴다 */
  retry: (failedIdx: number) => void;
  scrollRef: RefObject<HTMLDivElement>;
  palette: Palette;
  appState: AppState;
  course: Course;
  courseId: string;
  chapterId: string;
  chapters: ChapterSummaryResponse[];
  activeChapterId: string;
  /** 현재 음성 모드 활성 여부 */
  voiceMode?: boolean;
  /** 음성 모드 토글 핸들러 */
  onToggleVoice?: () => void;
  /** VAD 상태 */
  vadState?: VADState;
  /** 실시간 에너지 레벨 (0~1) */
  vadEnergy?: number;
  /** 부분 인식 텍스트 */
  partialTranscript?: string;
  /** 커리큘럼 탭에서 챕터 선택 시 호출 */
  onChapterSelect?: (chapterId: string) => void;
}

export function LearningRightPanel({
  rightTab, setRightTab,
  chat, typing, input, setInput, send, retry, scrollRef,
  palette, appState, course, courseId, chapterId, chapters, activeChapterId,
  voiceMode, onToggleVoice, vadState, vadEnergy, partialTranscript,
  onChapterSelect,
}: LearningRightPanelProps) {
  return (
    /* 보조 패널이라 배경·테두리·그림자를 메인보다 약하게 둔다 */
    <aside className="learning-right-panel" style={{
      position: 'sticky',
      top: 72,
      /* 메인 --paper보다 한 단계 옅은 --bg */
      background: 'var(--bg)',
      borderRadius: 12,
      /* 메인은 --line, 패널은 더 약한 --border-soft */
      border: '1px solid var(--border-soft)',
      /* 메인과 구분되게 그림자를 두지 않는다 */
      boxShadow: 'none',
      display: 'flex',
      flexDirection: 'column',
      height: `calc(100vh - ${RIGHT_PANEL_BASE_OFFSET + RIGHT_PANEL_AUDIO_SAFE_AREA}px)`,
      overflow: 'hidden',
      /* 패널 전체 opacity를 낮춰 메인보다 덜 눈에 띄게 한다 */
      opacity: 0.95,
    }}>
      {/* 탭 스위처. 강조하지 않는다 */}
      <div className="learning-panel-tabs" role="tablist" aria-label="수업 보조 도구" style={{
        display: 'flex',
        /* 탭 구분선도 --border-soft */
        borderBottom: '1px solid var(--border-soft)',
        background: 'var(--bg)',
      }}>
        {TABS.map(({ key, label, icon }) => (
          <button
            key={key}
            id={`learning-panel-tab-${key}`}
            className="learning-panel-tab"
            data-active={rightTab === key}
            role="tab"
            aria-selected={rightTab === key}
            aria-controls={`learning-panel-${key}`}
            onClick={() => setRightTab(key)}
            style={{
              flex: 1,
              padding: '10px 6px',
              fontSize: 12,
              /* 탭 헤더가 튀지 않게 활성 탭도 600 */
              fontWeight: rightTab === key ? 600 : 400,
              /* 비활성 탭은 더 옅은 --ink-3 */
              color: rightTab === key ? palette.primary : 'var(--ink-3)',
              /* 활성 탭 하단 선은 1.5px */
              borderBottom: rightTab === key ? `1.5px solid ${palette.primary}` : '1.5px solid transparent',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 5,
              transition: 'all .15s',
            }}
          >
            <Icon name={icon} size={12} color={rightTab === key ? palette.primary : 'var(--ink-3)'} />
            {label}
          </button>
        ))}
      </div>

      {rightTab === 'chat' && (
        <div id="learning-panel-chat" className="learning-panel-body" role="tabpanel" aria-labelledby="learning-panel-tab-chat">
          <LearningChatTab
            chat={chat}
            typing={typing}
            input={input}
            setInput={setInput}
            send={send}
            retry={retry}
            scrollRef={scrollRef}
            palette={palette}
            appState={appState}
            voiceMode={voiceMode}
            onToggleVoice={onToggleVoice}
            vadState={vadState}
            vadEnergy={vadEnergy}
            partialTranscript={partialTranscript}
          />
        </div>
      )}
      {rightTab === 'curriculum' && (
        <div id="learning-panel-curriculum" className="learning-panel-body" role="tabpanel" aria-labelledby="learning-panel-tab-curriculum">
          <LearningCurriculumTab
            course={course}
            courseId={courseId}
            chapters={chapters}
            activeChapterId={activeChapterId}
            palette={palette}
            onChapterSelect={onChapterSelect}
          />
        </div>
      )}
      {rightTab === 'note' && (
        <div id="learning-panel-note" className="learning-panel-body" role="tabpanel" aria-labelledby="learning-panel-tab-note">
          <LearningNoteTab courseId={courseId} chapterId={chapterId} palette={palette} />
        </div>
      )}
    </aside>
  );
}
