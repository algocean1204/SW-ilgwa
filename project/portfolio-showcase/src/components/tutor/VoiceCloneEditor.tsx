// 보이스 클론 패널: 업로드 → 테스트 재생 → 저장
import { useState, useRef, useCallback, useEffect } from 'react';
import type { ChangeEvent } from 'react';
import type { Palette } from '@/lib';
import type { Tutor } from '@/shared/types';
import { previewTutor } from '@/apis';
import type { PreviewTutorResponse } from '@/apis';
import { env } from '@/core/env';
import { resolvePreviewAudioUrl } from './resolvePreviewAudioUrl';
import Icon from '@/Icon';

interface VoiceCloneEditorProps {
  tutor: Tutor; palette: Palette;
  onUpdate: (patch: Partial<Tutor>) => void;
  // 파일을 고르면 원본 File을 부모로 넘긴다. 업로드는 저장 버튼에서 한다.
  onFileSelect?: (file: File) => void;
  locked?: boolean;
}

const ACCEPT = '.wav,.mp3,.m4a'; // 허용 오디오 포맷
const MAX_BYTES = 10 * 1024 * 1024; // 최대 10MB
const DEFAULT_TEXT = '안녕하세요, 저는 여러분의 튜터입니다. 오늘 함께 공부해봐요.';

type PreviewTutorWithAudio = PreviewTutorResponse & {
  voiceSampleUrl?: string;
  voiceUrl?: string;
};

function previewAudioUrl(data: PreviewTutorResponse | null | undefined): string | null {
  if (!data) return null;
  const withAudio = data as PreviewTutorWithAudio;
  const raw = withAudio.audioUrl ?? withAudio.voiceSampleUrl ?? withAudio.voiceUrl ?? null;
  // env.apiFastapi를 넘긴다. URL 정규화 규칙은 resolvePreviewAudioUrl에서 단위 테스트한다
  return resolvePreviewAudioUrl(raw, env.apiFastapi);
}

// PersonaEditor와 같은 패널·레이블 패턴
function Panel({ children }: { children: React.ReactNode }) {
  return <div style={{ padding: '16px 18px', borderRadius: 14, background: 'var(--surface-card)', border: '1px solid var(--border-soft)' }}>{children}</div>;
}
function SectionLabel({ step, title, subtitle }: { step: string; title: string; subtitle?: string }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span style={{ fontSize: 10, fontWeight: 700, color: 'var(--text-tertiary)', letterSpacing: '.08em' }}>{step}</span>
        <span style={{ fontSize: 13, fontWeight: 800, color: 'var(--text-primary)' }}>{title}</span>
      </div>
      {subtitle && <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>{subtitle}</div>}
    </div>
  );
}

export function VoiceCloneEditor({ tutor, palette, onUpdate, onFileSelect, locked }: VoiceCloneEditorProps) {
  const [text, setText] = useState(DEFAULT_TEXT); // 테스트 문장 (최대 100자)
  const [playing, setPlaying] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewText, setPreviewText] = useState('');
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const [audioUnavailable, setAudioUnavailable] = useState(false);
  const [elapsed, setElapsed] = useState(0); // 음성 생성 경과 초 (대기 카피용)
  // 화면에 보이는 <audio controls>. 사용자가 직접 재생·탐색할 수 있다.
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Data URL에 인코딩된 파일명을 꺼낸다
  const fileName = tutor.voiceSample
    ? tutor.voiceSample.substring(tutor.voiceSample.indexOf(';name=') + 6, tutor.voiceSample.indexOf(';base64'))
    : null;

  // 파일을 Data URL로 바꿔 저장(파일명 인코딩 포함)하고 원본 File은 부모로 넘긴다
  const handleFileChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > MAX_BYTES) { alert('10MB 이하의 파일을 선택해주세요.'); return; }
    const reader = new FileReader();
    reader.onload = () => {
      const raw = reader.result as string;
      const withName = raw.replace(';base64,', `;name=${encodeURIComponent(file.name)};base64,`);
      onUpdate({ voiceSample: withName, voiceCloneSaved: false });
      // 실제 업로드는 부모(TutorCustomize) 저장 버튼에서 한다
      onFileSelect?.(file);
    };
    reader.readAsDataURL(file);
    e.target.value = ''; // 같은 파일 재선택 가능하도록 초기화
  }, [onUpdate, onFileSelect]);

  const handleRemove = useCallback(() => onUpdate({ voiceSample: null, voiceCloneSaved: false }), [onUpdate]);

  // 화면에 그린 <audio controls>로 자동재생을 시도한다.
  // 브라우저 정책으로 막혀도 컨트롤바가 보여 사용자가 직접 재생할 수 있다.
  const playAudio = useCallback(async () => {
    const audio = audioRef.current;
    if (!audio) return;
    try {
      audio.currentTime = 0;
      await audio.play();
    } catch {
      // 자동재생 차단(NotAllowedError 등)은 실패로 보지 않는다. 컨트롤바로 재생하면 된다.
      setPlaying(false);
    }
  }, []);

  const handlePreview = useCallback(async () => {
    if (previewLoading || !tutor.id) return;
    const sampleQuestion = text.trim();
    if (!sampleQuestion) {
      setPreviewError('테스트 문장을 입력해 주세요.');
      return;
    }

    // 재생성 시 이전 오디오가 재생 중이면 정지하고 직전 URL을 비운다
    audioRef.current?.pause();
    setAudioUrl(null);
    setPlaying(false);

    setPreviewLoading(true);
    setPreviewError(null);
    setAudioUnavailable(false);
    try {
      const res = await previewTutor(tutor.id, {
        toneSlider: tutor.tone,
        speedSlider: tutor.pace,
        depthSlider: tutor.depth,
        questionStyleSlider: tutor.socratic,
        useEmoji: tutor.useEmoji,
        useFormalSpeech: tutor.formal,
        sampleQuestion,
      });
      const data = res.data;
      if (!data?.previewText) throw new Error('Preview response is empty');
      setPreviewText(data.previewText);
      const nextAudioUrl = previewAudioUrl(data);
      // URL을 상태에 넣으면 <audio> 요소가 렌더되고, 아래 useEffect가 자동재생을 시도한다
      setAudioUrl(nextAudioUrl);
      if (!nextAudioUrl) {
        // 서버가 audioUrl을 못 채웠으면 '준비 중'에 멈추지 않고 실패 안내를 보여 준다
        setAudioUnavailable(true);
        setPreviewError('음성 생성에 실패했어요. 텍스트 미리보기만 표시됩니다.');
      }
    } catch {
      setPlaying(false);
      setPreviewError('미리보기를 생성하지 못했습니다.');
    } finally {
      setPreviewLoading(false);
    }
  }, [previewLoading, tutor, text]);

  // audioUrl이 오면 렌더된 <audio>가 src를 받은 뒤 자동재생을 시도한다.
  // 자동재생이 막혀도 컨트롤바로 직접 재생할 수 있다.
  useEffect(() => {
    if (audioUrl) void playAudio();
  }, [audioUrl, playAudio]);

  // 음성 생성을 기다리는 동안 경과 초를 1초마다 갱신한다
  useEffect(() => {
    if (!previewLoading) {
      setElapsed(0);
      return;
    }
    setElapsed(0);
    const startedAt = Date.now();
    const timer = window.setInterval(() => {
      setElapsed(Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [previewLoading]);

  useEffect(() => {
    setAudioUnavailable(false);
    setAudioUrl(null);
  }, [text, tutor.id, tutor.tone, tutor.pace, tutor.depth, tutor.socratic, tutor.useEmoji, tutor.formal]);

  useEffect(() => {
    return () => {
      audioRef.current?.pause();
    };
  }, []);

  // 다시 녹음: 선택 파일·저장 상태를 비운다. 저장 버튼은 부모에 있다.
  const handleReset = useCallback(() => onUpdate({ voiceSample: null, voiceCloneSaved: false }), [onUpdate]);

  return (
    // locked면 반투명하게 하고 포인터를 막는다
    <div style={{ opacity: locked ? 0.5 : 1, pointerEvents: locked ? 'none' : undefined }}>
      <Panel>
        <SectionLabel
          step="⑤ 목소리"
          title="TTS 보이스 클론"
          subtitle="원하는 목소리 샘플을 넣고 테스트하세요"
        />

        {/* 음성 파일 업로드 영역 */}
        <div style={{
          padding: '12px 14px', borderRadius: 10,
          border: '1px dashed var(--border-default)',
          background: 'var(--surface-subtle)',
          marginBottom: 12,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, color: 'var(--text-muted)', marginBottom: 8 }}>
            <Icon name="mic" size={12} /> WAV · MP3 · M4A · 최대 10MB
          </div>
          {tutor.voiceSample ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 12, color: 'var(--text-secondary)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {fileName ?? '업로드된 파일'}
              </span>
              <button onClick={handleRemove} style={{
                display: 'inline-flex', alignItems: 'center', gap: 3,
                fontSize: 11, color: 'var(--danger)', background: 'none',
                border: '1px solid var(--danger)',
                cursor: 'pointer', padding: '2px 6px', borderRadius: 5,
              }}><Icon name="x" size={10} /> 제거</button>
            </div>
          ) : (
            <label style={{
              display: 'inline-block', padding: '6px 14px', borderRadius: 7,
              background: palette.wash, border: `1px solid ${palette.primary}`,
              fontSize: 12, fontWeight: 700, color: palette.primary,
              cursor: 'pointer',
            }}>
              파일 선택
              <input type="file" accept={ACCEPT} onChange={handleFileChange} style={{ display: 'none' }} />
            </label>
          )}
        </div>

        {/* 테스트 문장 입력 */}
        <div style={{ marginBottom: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
            <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-tertiary)' }}>테스트 문장</span>
            <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{text.length}/100</span>
          </div>
          <textarea
            value={text}
            onChange={e => setText(e.target.value.slice(0, 100))}
            maxLength={100}
            rows={2}
            style={{
              width: '100%', padding: '8px 10px', borderRadius: 8,
              border: '1px solid var(--border-default)', fontSize: 12,
              color: 'var(--text-primary)', background: 'var(--surface-card)',
              fontFamily: 'inherit', resize: 'none', boxSizing: 'border-box',
            }}
          />
        </div>

        {(previewText || previewError) && (
          <div style={{
            marginBottom: 12, padding: '10px 12px', borderRadius: 9,
            background: previewError ? 'var(--coral-wash)' : palette.wash,
            border: `1px solid ${previewError ? 'var(--danger)' : palette.soft}`,
            fontSize: 12, color: previewError ? 'var(--coral-ink-deep)' : 'var(--ink-2)',
            lineHeight: 1.5,
          }}>
            {previewText && <div style={{ color: 'var(--ink-2)' }}>{previewText}</div>}
            {previewError && <div style={{ marginTop: previewText ? 6 : 0, fontWeight: 800 }}>{previewError}</div>}
          </div>
        )}

        {/* 음성 생성 대기 안내와 경과 초. Qwen Modal 폴백 시 30초~1분 걸린다 */}
        {previewLoading && (
          <div style={{
            marginBottom: 12, padding: '10px 12px', borderRadius: 9,
            background: palette.wash, border: `1px solid ${palette.soft}`,
            fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5,
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
            <span style={{ flex: 1 }}>음성을 만드는 중이에요 · 보통 30초~1분 정도 걸려요</span>
            <span style={{ fontWeight: 800, color: palette.primary, fontVariantNumeric: 'tabular-nums' }}>
              {elapsed}초
            </span>
          </div>
        )}

        {/* 음성 미리보기 컨트롤바. audioUrl이 오면 렌더하고 자동재생을 시도한다 */}
        {audioUrl && !audioUnavailable && (
          <div style={{ marginBottom: 12 }}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 6,
              fontSize: 11, fontWeight: 700, color: 'var(--text-tertiary)', marginBottom: 5,
            }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                <Icon name="volume" size={12} /> 미리듣기
              </span>
              {playing && <span style={{ color: palette.primary }}>· 재생 중</span>}
            </div>
            <audio
              ref={audioRef}
              src={audioUrl}
              controls
              onPlay={() => setPlaying(true)}
              onPause={() => setPlaying(false)}
              onEnded={() => setPlaying(false)}
              onError={() => {
                setPlaying(false);
                setPreviewError('음성을 재생하지 못했습니다.');
              }}
              style={{ width: '100%', height: 36 }}
            >
              {/* 오디오 미지원 브라우저 폴백 */}
              브라우저가 오디오 재생을 지원하지 않아요.
            </audio>
          </div>
        )}

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <button onClick={() => void handlePreview()} disabled={previewLoading || !text.trim()} className="lift" style={{
            padding: '8px 16px', borderRadius: 8,
            background: previewLoading ? 'var(--surface-subtle)' : palette.primary,
            color: previewLoading ? palette.primary : 'var(--paper)',
            border: previewLoading ? `1px solid ${palette.primary}` : 'none',
            fontSize: 12, fontWeight: 700, cursor: previewLoading ? 'default' : 'pointer',
            transition: 'all .2s',
          }}>
            {/* 재생은 위 컨트롤바가 하고 버튼은 생성/재생성만 한다 */}
            {previewLoading ? '생성 중...' : audioUrl ? '다시 생성' : '미리보기 생성'}
          </button>

          <button onClick={handleReset} style={{
            padding: '8px 16px', borderRadius: 8,
            background: 'none', border: 'none',
            fontSize: 12, color: 'var(--text-muted)', cursor: 'pointer',
          }}>다시 녹음</button>

          {tutor.voiceCloneSaved && ( // 저장 완료 배지
            <span style={{
              marginLeft: 'auto', fontSize: 11, fontWeight: 700,
              color: 'var(--grass-primary)', display: 'flex', alignItems: 'center', gap: 3,
            }}>
              <Icon name="check" size={12} /> 보이스 저장됨
            </span>
          )}
        </div>

        <div style={{ // 음질 권장 팁
          display: 'flex', alignItems: 'flex-start', gap: 6,
          marginTop: 12, padding: '8px 12px', borderRadius: 8,
          background: 'var(--surface-subtle)',
          fontSize: 11, color: 'var(--text-muted)', lineHeight: 1.5,
        }}>
          <Icon name="warning" size={12} style={{ flexShrink: 0, marginTop: 2 }} />
          깨끗하고 잡음 없는 5~15초 음성을 넣으면 가장 자연스러운 결과를 얻어요
        </div>
      </Panel>
    </div>
  );
}
