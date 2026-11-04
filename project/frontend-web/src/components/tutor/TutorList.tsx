// 튜터 목록 선택 패널. 활성 카드만 강조한다.
import { useState } from 'react';
import type { Palette } from '@/lib';
import type { Tutor } from '@/shared/types';
import { TutorAvatar } from './TutorAvatar';
import Icon from '@/Icon';

interface TutorListProps {
  tutors: Tutor[];
  activeId: string;
  palette: Palette;
  onSelect: (id: string) => void;
  onAdd: () => void;
  onDelete: (id: string) => void;
  onDuplicate: (id: string) => void;
  /** 튜터 최대 5명에 도달했는지. true면 추가 버튼을 끈다 */
  maxReached?: boolean;
  /** 무료 플랜인지. true면 커스텀 튜터에 잠금을 표시한다 */
  isFree?: boolean;
}

interface TutorCardProps {
  tutor: Tutor;
  active: boolean;
  palette: Palette;
  canDelete: boolean;
  canDuplicate: boolean;
  onClick: () => void;
  onDelete: () => void;
  onDuplicate: () => void;
  /** 무료 플랜이면 커스텀 튜터에 잠금 오버레이와 PRO 배지를 보여 준다 */
  isFree?: boolean;
}

// 개별 튜터 카드. 비활성 카드는 흐리게, 활성 카드만 강조한다.
function TutorCard({ tutor, active, palette, canDelete, canDuplicate, onClick, onDelete, onDuplicate, isFree }: TutorCardProps) {
  const [hover, setHover] = useState(false);
  const isLocked = isFree && !tutor.builtIn; // 무료 플랜 커스텀 튜터는 잠금
  return (
    <div onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)} style={{ position: 'relative' }}>
      <button
        onClick={isLocked ? undefined : onClick}
        className="lift"
        style={{
          width: 116,
          padding: active ? '12px 8px 10px' : '10px 8px 8px',
          borderRadius: 12,
          // 활성: palette wash + 강한 테두리, 비활성: 거의 보이지 않는 배경
          background: active ? palette.wash : 'var(--surface-page)',
          border: active
            ? `2px solid ${palette.primary}`
            : hover
              ? '1px solid var(--border-default)'
              : '1px solid var(--border-soft)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 5,
          textAlign: 'center',
          boxShadow: active ? `0 4px 16px -6px ${palette.primary}40` : 'none', // 활성만 그림자
          transition: 'all .2s ease',
        }}
      >
        <TutorAvatar photo={tutor.photo} size={active ? 44 : 36} />
        <div style={{
          fontSize: 11,
          fontWeight: active ? 800 : 600,
          color: active ? 'var(--text-primary)' : 'var(--text-tertiary)', // 활성 짙게 · 비활성 흐리게
          lineHeight: 1.3,
          transition: 'color .2s',
        }}>{tutor.name}</div>
        {/* 설명은 활성 카드에서만 보여 준다 */}
        {active && (
          <div style={{ fontSize: 10, color: palette.primary, lineHeight: 1.3, opacity: .85 }}>
            {tutor.desc}
          </div>
        )}
        {/* builtIn이면 "기본" 배지 */}
        {tutor.builtIn && (
          <span style={{
            fontSize: 9, fontWeight: 800,
            padding: '2px 7px', borderRadius: 999,
            background: 'var(--honey-pale)', color: 'var(--honey-ink)',
            letterSpacing: '.04em', border: '1px solid var(--honey)',
          }}>기본</span>
        )}
        {/* 기본 튜터가 아니고 편집 중일 때만 보이는 활성 배지 */}
        {active && !isLocked && !tutor.builtIn && (
          <span style={{
            fontSize: 9,
            fontWeight: 700,
            padding: '2px 7px',
            borderRadius: 999,
            background: palette.primary,
            color: 'var(--paper)',
            letterSpacing: '.04em',
          }}>편집 중</span>
        )}
        {/* 무료 플랜의 커스텀 튜터에만 보이는 PRO 배지 */}
        {isLocked && (
          <span style={{
            fontSize: 9, fontWeight: 800,
            padding: '2px 7px', borderRadius: 999,
            background: 'var(--honey-ink2)', color: 'var(--paper)',
            letterSpacing: '.06em',
          }}>PRO</span>
        )}
      </button>
      {/* 무료 플랜 커스텀 튜터 위의 반투명 잠금 표시 */}
      {isLocked && (
        <div style={{
          position: 'absolute', inset: 0, borderRadius: 12,
          background: 'rgba(0,0,0,0.18)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          pointerEvents: 'none',
        }}>
          <Icon name="lock" size={18} color="var(--paper)" />
        </div>
      )}
      {/* 복제·삭제 버튼. 호버·활성일 때만, 잠금이면 숨긴다 */}
      {!isLocked && hover && (canDuplicate || (canDelete && !tutor.builtIn)) && (
        <div style={{ position: 'absolute', top: 5, right: 5, display: 'flex', gap: 2 }}>
          {canDuplicate && (
            <button onClick={onDuplicate} title="복제" style={{
              width: 20, height: 20, borderRadius: 5,
              background: 'var(--surface-card)', border: '1px solid var(--border-default)',
              display: 'grid', placeItems: 'center', color: 'var(--text-secondary)',
            }}>
              <Icon name="plus" size={9} />
            </button>
          )}
          {canDelete && !tutor.builtIn && (
            <button onClick={onDelete} title="삭제" style={{
              width: 20, height: 20, borderRadius: 5,
              background: 'var(--surface-card)', border: '1px solid var(--border-default)',
              display: 'grid', placeItems: 'center',
            }}>
              <Icon name="trash" size={9} color="var(--danger)" />
            </button>
          )}
        </div>
      )}
    </div>
  );
}

export function TutorList({ tutors, activeId, palette, onSelect, onAdd, onDelete, onDuplicate, maxReached, isFree }: TutorListProps) {
  const canDuplicate = !isFree && !maxReached;

  return (
    <div style={{ marginBottom: 'var(--section-gap)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
        <span style={{
          fontSize: 10, fontWeight: 700, letterSpacing: '.1em',
          color: palette.primary, textTransform: 'uppercase',
        }}>① 튜터 선택</span>
        <div style={{ flex: 1, height: 1, background: 'var(--border-soft)' }} />
        <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
          {tutors.length}/5명
        </span>
      </div>

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        {tutors.map(tr => (
          <TutorCard
            key={tr.id}
            tutor={tr}
            active={tr.id === activeId}
            palette={palette}
            canDelete={tutors.length > 1}
            canDuplicate={canDuplicate}
            onClick={() => onSelect(tr.id)}
            onDelete={() => onDelete(tr.id)}
            onDuplicate={() => onDuplicate(tr.id)}
            isFree={isFree}
          />
        ))}
        {/* 새 튜터 추가 버튼. maxReached·isFree면 disabled로 키보드까지 막는다 */}
        <button
          disabled={maxReached || isFree}
          onClick={maxReached || isFree ? undefined : onAdd}
          className={maxReached || isFree ? undefined : 'lift'}
          aria-label={maxReached ? '튜터 최대 5명 도달' : isFree ? 'PRO 전용 기능' : '새 튜터 추가'}
          style={{
            width: 116, borderRadius: 12, padding: '10px 8px 8px',
            background: 'var(--surface-page)',
            border: maxReached || isFree // 비활성: 실선·흐림 / 활성: 점선
              ? '1px solid var(--text-muted)'
              : '1px dashed var(--border-soft)',
            display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center',
            gap: 5, color: 'var(--text-muted)',
            cursor: maxReached || isFree ? 'not-allowed' : 'pointer',
            opacity: maxReached || isFree ? 0.5 : 1,
          }}
        >
          <div style={{
            width: 36, height: 36, borderRadius: '50%',
            background: 'var(--surface-subtle)', display: 'grid', placeItems: 'center',
          }}>
            <Icon name="plus" size={14} color="var(--text-muted)" />
          </div>
          <span style={{ fontSize: 10, fontWeight: 600 }}>
            {maxReached ? '최대 5명' : isFree ? 'PRO 전용' : '새 튜터'}
          </span>
        </button>
      </div>
    </div>
  );
}
