// 클라이언트 VAD(Voice Activity Detection) 훅.
// 마이크 → AudioContext → AnalyserNode → rAF 루프로 RMS 에너지를 재고 상태 머신을 돌린다.
// endpoint 확정은 백엔드가 하고, 이 훅은 "endpoint-candidate" 이벤트만 낸다.
import { useState, useRef, useCallback, useEffect, useMemo } from 'react';
import type { VADState, VADConfig, VADEvent } from './vad-types';

/** useVAD 훅의 반환 타입 */
export interface UseVADReturn {
  state: VADState;
  energy: number;
  start: () => Promise<void>;
  stop: () => void;
  onEvent: (handler: (event: VADEvent) => void) => void;
}

const DEFAULT_CONFIG: VADConfig = {
  sampleRate: 16000,
  speechThreshold: 0.015,
  silenceDuration: 1500,
  minSpeechDuration: 300,
};

/** Float32Array 타임도메인 버퍼의 RMS 에너지 */
function computeRms(buffer: Float32Array): number {
  let sum = 0;
  for (let i = 0; i < buffer.length; i++) sum += (buffer[i] ?? 0) ** 2;
  return Math.sqrt(sum / buffer.length);
}

export function useVAD(config?: Partial<VADConfig>): UseVADReturn {
  // config가 매 렌더 새 객체가 되면 useEffect가 다시 돌아서 useMemo로 고정한다
  const cfg = useMemo(() => ({ ...DEFAULT_CONFIG, ...config }), [config]);
  const [state, setState] = useState<VADState>('idle');
  const [energy, setEnergy] = useState(0);

  const eventHandlerRef = useRef<((e: VADEvent) => void) | null>(null);
  const audioCtxRef     = useRef<AudioContext | null>(null);
  const analyserRef     = useRef<AnalyserNode | null>(null);
  const streamRef       = useRef<MediaStream | null>(null);
  const rafIdRef        = useRef<number | null>(null);

  // 상태 머신 변수. rAF 콜백이 최신값을 읽도록 ref로 둔다.
  const vadStateRef             = useRef<VADState>('idle');
  const speechStartRef          = useRef<number | null>(null);
  const silenceStartRef         = useRef<number | null>(null);
  const endpointFiredRef        = useRef(false);
  const belowFramesRef          = useRef(0);

  const emit = useCallback((e: VADEvent) => eventHandlerRef.current?.(e), []);

  // 루프 함수는 ref에 둔다. useCallback 안에서 analysisLoop를 직접 참조하면
  // react-hooks/immutability 규칙에 걸린다.
  const analysisLoopRef = useRef<() => void>(() => {});

  /** rAF 분석 루프. cfg·emit가 바뀔 때마다 ref를 최신 클로저로 바꾼다. */
  useEffect(() => {
    analysisLoopRef.current = () => {
      const analyser = analyserRef.current;
      if (!analyser) return;
      const buf = new Float32Array(analyser.fftSize);
      analyser.getFloatTimeDomainData(buf);
      const rms = computeRms(buf);
      const norm = Math.min(1, rms / 0.3);
      setEnergy(norm);
      emit({ type: 'energy-update', timestamp: Date.now(), energy: norm });

      const s = vadStateRef.current;
      const now = Date.now();
      const above = rms > cfg.speechThreshold;

      if (s === 'listening') {
        if (above) {
          vadStateRef.current = 'speech'; setState('speech');
          speechStartRef.current = now; silenceStartRef.current = null;
          belowFramesRef.current = 0; endpointFiredRef.current = false;
          emit({ type: 'speech-start', timestamp: now });
        }
      } else if (s === 'speech') {
        if (!above) {
          belowFramesRef.current += 1;
          // 3프레임 연속 임계 미달이면 묵음으로 본다 (짧은 노이즈 스파이크는 무시)
          if (belowFramesRef.current >= 3) {
            vadStateRef.current = 'silence'; setState('silence');
            silenceStartRef.current = now;
            const dur = speechStartRef.current ? now - speechStartRef.current : 0;
            if (dur >= cfg.minSpeechDuration) emit({ type: 'speech-end', timestamp: now, duration: dur });
          }
        } else {
          belowFramesRef.current = 0;
        }
      } else if (s === 'silence') {
        if (above) {
          // 묵음 중 음성이 다시 잡히면 speech로 돌아간다
          vadStateRef.current = 'speech'; setState('speech');
          if (!speechStartRef.current) speechStartRef.current = now;
          silenceStartRef.current = null; belowFramesRef.current = 0;
          endpointFiredRef.current = false;
          emit({ type: 'speech-start', timestamp: now });
        } else if (!endpointFiredRef.current && silenceStartRef.current) {
          // 묵음이 길어지면 endpoint-candidate를 낸다 (최종 판단은 백엔드)
          if (now - silenceStartRef.current >= cfg.silenceDuration) {
            endpointFiredRef.current = true;
            emit({ type: 'endpoint-candidate', timestamp: now });
          }
        }
      }
      // 린트 self-reference 규칙 때문에 ref로 다음 프레임을 예약한다
      rafIdRef.current = requestAnimationFrame(analysisLoopRef.current);
    };
  }, [cfg, emit]);

  /** 마이크 권한을 요청하고 VAD 감지를 시작한다 */
  const start = useCallback(async () => {
    if (vadStateRef.current !== 'idle') return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { sampleRate: cfg.sampleRate, channelCount: 1, echoCancellation: true, noiseSuppression: true },
      });
      streamRef.current = stream;
      const ctx = new AudioContext({ sampleRate: cfg.sampleRate });
      audioCtxRef.current = ctx;
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 2048;
      analyserRef.current = analyser;
      ctx.createMediaStreamSource(stream).connect(analyser);
      vadStateRef.current = 'listening'; setState('listening');
      // ref로 최신 클로저를 호출한다
      rafIdRef.current = requestAnimationFrame(analysisLoopRef.current);
    } catch {
      // 마이크 권한 거부 등은 idle로 둔다
      vadStateRef.current = 'idle'; setState('idle');
    }
  }, [cfg.sampleRate]);

  /** VAD를 멈추고 오디오 자원을 해제한다 */
  const stop = useCallback(() => {
    if (rafIdRef.current !== null) { cancelAnimationFrame(rafIdRef.current); rafIdRef.current = null; }
    streamRef.current?.getTracks().forEach(t => t.stop());
    streamRef.current = null;
    audioCtxRef.current?.close();
    audioCtxRef.current = null; analyserRef.current = null;
    vadStateRef.current = 'idle'; setState('idle'); setEnergy(0);
    speechStartRef.current = null; silenceStartRef.current = null;
    belowFramesRef.current = 0; endpointFiredRef.current = false;
  }, []);

  const onEvent = useCallback((handler: (e: VADEvent) => void) => {
    eventHandlerRef.current = handler;
  }, []);

  return { state, energy, start, stop, onEvent };
}
