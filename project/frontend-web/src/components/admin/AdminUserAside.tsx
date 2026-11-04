// 관리자 사용자 탭 보조 영역. 최근 가입 5명 목록과 텔레그램 브로드캐스트 섹션이 있다.
import { useState, useEffect } from 'react';
import { getAdminUsers, sendBroadcast } from '@/apis/admin';
import type { AdminUserListResponse, BroadcastResponse } from '@/apis/admin';

interface AdminUserAsideProps {
  primary: string;
}

// ISO 날짜를 "방금", "N분 전", "N시간 전", "N일 전" 형식으로 바꾼다
function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return '방금';
  if (mins < 60) return `${mins}분 전`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}시간 전`;
  const days = Math.floor(hrs / 24);
  return `${days}일 전`;
}

function RecentUsersSection({ primary }: { primary: string }) {
  const [users, setUsers] = useState<AdminUserListResponse[]>([]);

  useEffect(() => {
    getAdminUsers({ size: 5 })
      .then(res => {
        if (res.data) setUsers(res.data.content);
      })
      .catch(() => {});
  }, []);

  return (
    <div style={{ background: 'var(--paper)', borderRadius: 14, border: '1px solid var(--line)', padding: '16px 18px', boxShadow: 'var(--shadow-sm)' }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', letterSpacing: '.1em', textTransform: 'uppercase', marginBottom: 12 }}>
        최근 가입
      </div>
      {users.length === 0 ? (
        <div style={{ fontSize: 12, color: 'var(--muted)' }}>불러오는 중...</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {users.map(u => (
            <div key={u.id} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <div style={{ width: 6, height: 6, borderRadius: 3, background: primary, flexShrink: 0 }} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {u.nickname}
                </div>
                <div style={{ fontSize: 10, color: 'var(--muted)', marginTop: 1 }}>
                  {relativeTime(u.createdAt)}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function BroadcastSection({ primary }: { primary: string }) {
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<BroadcastResponse | null>(null);

  async function handleSend() {
    const trimmed = message.trim();
    if (!trimmed || sending) return;
    setSending(true);
    setResult(null);
    try {
      const res = await sendBroadcast({ message: trimmed, targetUserIds: null });
      if (res.data) setResult(res.data);
      setMessage('');
    } catch {
      // 오류는 사용자에게 알리지 않는다
    } finally {
      setSending(false);
    }
  }

  return (
    <div style={{ background: 'var(--paper)', borderRadius: 14, border: '1px solid var(--line)', padding: '16px 18px', boxShadow: 'var(--shadow-sm)' }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', letterSpacing: '.1em', textTransform: 'uppercase', marginBottom: 10 }}>
        전체 발송
      </div>
      <textarea
        placeholder="전체 사용자에게 메시지를 전송해요..."
        value={message}
        onChange={e => setMessage(e.target.value)}
        style={{
          width: '100%', minHeight: 68, padding: '10px 12px',
          borderRadius: 8, fontSize: 12, border: '1px solid var(--line)',
          background: 'var(--bg)', color: 'var(--ink)', resize: 'none',
          outline: 'none', fontFamily: 'inherit', boxSizing: 'border-box',
        }}
      />
      <button
        className="lift"
        onClick={handleSend}
        disabled={sending || !message.trim()}
        style={{
          marginTop: 8, width: '100%', padding: '9px 0', borderRadius: 10, border: 'none',
          background: sending || !message.trim() ? 'var(--muted)' : primary,
          color: 'var(--paper)', fontSize: 12, fontWeight: 700,
          cursor: sending || !message.trim() ? 'not-allowed' : 'pointer',
        }}
      >
        {sending ? '발송 중...' : '텔레그램으로 발송'}
      </button>
      {result && (
        <div style={{ marginTop: 8, fontSize: 11, color: 'var(--ink-2)' }}>
          발송 {result.sentCount}명 완료
          {result.failedCount > 0 && (
            <span style={{ color: 'var(--coral-ink)' }}> / 실패 {result.failedCount}명</span>
          )}
        </div>
      )}
    </div>
  );
}

function AdminUserAside({ primary }: AdminUserAsideProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <RecentUsersSection primary={primary} />
      <BroadcastSection primary={primary} />
    </div>
  );
}

export default AdminUserAside;
