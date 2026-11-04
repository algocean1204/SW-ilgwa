// 튜터 프로필과 스타일 편집 패널.
// 튜터 화면의 좌측·중앙 영역에 필요한 입력만 그린다.
import { useCallback } from 'react';
import type { ReactNode } from 'react';
import type { Palette } from '@/lib';
import type { Tutor } from '@/shared/types';
import { PhotoDropzone } from './PhotoDropzone';
import { SliderRow } from './SliderRow';
import { ToggleRow } from './ToggleRow';

interface PersonaEditorProps {
  tutor: Tutor;
  palette: Palette;
  onUpdate: (patch: Partial<Tutor>) => void;
  /** Pro 구독이 필요한 잠금 상태. true면 편집을 막는다 */
  locked?: boolean;
  /** 프로필(사진+이름+소개) 부분만 렌더 */
  profileOnly?: boolean;
  /** 스타일 설정(말투·질문·깊이·이모지·높임말) 부분만 렌더 */
  styleOnly?: boolean;
  /** 저장된 튜터의 프로필 이미지를 고르면 호출한다. 업로드는 부모가 한다 */
  onPhotoFile?: (file: File) => void;
  /** 프로필 이미지 삭제 시 호출한다. 있으면 기본 onUpdate({ photo: null }) 대신 쓴다 */
  onPhotoRemove?: () => void;
}

// 단계 레이블이 붙은 섹션 헤더
function SectionLabel({ step, title, subtitle }: { step: string; title: string; subtitle?: string }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span style={{ fontSize: 10, fontWeight: 700, color: 'var(--text-tertiary)', letterSpacing: '.08em' }}>
          {step}
        </span>
        <span style={{ fontSize: 13, fontWeight: 800, color: 'var(--text-primary)' }}>{title}</span>
      </div>
      {subtitle && (
        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>{subtitle}</div>
      )}
    </div>
  );
}

// 편집 패널 카드
function Panel({ children }: { children: ReactNode }) {
  return (
    <div>
      {children}
    </div>
  );
}

// 슬라이더 값 범위(저/중/고)별 한국어 힌트
const hint3 = (v: number, lo: string, mid: string, hi: string) => v < 30 ? lo : v < 70 ? mid : hi;
const getToneHint = (v: number) => hint3(v, '깐깐하게 지적합니다', '균형잡힌 톤', '따뜻하고 다정하게');
const getDepthHint = (v: number) => hint3(v, '꼭 필요한 것만', '예시 1~2개', '심화 · 원리까지');
const getSocraticHint = (v: number) => hint3(v, '바로 정답', '힌트·답', '스스로 찾게');

export function PersonaEditor({ tutor, palette, onUpdate, locked, profileOnly, styleOnly, onPhotoFile, onPhotoRemove }: PersonaEditorProps) {
  // 자식 리렌더를 줄이려고 핸들러를 useCallback으로 고정한다
  const handleUpload = useCallback((dataUrl: string) => onUpdate({ photo: dataUrl }), [onUpdate]);
  // onPhotoRemove가 있으면 그 핸들러를 쓴다. 부모가 서버 삭제까지 처리한다.
  const handleRemove = useCallback(() => {
    if (onPhotoRemove) {
      onPhotoRemove();
    } else {
      onUpdate({ photo: null });
    }
  }, [onUpdate, onPhotoRemove]);
  const setTone = useCallback((v: number) => onUpdate({ tone: v }), [onUpdate]);
  const setDepth = useCallback((v: number) => onUpdate({ depth: v }), [onUpdate]);
  const setSocratic = useCallback((v: number) => onUpdate({ socratic: v }), [onUpdate]);
  const setEmoji = useCallback((v: boolean) => onUpdate({ useEmoji: v }), [onUpdate]);
  const setFormal = useCallback((v: boolean) => onUpdate({ formal: v }), [onUpdate]);

  return (
    // locked면 CSS 포인터 차단에 더해 aria-disabled를 줘
    // 키보드·스크린리더도 비활성으로 인식하게 한다
    <div
      aria-disabled={locked}
      style={{
        display: 'flex', flexDirection: 'column', gap: 12, position: 'relative',
        opacity: locked ? 0.5 : 1,
        pointerEvents: locked ? 'none' : undefined,
      }}
    >
      {/* Pro 구독 안내 배너. locked이고 단독 렌더가 아닐 때만 보인다 */}
      {locked && !profileOnly && !styleOnly && (
        <div style={{ fontSize: 12, fontWeight: 600, color: palette.primary, marginBottom: 2 }}>
          이 튜터를 편집하려면 Pro 구독이 필요해요
        </div>
      )}
      {/* 프로필 패널 (styleOnly면 숨김) */}
      {!styleOnly && <Panel>
        <SectionLabel step="② 프로필" title="이름 · 사진" subtitle="좋아하는 사람 사진을 넣어 나만의 튜터를 만드세요" />
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
          <PhotoDropzone
            photo={tutor.photo}
            palette={palette}
            onUpload={handleUpload}
            onRemove={handleRemove}
            onFileSelect={onPhotoFile}
          />
          <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div>
              <label htmlFor="tutor-name" style={{ fontSize: 11, color: 'var(--text-tertiary)', fontWeight: 600, marginBottom: 3, display: 'block' }}>이름</label>
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                {/* locked면 disabled로 포커스·키보드·폼 제출을 막는다 */}
                <input id="tutor-name" value={tutor.name} onChange={e => onUpdate({ name: e.target.value })} disabled={locked} readOnly={locked} style={{ flex: 1, padding: '8px 10px', borderRadius: 8, border: '1px solid var(--border-default)', fontSize: 13, color: 'var(--text-primary)', background: 'var(--surface-card)', fontFamily: 'inherit', fontWeight: 600 }} />
                <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{tutor.name.length}/20</div>
              </div>
            </div>
            <div>
              <label htmlFor="tutor-desc" style={{ fontSize: 11, color: 'var(--text-tertiary)', fontWeight: 600, marginBottom: 3, display: 'block' }}>한줄 소개</label>
              {/* locked면 disabled + readOnly로 포커스와 폼 제출을 막는다 */}
              <input id="tutor-desc" value={tutor.desc} onChange={e => onUpdate({ desc: e.target.value })} disabled={locked} readOnly={locked} style={{ width: '100%', padding: '8px 10px', borderRadius: 8, border: '1px solid var(--border-default)', fontSize: 12, color: 'var(--text-secondary)', background: 'var(--surface-card)', fontFamily: 'inherit' }} placeholder="어떤 튜터인지 한 문장으로" />
            </div>
          </div>
        </div>
      </Panel>}

      {/* 스타일 설정 패널 (profileOnly면 숨김) */}
      {!profileOnly && <Panel>
        <SectionLabel
          step="③ 스타일 설정"
          title="핵심 3가지"
          subtitle="말투, 질문 방식, 설명 깊이만 조정합니다"
        />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* 1순위: 말투. 첫인상을 정한다 */}
          {/* locked면 disabled를 넘겨 키보드 포커스를 막는다 */}
          <SliderRow label="말투" left="엄격" right="친근" val={tutor.tone} set={setTone} palette={palette} hint={getToneHint(tutor.tone)} disabled={locked} />
          {/* 2순위: 소크라테스 방식. 학습 효과에 가장 크게 영향을 준다 */}
          <SliderRow label="질문 스타일" left="답 먼저" right="질문 먼저" val={tutor.socratic} set={setSocratic} palette={palette} hint={getSocraticHint(tutor.socratic)} disabled={locked} />
          {/* 3순위: 설명 깊이. 과목·난이도별로 자주 바꾼다 */}
          <SliderRow label="설명 깊이" left="개념만" right="깊이 파기" val={tutor.depth} set={setDepth} palette={palette} hint={getDepthHint(tutor.depth)} disabled={locked} />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            <ToggleRow label="이모지 사용" desc="필요할 때만 가볍게" val={tutor.useEmoji} set={setEmoji} palette={palette} disabled={locked} />
            <ToggleRow label="높임말" desc='"~예요" · "~입니다"' val={tutor.formal} set={setFormal} palette={palette} disabled={locked} />
          </div>
        </div>
      </Panel>}
    </div>
  );
}
