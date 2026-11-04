// AI 튜터 타입 (TutorCustomize의 DEFAULT_TUTORS / PERSONA_BASES 구조)

/** 기본 제공 페르소나 종류 */
export type TutorAvatarKind = 'owl' | 'cat' | 'fox' | 'bear' | 'cloud' | 'spark';

/** 튜터 기반 캐릭터 (선택 가능한 프리셋) */
export interface PersonaBase {
  id: string;
  avatar: TutorAvatarKind;
  /** 아바타 배경색 (hex) */
  bg: string;
  /** 아바타 전경색 (hex) */
  fg: string;
  name: string;
  desc: string;
  tags: string[];
}

/** 사용자가 커스터마이즈한 AI 튜터 */
export interface Tutor {
  id: string;
  /** @deprecated 아바타를 없애서 쓰지 않는다 */
  base?: string;
  name: string;
  /** @deprecated photo를 쓴다 */
  avatar?: TutorAvatarKind;
  /** @deprecated photo를 쓴다 */
  avatarBg?: string;
  /** @deprecated photo를 쓴다 */
  avatarFg?: string;
  /** 프로필 사진 URL (API) 또는 Data URL (로컬 미리보기) */
  photo?: string | null;
  desc: string;
  /** @deprecated API에 없는 필드다 */
  tags?: string[];
  /** 말투 친근도 (0=엄격~100=친근) */
  tone: number;
  /** 설명 속도 (0=천천히~100=빠르게) */
  pace: number;
  /** 설명 깊이 (0=개념만~100=깊이 파기) */
  depth: number;
  /** 질문 스타일 (0=답 먼저~100=질문 먼저) */
  socratic: number;
  useEmoji: boolean;
  formal: boolean;
  /** 기본 제공 튜터 여부 */
  builtIn: boolean;
  /** 보이스 클론 URL (API) 또는 Data URL (로컬) */
  voiceSample?: string | null;
  /** 보이스 클론 저장 완료 여부 */
  voiceCloneSaved?: boolean;
}
