// 마이크 입력의 음성/묵음 구간을 판별하는 VAD 상태 머신 타입

/** VAD 상태 */
export type VADState = 'idle' | 'listening' | 'speech' | 'silence';

/** VAD 설정. 일부만 덮어쓸 수 있다. */
export interface VADConfig {
  /** Web Audio API 샘플링 레이트 (16kHz 권장) */
  sampleRate: number;
  /** 이 RMS 에너지보다 크면 음성으로 본다 (기본 0.015) */
  speechThreshold: number;
  /** 묵음이 이 시간(ms) 이어지면 endpoint-candidate를 낸다 (기본 1500ms) */
  silenceDuration: number;
  /** 이 시간(ms)보다 짧은 발화는 노이즈로 보고 무시한다 (기본 300ms) */
  minSpeechDuration: number;
}

/** VAD 이벤트. 상태 전환과 에너지 갱신을 알린다. */
export interface VADEvent {
  type: 'speech-start' | 'speech-end' | 'endpoint-candidate' | 'energy-update';
  /** 이벤트 발생 시각 (Date.now() 기준 ms) */
  timestamp: number;
  /** 현재 RMS 에너지, 0~1 정규화 (energy-update 이벤트) */
  energy?: number;
  /** 발화 지속 시간 ms (speech-end 이벤트) */
  duration?: number;
}
