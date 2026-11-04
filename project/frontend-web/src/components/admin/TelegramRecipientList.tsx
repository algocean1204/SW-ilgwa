// 텔레그램 수신자 목록. 추가·삭제가 성공하면 서버 목록을 다시 불러온다.
import { useState, useEffect } from 'react';
import { getRecipients, addRecipient, deleteRecipient } from '@/apis/admin';
import type { RecipientResponse } from '@/apis/admin';

interface TelegramRecipientListProps {
  primary: string;
}

function TelegramRecipientList({ primary }: TelegramRecipientListProps) {
  const [list, setList] = useState<RecipientResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [addNick, setAddNick] = useState('');
  const [addChatId, setAddChatId] = useState('');
  const [adding, setAdding] = useState(false);

  function fetchList() {
    setLoading(true);
    setError(false);
    getRecipients()
      .then(res => { if (res.data) setList(res.data.recipients); })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }

  useEffect(() => { fetchList(); }, []);

  async function handleAdd() {
    const nick = addNick.trim();
    const cid = parseInt(addChatId.trim(), 10);
    if (!nick || isNaN(cid)) return;
    setAdding(true);
    try {
      await addRecipient({ nickname: nick, chatId: cid });
      setAddNick('');
      setAddChatId('');
      fetchList();
    } catch {
      // 실패는 화면에 알리지 않는다
    } finally {
      setAdding(false);
    }
  }

  async function handleRemove(id: string) {
    try {
      await deleteRecipient(id);
      fetchList();
    } catch {
      // 실패는 화면에 알리지 않는다
    }
  }

  const inputStyle = {
    padding: '8px 10px', borderRadius: 7, border: '1px solid var(--line)',
    fontSize: 12, background: 'var(--bg)', color: 'var(--ink)',
    outline: 'none', fontFamily: 'inherit', width: '100%',
    boxSizing: 'border-box' as const,
  };

  return (
    <div style={{
      background: 'var(--paper)', borderRadius: 14,
      border: '1px solid var(--line)', padding: '20px 22px',
      boxShadow: 'var(--shadow-sm)',
    }}>
      <div style={{
        fontSize: 11, fontWeight: 700, color: primary,
        letterSpacing: '.1em', textTransform: 'uppercase', marginBottom: 16,
      }}>
        수신자 목록
      </div>

      {loading && (
        <div style={{ fontSize: 12, color: 'var(--muted)', padding: '8px 0' }}>불러오는 중...</div>
      )}
      {error && !loading && (
        <div style={{ fontSize: 12, color: 'var(--coral-ink)', padding: '8px 0' }}>
          목록을 불러오지 못했어요.
        </div>
      )}

      {!loading && !error && list.map(r => (
        <div key={r.id} style={{
          display: 'flex', alignItems: 'center', gap: 8,
          padding: '10px 12px', borderRadius: 8,
          background: 'var(--bg)', border: '1px solid var(--line)', marginBottom: 8,
        }}>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink)' }}>{r.nickname}</div>
            <div style={{ fontSize: 11, color: 'var(--muted)', marginTop: 2, fontFamily: 'monospace' }}>
              Chat ID: {r.chatId}
            </div>
          </div>
          <button onClick={() => handleRemove(r.id)} style={{
            fontSize: 11, color: 'var(--coral-ink)', background: 'none',
            border: 'none', padding: 0, cursor: 'pointer', fontWeight: 700,
          }}>삭제</button>
        </div>
      ))}

      {/* 수신자 추가 폼 */}
      <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', letterSpacing: '.05em' }}>
          수신자 추가
        </div>
        <input
          type="text"
          placeholder="닉네임"
          value={addNick}
          onChange={e => setAddNick(e.target.value)}
          style={inputStyle}
        />
        <input
          type="number"
          placeholder="Chat ID"
          value={addChatId}
          onChange={e => setAddChatId(e.target.value)}
          style={inputStyle}
        />
        <button onClick={handleAdd} disabled={adding} style={{
          padding: '8px 0', borderRadius: 8, border: 'none',
          background: adding ? 'var(--muted)' : primary, color: 'var(--paper)',
          fontSize: 12, fontWeight: 700, cursor: adding ? 'not-allowed' : 'pointer',
        }}>{adding ? '추가 중...' : '추가'}</button>
      </div>
    </div>
  );
}

export default TelegramRecipientList;
