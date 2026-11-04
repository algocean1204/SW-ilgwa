export interface TutorResponse {
  tutorId: string;
  name: string;
  tagline: string;
  profileImageUrl: string;
  voiceSampleUrl: string;
  isDefault: boolean;
  toneSlider: number;
  speedSlider: number;
  depthSlider: number;
  questionStyleSlider: number;
  useEmoji: boolean;
  useFormalSpeech: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TutorListResponse {
  tutors: TutorResponse[];
}

export interface CreateTutorResponse {
  tutorId: string;
}

export interface PreviewTutorResponse {
  previewText: string;
  settingTags: string[];
  // 미리보기 TTS 음성 URL. 합성에 실패하면 null이고 텍스트 미리보기만 보인다.
  audioUrl?: string | null;
}

export interface VoiceSampleResponse {
  tutorId: string;
  voiceSampleUrl: string;
  uploadedAt: string;
  status: string;
}

export interface ProfileImageResponse {
  tutorId: string;
  profileImageUrl: string;
  uploadedAt: string;
}

export interface CreateTutorRequest {
  name: string;
  tagline?: string;
  profileImageUrl?: string;
  toneSlider?: number;
  speedSlider?: number;
  depthSlider?: number;
  questionStyleSlider?: number;
  useEmoji?: boolean;
  useFormalSpeech?: boolean;
}

export interface UpdateTutorRequest {
  name?: string;
  tagline?: string;
  profileImageUrl?: string;
  toneSlider?: number;
  speedSlider?: number;
  depthSlider?: number;
  questionStyleSlider?: number;
  useEmoji?: boolean;
  useFormalSpeech?: boolean;
}

export interface PreviewTutorRequest {
  toneSlider?: number;
  speedSlider?: number;
  depthSlider?: number;
  questionStyleSlider?: number;
  useEmoji?: boolean;
  useFormalSpeech?: boolean;
  sampleQuestion: string;
}
