// 튜터 프로필 이미지. photo(URL)가 있으면 쓰고 없으면 기본 이미지를 보여 준다.
const DEFAULT_TUTOR_IMAGE = import.meta.env.BASE_URL + 'default-tutor.svg';

interface TutorAvatarProps {
  size?: number;
  photo?: string | null;
}

export function TutorAvatar({ size = 56, photo }: TutorAvatarProps) {
  return (
    <img
      src={photo ?? DEFAULT_TUTOR_IMAGE}
      alt=""
      style={{
        width: size, height: size, borderRadius: '50%', flexShrink: 0,
        objectFit: 'cover', objectPosition: 'center',
        border: '1px solid var(--line)', background: 'var(--surface-subtle)',
      }}
    />
  );
}
