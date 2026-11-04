-- V6: 기본 튜터 3종 구성을 맞추기 위해 포근한 존댓말 프리셋을 추가한다.
INSERT INTO tutor (
  tutor_id,
  user_id,
  name,
  tagline,
  profile_image_url,
  voice_sample_url,
  tone_slider,
  speed_slider,
  depth_slider,
  question_style_slider,
  use_emoji,
  use_formal_speech,
  is_default
)
VALUES (
  'tut_0000000000000PRESET_BEAR01',
  NULL,
  '곰 선생님',
  '포근하고 다정한 설명 · 천천히 함께',
  NULL,
  NULL,
  65,
  40,
  55,
  40,
  true,
  true,
  true
)
ON CONFLICT (tutor_id) DO NOTHING;
