import { useState } from 'react';
import Icon from '@/Icon';
import type { RouteSetter } from '@/shared/types';

interface LandingLaunchPanelProps {
  setRoute: RouteSetter;
}

const EXAMPLES = [
  '중1 정수와 유리수 2주',
  '중3 이차함수 기초',
  '고1 일차방정식 복습',
];

function LandingLaunchPanel({ setRoute }: LandingLaunchPanelProps) {
  const [mode, setMode] = useState<'pdf' | 'topic'>('pdf');
  const [topic, setTopic] = useState('');

  return (
    <section className="landing-launch-panel" id="start">
      <div className="landing-section-copy">
        <span>START</span>
        <h2>자료를 넣거나, 주제를 적고 바로 수업 흐름을 만듭니다.</h2>
      </div>

      <div className="landing-launch-board">
        <div className="landing-mode-stack" role="tablist" aria-label="시작 방식">
          <button role="tab" data-active={mode === 'pdf'} aria-selected={mode === 'pdf'} onClick={() => setMode('pdf')}>
            <Icon name="pdf" size={18} />
            <span>PDF로 시작</span>
          </button>
          <button role="tab" data-active={mode === 'topic'} aria-selected={mode === 'topic'} onClick={() => setMode('topic')}>
            <Icon name="edit" size={18} />
            <span>주제로 시작</span>
          </button>
        </div>

        <div className="landing-input-stage">
          {mode === 'pdf' ? (
            <button className="landing-upload-zone" onClick={() => setRoute('auth')} title="로그인 후 PDF 업로드">
              <Icon name="upload" size={24} />
              <strong>교재, 강의자료, 참고 PDF</strong>
              <span>목차를 읽고 수업 순서와 복습 단위를 정리합니다.</span>
            </button>
          ) : (
            <div className="landing-topic-zone">
              <textarea
                value={topic}
                onChange={(event) => setTopic(event.target.value)}
                placeholder="예) 중1 정수와 유리수를 2주 안에 복습하기"
              />
              <div className="landing-example-row">
                {EXAMPLES.map((example) => (
                  <button key={example} onClick={() => setTopic(example)} title={`${example} 입력`}>
                    {example}
                  </button>
                ))}
              </div>
              <button className="landing-create-action" onClick={() => setRoute('auth')} title="로그인 후 커리큘럼 만들기">
                커리큘럼 만들기
              </button>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

export { LandingLaunchPanel };
