// 미리보기 오디오 URL 정규화 함수 단위 테스트.
// AudioPlayerBar와 같은 규칙(절대/blob/data 통과, /media 절대화, file:// 차단)을 확인한다.
import { describe, it, expect } from 'vitest';
import { resolvePreviewAudioUrl } from '../resolvePreviewAudioUrl';

const FASTAPI = 'http://localhost:8000';

describe('resolvePreviewAudioUrl', () => {
  it('빈 값/undefined/null이면 null을 반환한다', () => {
    expect(resolvePreviewAudioUrl(null, FASTAPI)).toBeNull();
    expect(resolvePreviewAudioUrl(undefined, FASTAPI)).toBeNull();
    expect(resolvePreviewAudioUrl('', FASTAPI)).toBeNull();
  });

  it('file:// 로컬 경로는 재생 불가로 보고 null을 반환한다', () => {
    expect(resolvePreviewAudioUrl('file:///tmp/out.wav', FASTAPI)).toBeNull();
  });

  it('http/https 절대 URL은 그대로 통과시킨다', () => {
    expect(resolvePreviewAudioUrl('https://cdn.example.com/a.wav', FASTAPI))
      .toBe('https://cdn.example.com/a.wav');
    expect(resolvePreviewAudioUrl('http://example.com/b.mp3', ''))
      .toBe('http://example.com/b.mp3');
  });

  it('blob:/data: URL은 그대로 통과시킨다', () => {
    expect(resolvePreviewAudioUrl('blob:http://x/123', FASTAPI)).toBe('blob:http://x/123');
    expect(resolvePreviewAudioUrl('data:audio/wav;base64,AAAA', FASTAPI))
      .toBe('data:audio/wav;base64,AAAA');
  });

  it('/media 상대경로는 FastAPI 베이스가 있으면 절대경로로 변환한다', () => {
    expect(resolvePreviewAudioUrl('/media/tts/preview.wav', FASTAPI))
      .toBe('http://localhost:8000/media/tts/preview.wav');
  });

  it('/media 상대경로라도 FastAPI 베이스가 비어 있으면 상대경로 그대로 둔다 (same-origin 프록시 폴백)', () => {
    expect(resolvePreviewAudioUrl('/media/tts/preview.wav', ''))
      .toBe('/media/tts/preview.wav');
  });

  it('/media 가 아닌 기타 상대경로는 변환하지 않고 그대로 반환한다', () => {
    expect(resolvePreviewAudioUrl('/static/x.wav', FASTAPI)).toBe('/static/x.wav');
  });
});
