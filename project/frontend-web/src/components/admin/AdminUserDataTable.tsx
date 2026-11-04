// 사용자 목록 테이블과 정지 확인 모달.
// tableLayout: fixed로 열 폭을 고정해 가로 스크롤이 생기지 않게 한다.
import { useState } from 'react';
import type { AdminUserListResponse } from '@/apis/admin';
import ConfirmDialog from '@/components/ui/ConfirmDialog';

interface Props {
  users: AdminUserListResponse[];
  totalElements: number;
  page: number;
  totalPages: number;
  onPageChange: (p: number) => void;
  // reason은 API에 함께 전달할 정지 사유
  onSuspended: (userId: string, reason?: string) => void;
  primary: string;
}

const SUB_LABEL: Record<string, string> = { FREE: 'Free', PRO: 'Pro', PREMIUM: 'Premium', EXPIRED: '만료' };
// YYYY-MM-DD로 자른다
const shortDate = (s: string) => s.slice(0, 10);
const statusStyle = (s: string) =>
  s === 'ACTIVE' ? { bg: 'var(--grass-wash)', ink: 'var(--grass-ink)' } : { bg: 'var(--coral-wash)', ink: 'var(--coral-ink)' };

const COLS = ['닉네임', '이메일', '구독', '상태', '텔레그램', '가입일', '액션'];
const WIDTHS = ['14%', '24%', '10%', '10%', '12%', '16%', '14%'];

// 정지 확인 모달. ConfirmDialog를 쓰고 사유 입력 textarea는 children 슬롯에 넣는다.
function SuspendModal({ user, onClose, onConfirm }: { user: AdminUserListResponse; onClose: () => void; onConfirm: (r: string) => void }) {
  const [reason, setReason] = useState('');
  return (
    <ConfirmDialog
      open={true}
      onClose={onClose}
      title="계정 정지"
      description={<><b>{user.nickname}</b> ({user.email}) 계정을 정지하시겠어요?</>}
      confirmLabel="정지 확인"
      onConfirm={() => onConfirm(reason)}
      cancelLabel="취소"
      confirmTone="danger"
    >
      <textarea
        placeholder="정지 사유 (선택, UI 전용)"
        value={reason}
        onChange={e => setReason(e.target.value)}
        style={{
          width: '100%', minHeight: 72, padding: '10px 12px',
          borderRadius: 8, fontSize: 12,
          border: '1px solid var(--border-default)',
          background: 'var(--surface-card)', color: 'var(--text-primary)',
          resize: 'none', outline: 'none', fontFamily: 'inherit',
          boxSizing: 'border-box',
        }}
      />
    </ConfirmDialog>
  );
}

function AdminUserDataTable({ users, totalElements, page, totalPages, onPageChange, onSuspended, primary }: Props) {
  const [target, setTarget] = useState<AdminUserListResponse | null>(null);

  async function handleConfirm(reason: string) {
    if (!target) return;
    // API 호출은 상위 컴포넌트가 하므로 reason만 넘긴다
    try { onSuspended(target.id, reason || undefined); }
    finally { setTarget(null); }
  }

  return (
    <>
      <div style={{ background: 'var(--paper)', borderRadius: 14, border: '1px solid var(--line)', boxShadow: 'var(--shadow-md)', overflow: 'hidden' }}>
        <div style={{ padding: '14px 20px', borderBottom: '1px solid var(--line-2)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-3)' }}>사용자 목록</div>
          <div style={{ fontSize: 11, color: 'var(--muted)' }}>총 {totalElements}명</div>
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', tableLayout: 'fixed' }}>
            <colgroup>{WIDTHS.map((w, i) => <col key={i} style={{ width: w }} />)}</colgroup>
            <thead>
              <tr style={{ background: 'var(--bg)' }}>
                {COLS.map(h => <th key={h} style={{ padding: '10px 14px', fontSize: 10, fontWeight: 700, textAlign: 'left', color: 'var(--ink-3)', letterSpacing: '.08em', textTransform: 'uppercase', borderBottom: '1px solid var(--line-2)', whiteSpace: 'nowrap' }}>{h}</th>)}
              </tr>
            </thead>
            <tbody>
              {users.map((u, i) => {
                const ss = statusStyle(u.status);
                return (
                  <tr key={u.id} style={{ borderBottom: '1px solid var(--line-2)', background: i % 2 === 0 ? 'var(--paper)' : 'var(--bg)' }}>
                    <td style={{ padding: '10px 14px', fontSize: 13, fontWeight: 800, color: 'var(--ink)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{u.nickname}</td>
                    <td style={{ padding: '10px 14px', fontSize: 12, color: 'var(--ink-2)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{u.email}</td>
                    <td style={{ padding: '10px 14px', fontSize: 11, fontWeight: 700, color: primary }}>{SUB_LABEL[u.subscriptionStatus] ?? u.subscriptionStatus}</td>
                    <td style={{ padding: '10px 14px' }}>
                      <span style={{ fontSize: 10, fontWeight: 700, padding: '3px 7px', borderRadius: 8, background: ss.bg, color: ss.ink }}>{u.status === 'ACTIVE' ? '활성' : '정지'}</span>
                    </td>
                    <td style={{ padding: '10px 14px', fontSize: 12, color: 'var(--ink-3)' }}>{u.telegramLinked ? '연결됨' : '미연결'}</td>
                    <td style={{ padding: '10px 14px', fontSize: 12, color: 'var(--ink-3)' }}>{shortDate(u.createdAt)}</td>
                    <td style={{ padding: '10px 14px' }}>
                      {u.status === 'ACTIVE' && <button onClick={() => setTarget(u)} style={{ fontSize: 11, fontWeight: 700, color: 'var(--coral-ink)', background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}>정지</button>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {totalPages > 1 && (
          <div style={{ padding: '12px 20px', borderTop: '1px solid var(--line-2)', display: 'flex', gap: 6, justifyContent: 'center' }}>
            {Array.from({ length: totalPages }, (_, i) => (
              <button key={i} onClick={() => onPageChange(i)} style={{ width: 28, height: 28, borderRadius: 6, border: '1px solid var(--line)', fontSize: 11, fontWeight: 700, cursor: 'pointer', background: i === page ? primary : 'var(--paper)', color: i === page ? 'var(--paper)' : 'var(--ink-2)' }}>{i + 1}</button>
            ))}
          </div>
        )}
      </div>
      {target && <SuspendModal user={target} onClose={() => setTarget(null)} onConfirm={handleConfirm} />}
    </>
  );
}

export default AdminUserDataTable;
