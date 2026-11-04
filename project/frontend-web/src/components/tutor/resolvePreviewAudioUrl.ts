// 서버가 준 미리보기 오디오 URL을 브라우저가 재생할 수 있는 절대 URL로 바꾼다.
// 규칙은 AudioPlayerBar의 resolveAudioUrl과 같고, env를 인자로 받아 단위 테스트할 수 있다.
// VoiceCloneEditor가 env.apiFastapi를 넘겨 호출한다.

/**
 * @param value    서버 응답의 오디오 URL (절대/blob/data/상대 /media 경로 등)
 * @param fastapiBase FastAPI 베이스 URL (env.apiFastapi). 빈 문자열이면 same-origin 폴백을 의미한다.
 * @returns 재생 가능한 URL, 재생 불가(file:// 또는 빈 값)면 null
 */
export function resolvePreviewAudioUrl(
  value: string | null | undefined,
  fastapiBase: string,
): string | null {
  if (!value || value.startsWith('file://')) return null;
  // 이미 절대/blob/data URL이면 그대로 사용
  if (/^(https?:|blob:|data:)/.test(value)) return value;
  // /media/ 상대경로는 FastAPI 베이스가 있을 때만 절대경로로 바꾼다
  if (value.startsWith('/media/') && fastapiBase) return `${fastapiBase}${value}`;
  // 그 외(빈 베이스의 /media 등)는 same-origin 상대경로로 두고 dev 프록시가 처리한다
  return value;
}
