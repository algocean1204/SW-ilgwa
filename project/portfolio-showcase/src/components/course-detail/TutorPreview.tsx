// 튜터 미리보기 카드.
// 프로필, API 말풍선 미리보기, 설정 페이지 이동 버튼을 둔다.
import { useEffect, useState } from 'react';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import { previewTutor } from '@/apis';

const DEFAULT_TUTOR_IMAGE = import.meta.env.BASE_URL + 'default-tutor.svg';
const SAMPLE_QUESTION = '데이터베이스는 왜 정규화를 할까?';

interface PreviewState {
  tutorId: string;
  text: string;
  error: string | null;
}

interface TutorPreviewProps {
  palette: Palette;
  setRoute: RouteSetter;
  tutorId?: string;
  tutorName?: string;
  tutorAvatarUrl?: string;
}

export function TutorPreview({ palette, setRoute, tutorId, tutorName, tutorAvatarUrl }: TutorPreviewProps) {
  const name = tutorName || '올빼미 교수';
  const avatar = tutorAvatarUrl || DEFAULT_TUTOR_IMAGE;
  const [previewState, setPreviewState] = useState<PreviewState | null>(null);

  useEffect(() => {
    if (!tutorId) return;

    let mounted = true;
    void previewTutor(tutorId, { sampleQuestion: SAMPLE_QUESTION })
      .then((res) => {
        if (!mounted) return;
        const text = res.data?.previewText?.trim();
        setPreviewState({
          tutorId,
          text: text ?? '',
          error: text ? null : '미리보기 문장을 아직 만들지 못했습니다.',
        });
      })
      .catch(() => {
        if (mounted) {
          setPreviewState({ tutorId, text: '', error: '튜터 미리보기를 불러오지 못했습니다.' });
        }
      });

    return () => {
      mounted = false;
    };
  }, [tutorId]);

  const loading = !!tutorId && previewState?.tutorId !== tutorId;
  const bubbleText = !tutorId
    ? '튜터가 연결되면 말투 미리보기를 표시합니다.'
    : loading ? '말투 미리보기를 불러오는 중...' : previewState?.error ?? previewState?.text ?? '';

  return (
    <div style={{
      /* 히어로보다 덜 눈에 띄게 --surface-subtle 배경을 쓴다 */
      padding: "22px 24px", borderRadius: 16, background: "var(--surface-subtle)",
      border: "1px solid var(--border-soft)", display: "flex", flexDirection: "column", gap: 14,
    }}>
      <div style={{
        fontSize: 12, fontWeight: 800, color: "var(--muted)",
        letterSpacing: ".1em", textTransform: "uppercase",
      }}>
        내 튜터
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <img
          src={avatar}
          alt={name}
          style={{
            width: 56, height: 56, borderRadius: "50%", objectFit: 'cover',
            border: `2px solid ${palette.soft}`, background: palette.wash,
          }}
        />
        <div>
          <div style={{ fontSize: 14, fontWeight: 800, color: "var(--ink)" }}>{name}</div>
        </div>
      </div>

      {/* 말풍선 샘플. 위쪽 꼬리 삼각형은 absolute로 그린다 */}
      <div style={{
        padding: "14px 16px", borderRadius: 12, background: palette.wash,
        border: `1px solid ${palette.soft}`, position: "relative",
      }}>
        <div style={{
          position: "absolute", top: -6, left: 22, width: 12, height: 12,
          background: palette.wash,
          borderLeft: `1px solid ${palette.soft}`, borderTop: `1px solid ${palette.soft}`,
          transform: "rotate(45deg)",
        }} />
        <div style={{ fontSize: 12, color: "var(--ink-2)", lineHeight: 1.6, fontStyle: "italic" }}>
          {bubbleText}
        </div>
      </div>

      <button onClick={() => setRoute("tutor")} style={{
        fontSize: 12, fontWeight: 700, color: palette.primary, textAlign: "left",
      }}>
        목소리·말투·호칭 바꾸기
      </button>
    </div>
  );
}
