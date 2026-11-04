// 튜터 미리보기. 한 토픽의 학습 세션 장면으로 구성해
// 이 튜터와 공부하면 어떤 모습인지 보여 준다.
import type { Palette } from '@/lib';
import type { Tutor } from '@/shared/types';
import { TutorAvatar } from './TutorAvatar';
import Icon from '@/Icon';

interface LivePreviewProps {
  palette: Palette;
  tutor: Tutor;
}

// 튜터 설정(말투·소크라테스·깊이)을 반영한 예시 응답을 만든다
function buildReply({ tone, depth, socratic, useEmoji, formal }: Tutor): string {
  const en = formal ? '요' : '해';
  const polite = formal ? '해요' : '해';
  const pre = socratic > 60
    ? `한 가지만 먼저 물어볼게${en}.\n→ "지금 만드는 모듈이 '하나의 일'만 하고 있나${en}?"\n이 질문이 응집도의 핵심이${en}.`
    : `둘은 방향이 정반대${polite}.\n· 응집도 = 모듈 "안"이 얼마나 하나로 묶여 있는가\n· 결합도 = 모듈 "사이"가 얼마나 얽혀 있는가`;
  const mid = depth > 60
    ? `\n\n더 깊이 보면 — 기능적 응집이 가장 높고, 우연적 응집이 가장 낮${polite}.`
    : '';
  const end = tone > 70
    ? `\n\n${useEmoji ? '💪 ' : ''}같이 해봐${polite}!`
    : tone < 30 ? `\n\n이 부분은 반드시 외워두${en}.`
    : `\n\n준비되면 다음으로 넘어갈게${en}.`;
  return pre + mid + end;
}

// 튜터 슬라이더 값을 스타일 태그로 바꾼다
function summarizeTags({ tone, pace, depth, socratic }: Tutor): string[] {
  return [
    tone < 30 ? '엄격' : tone > 70 ? '친근' : '균형',
    pace < 30 ? '천천히' : pace > 70 ? '빠르게' : '표준',
    depth < 30 ? '핵심만' : depth > 70 ? '심화' : '예시포함',
    socratic > 60 ? '소크라테스식' : '직접 설명',
  ];
}

// 학습 세션 헤더. 과목·토픽·진행률로 어떤 상황의 미리보기인지 알려 준다.
function SessionHeader({ palette }: { palette: Palette }) {
  return (
    <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border-soft)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginBottom: 8 }}>
        <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>소프트웨어공학</span>
        <Icon name="arrowRight" size={8} color="var(--text-muted)" />
        <span style={{ fontSize: 10, fontWeight: 700, color: 'var(--text-secondary)' }}>응집도 vs 결합도</span>
      </div>
      {/* 62% 진행으로 대화 중간 장면임을 보여 준다 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{ flex: 1, height: 4, borderRadius: 99, background: 'var(--surface-subtle)', overflow: 'hidden' }}>
          <div style={{ width: '62%', height: '100%', borderRadius: 99, background: palette.primary, opacity: .75 }} />
        </div>
        <span style={{ fontSize: 10, color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>62% · 5/8 개념</span>
      </div>
    </div>
  );
}

// 브라우저 크롬 바: 신호등 3개와 URL 바
function ChromeBar() {
  const dot = (bg: string) => <div style={{ width: 9, height: 9, borderRadius: '50%', background: bg }} />;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px', borderBottom: '1px solid var(--border-soft)', background: 'var(--surface-subtle)' }}>
      <div style={{ display: 'flex', gap: 5, flexShrink: 0 }}>
        {dot('var(--chrome-dot-close)')} {dot('var(--chrome-dot-minimise)')} {dot('var(--chrome-dot-expand)')}
      </div>
      <div style={{ flex: 1, padding: '3px 10px', borderRadius: 5, background: 'var(--surface-card)', border: '1px solid var(--border-soft)', fontSize: 10, color: 'var(--text-muted)' }}>
        univt.app / 학습 / 소프트웨어공학
      </div>
    </div>
  );
}

// 미니 입력창. 실제 채팅 UI처럼 하단에 둔다.
function InputBar({ palette }: { palette: Palette }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 8,
      padding: '8px 12px',
      borderTop: '1px solid var(--border-soft)',
      background: 'var(--surface-subtle)',
    }}>
      <div style={{
        flex: 1, padding: '6px 10px', borderRadius: 8,
        background: 'var(--surface-card)', border: '1px solid var(--border-default)',
        fontSize: 11, color: 'var(--text-muted)',
      }}>
        다음 질문을 입력하세요…
      </div>
      <div style={{
        width: 28, height: 28, borderRadius: 8, flexShrink: 0,
        background: palette.primary, display: 'grid', placeItems: 'center',
      }}>
        <Icon name="arrowRight" size={12} color="var(--paper)" />
      </div>
    </div>
  );
}

export function LivePreview({ palette, tutor }: LivePreviewProps) {
  const msg = buildReply(tutor);
  const tags = summarizeTags(tutor);
  return (
    // 앱 창처럼 보이게 border와 shadow로 경계를 준다
    <div style={{
      borderRadius: 14,
      background: 'var(--surface-page)',
      border: '1px solid var(--border-default)',
      boxShadow: 'var(--shadow-md)',
      overflow: 'hidden',
      display: 'flex', flexDirection: 'column',
    }}>
      <ChromeBar />
      <SessionHeader palette={palette} />

      <div style={{ padding: '12px 14px', flex: 1 }}>
        <div style={{
          fontSize: 10, fontWeight: 700, color: palette.primary,
          letterSpacing: '.1em', textTransform: 'uppercase',
          marginBottom: 10, display: 'flex', alignItems: 'center', gap: 4,
        }}>
          <Icon name="sparkle" size={9} color={palette.primary} /> 이 튜터와의 학습 장면
        </div>

        {/* 학생 질문 (우측 정렬) */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 10 }}>
          <div style={{
            maxWidth: '82%', padding: '8px 12px',
            borderRadius: '10px 10px 3px 10px',
            background: palette.wash, border: `1px solid ${palette.soft}`,
          }}>
            <div style={{ fontSize: 11, color: 'var(--text-secondary)', lineHeight: 1.55 }}>
              응집도와 결합도가 헷갈려요. 어떻게 구분하나요?
            </div>
          </div>
        </div>

        {/* 튜터 응답: 좌측 정렬, 아바타, 이름, 말풍선 */}
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', marginBottom: 10 }}>
          <TutorAvatar photo={tutor.photo} size={30} />
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 5, marginBottom: 3 }}>
              <span style={{ fontSize: 11, fontWeight: 800, color: 'var(--text-primary)' }}>{tutor.name}</span>
              <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>방금 전</span>
            </div>
            <div style={{
              padding: '9px 12px', borderRadius: '3px 10px 10px 10px',
              background: 'var(--surface-card)', border: '1px solid var(--border-default)',
            }}>
              <div style={{ fontSize: 11, color: 'var(--text-secondary)', lineHeight: 1.7, whiteSpace: 'pre-wrap' }}>
                {msg}
              </div>
            </div>
          </div>
        </div>

        {/* 현재 설정을 요약한 스타일 태그 */}
        <div style={{
          display: 'flex', gap: 4, flexWrap: 'wrap',
          paddingTop: 8, borderTop: '1px solid var(--border-soft)',
        }}>
          {tags.map(tag => (
            <span key={tag} style={{
              fontSize: 10, fontWeight: 600, padding: '2px 7px',
              borderRadius: 999,
              background: 'var(--surface-subtle)', border: '1px solid var(--border-soft)',
              color: 'var(--text-tertiary)',
            }}>#{tag}</span>
          ))}
        </div>
      </div>

      <InputBar palette={palette} />
    </div>
  );
}
