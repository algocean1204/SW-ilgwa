// API TutorResponse ↔ 프론트엔드 Tutor 필드 매핑 어댑터
import type { Tutor } from '@/shared/types';
import type { TutorResponse, CreateTutorRequest, UpdateTutorRequest } from '@/apis';

export function toTutor(res: TutorResponse): Tutor {
  return {
    id: res.tutorId,
    name: res.name,
    desc: res.tagline,
    photo: res.profileImageUrl || undefined,
    voiceSample: res.voiceSampleUrl || undefined,
    builtIn: res.isDefault,
    tone: res.toneSlider,
    pace: res.speedSlider,
    depth: res.depthSlider,
    socratic: res.questionStyleSlider,
    useEmoji: res.useEmoji,
    formal: res.useFormalSpeech,
    // avatar 관련 필드는 API에 없으므로 기본값 부여
    base: 'owl',
    avatar: 'owl',
    avatarBg: 'var(--honey-pale)',
    avatarFg: 'var(--honey-ink3)',
    tags: [],
  };
}

export function toCreateRequest(tutor: Tutor): CreateTutorRequest {
  return {
    name: tutor.name,
    tagline: tutor.desc,
    toneSlider: tutor.tone,
    speedSlider: tutor.pace,
    depthSlider: tutor.depth,
    questionStyleSlider: tutor.socratic,
    useEmoji: tutor.useEmoji,
    useFormalSpeech: tutor.formal,
  };
}

export function toUpdateRequest(patch: Partial<Tutor>): UpdateTutorRequest {
  const req: UpdateTutorRequest = {};
  if (patch.name !== undefined) req.name = patch.name;
  if (patch.desc !== undefined) req.tagline = patch.desc;
  if (patch.tone !== undefined) req.toneSlider = patch.tone;
  if (patch.pace !== undefined) req.speedSlider = patch.pace;
  if (patch.depth !== undefined) req.depthSlider = patch.depth;
  if (patch.socratic !== undefined) req.questionStyleSlider = patch.socratic;
  if (patch.useEmoji !== undefined) req.useEmoji = patch.useEmoji;
  if (patch.formal !== undefined) req.useFormalSpeech = patch.formal;
  return req;
}
