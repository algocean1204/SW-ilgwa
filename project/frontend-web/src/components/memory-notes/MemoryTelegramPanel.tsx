// 텔레그램 자동전송 설정 패널. Switch·SettingRow는 TelegramControls.tsx, 대기 목록은 TelegramQueueList.tsx에 있다.
// 상태 변경은 모두 콜백으로 부모(MemoryNotes)에 넘긴다.
// 보조 패널이라 노트 그리드보다 눈에 덜 띄게 옅은 스타일을 쓴다.
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import type { MemoNote } from '@/shared/types';
import { Switch, SettingRow } from './TelegramControls';
import { TelegramQueueList } from './TelegramQueueList';

interface MemoryTelegramPanelProps {
  palette: Palette;
  telegramOn: boolean;
  setTelegramOn: (v: boolean) => void;
  autoSend: boolean;
  setAutoSend: (v: boolean) => void;
  sendTime: string;
  setSendTime: (v: string) => void;
  queued: MemoNote[];
  onClose: () => void;
}

export function MemoryTelegramPanel({
  palette, telegramOn, setTelegramOn,
  autoSend, setAutoSend, sendTime, setSendTime,
  queued, onClose,
}: MemoryTelegramPanelProps) {
  return (
    <div className="scroll" style={{ flex: 1, overflowY: 'auto', padding: '14px 16px 16px' }}>
      {/* 패널 헤더. 여백을 줄여 보조 패널처럼 보이게 한다 */}
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12 }}>
        <div style={{
          fontSize: 10, fontWeight: 700, color: 'var(--text-muted)',
          letterSpacing: '.14em', textTransform: 'uppercase', flex: 1,
        }}>
          TELEGRAM BOT
        </div>
        <button
          onClick={onClose}
          style={{ width: 24, height: 24, borderRadius: 5, color: 'var(--text-tertiary)', display: 'grid', placeItems: 'center' }}
          title="닫기"
        >
          <Icon name="x" size={12} />
        </button>
      </div>

      {/* 봇 연결 배너. 시선이 노트 그리드에 가도록 파란 배경 대신 연한 설정 배경 토큰을 쓴다 */}
      <div style={{
        background: 'var(--telegram-settings-bg)',
        border: '1px solid var(--telegram-settings-border)',
        borderRadius: 10,
        padding: '12px 14px',
        position: 'relative', overflow: 'hidden',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 9, position: 'relative' }}>
          <div style={{
            width: 30, height: 30, borderRadius: 8,
            background: 'var(--telegram-ui-border)', display: 'grid', placeItems: 'center',
          }}>
            <Icon name="send" size={14} color="var(--telegram-blue)" />
          </div>
          <div style={{ flex: 1 }}>
            {/* 배너 제목. 텔레그램 블루 글자, 작은 크기 */}
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--telegram-blue)', letterSpacing: '-.01em' }}>자동 전송</div>
            <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 1 }}>매일 정해진 시간에 암기노트를 보내요</div>
          </div>
          <Switch on={telegramOn} onChange={setTelegramOn} />
        </div>
        {telegramOn && (
          <div style={{
            marginTop: 10, padding: '6px 10px', borderRadius: 7,
            background: 'var(--telegram-ui-border)', fontSize: 11,
            display: 'flex', alignItems: 'center', gap: 6,
            color: 'var(--text-secondary)',
          }}>
            {/* 연결 상태 도트 */}
            <span style={{ width: 5, height: 5, borderRadius: 3, background: 'var(--grass-2)' }} className="pulse" />
            <span>@ilgwa_bot · 연결됨</span>
            <span style={{ marginLeft: 'auto', color: 'var(--text-tertiary)' }}>김지윤</span>
          </div>
        )}
      </div>

      {/* 자동 전송 설정 섹션 레이블 */}
      <div style={{
        fontSize: 10, fontWeight: 600, color: 'var(--text-muted)',
        letterSpacing: '.10em', textTransform: 'uppercase',
        margin: '16px 0 6px', padding: '0 2px',
      }}>
        자동 전송 설정
      </div>
      <div style={{ background: 'var(--surface-subtle)', borderRadius: 10, border: '1px solid var(--border-soft)', padding: '2px 0' }}>
        <SettingRow label="매일 자동 전송" right={<Switch on={autoSend} onChange={setAutoSend} />} />
        <SettingRow
          label="전송 시각"
          right={
            <input
              value={sendTime}
              onChange={e => setSendTime(e.target.value)}
              style={{
                width: 60, padding: '3px 7px', borderRadius: 5,
                border: '1px solid var(--border-default)', fontSize: 11, fontWeight: 700,
                color: 'var(--text-primary)', fontFamily: 'inherit',
                textAlign: 'center', background: 'var(--surface-card)',
              }}
            />
          }
        />
        <SettingRow
          label="하루 노트 수"
          right={<span style={{ fontSize: 11, fontWeight: 700, color: palette.primary }}>5개</span>}
        />
        <SettingRow
          label="주말 전송"
          right={<span style={{ fontSize: 11, color: 'var(--text-tertiary)', fontWeight: 600 }}>건너뜀</span>}
          border={false}
        />
      </div>

      {/* 다음 전송 예고. 옅은 accent wash 배경으로 섹션을 나눈다 */}
      <div style={{
        marginTop: 12, padding: '10px 12px', borderRadius: 9,
        background: palette.wash, border: `1px solid ${palette.soft}`,
        display: 'flex', alignItems: 'center', gap: 8,
      }}>
        <Icon name="clock" size={14} color={palette.primary} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 10, fontWeight: 600, color: palette.primary }}>다음 전송까지</div>
          <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-primary)', marginTop: 1 }}>
            오늘 {sendTime} · 3시간 24분
          </div>
        </div>
      </div>

      {/* 전송 대기 목록 */}
      <TelegramQueueList palette={palette} queued={queued} />
    </div>
  );
}
