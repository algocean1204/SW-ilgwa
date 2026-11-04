// 텔레그램 설정 탭.
// 봇 자격증명 카드와 업데이트 폼(좌측), 수신자 목록(우측)을 grid-cols-2로 배치한다.
import { useState, useEffect } from 'react';
import { getTelegramCredential, updateTelegramCredential } from '@/apis/admin';
import type { TelegramCredentialResponse, TelegramCredentialUpdateRequest } from '@/apis/admin';
import TelegramCredentialCard from './TelegramCredentialCard';
import TelegramRecipientList from './TelegramRecipientList';

interface AdminTelegramSettingsProps {
  primary: string;
}

function UpdateCredentialForm({ primary, onUpdated }: { primary: string; onUpdated: () => void }) {
  const [botToken, setBotToken] = useState('');
  const [adminChatId, setAdminChatId] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  async function handleSave() {
    if (saving) return;
    const body: TelegramCredentialUpdateRequest = {};
    if (botToken.trim()) body.botToken = botToken.trim();
    const cid = parseInt(adminChatId.trim(), 10);
    if (!isNaN(cid)) body.adminChatId = cid;
    if (!body.botToken && !body.adminChatId) return;

    setSaving(true);
    setSaved(false);
    try {
      await updateTelegramCredential(body);
      setBotToken('');
      setAdminChatId('');
      setSaved(true);
      onUpdated();
    } catch {
      // 오류는 사용자에게 알리지 않는다
    } finally {
      setSaving(false);
    }
  }

  const inputStyle = {
    width: '100%', padding: '9px 12px', borderRadius: 8,
    border: '1px solid var(--line)', fontSize: 12,
    background: 'var(--bg)', color: 'var(--ink)',
    outline: 'none', fontFamily: 'inherit', boxSizing: 'border-box' as const,
  };

  return (
    <div style={{
      background: 'var(--paper)', borderRadius: 14,
      border: '1px solid var(--line)', padding: '20px 22px',
      boxShadow: 'var(--shadow-sm)', marginTop: 12,
    }}>
      <div style={{
        fontSize: 11, fontWeight: 700, color: 'var(--ink-3)',
        letterSpacing: '.1em', textTransform: 'uppercase', marginBottom: 14,
      }}>
        자격증명 업데이트
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div>
          <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--ink-3)', marginBottom: 4 }}>봇 토큰 (선택)</div>
          <input
            type="password"
            placeholder="새 봇 토큰 입력..."
            value={botToken}
            onChange={e => setBotToken(e.target.value)}
            style={inputStyle}
          />
        </div>
        <div>
          <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--ink-3)', marginBottom: 4 }}>관리자 Chat ID (선택)</div>
          <input
            type="number"
            placeholder="새 Chat ID 입력..."
            value={adminChatId}
            onChange={e => setAdminChatId(e.target.value)}
            style={inputStyle}
          />
        </div>
        <button onClick={handleSave} disabled={saving} style={{
          padding: '9px 0', borderRadius: 10, border: 'none',
          background: saving ? 'var(--muted)' : primary,
          color: 'var(--paper)', fontSize: 12, fontWeight: 700,
          cursor: saving ? 'not-allowed' : 'pointer',
        }}>
          {saving ? '저장 중...' : '저장'}
        </button>
        {saved && <div style={{ fontSize: 11, color: 'var(--grass-ink)' }}>저장되었어요.</div>}
      </div>
    </div>
  );
}

function AdminTelegramSettings({ primary }: AdminTelegramSettingsProps) {
  const [cred, setCred] = useState<TelegramCredentialResponse | null>(null);
  const [loading, setLoading] = useState(true);

  function loadCred() {
    setLoading(true);
    getTelegramCredential()
      .then(res => { if (res.data) setCred(res.data); })
      .catch(() => {})
      .finally(() => setLoading(false));
  }

  useEffect(() => { loadCred(); }, []);

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24 }}>
      {/* 좌측: 자격증명 카드와 업데이트 폼 */}
      <div>
        {loading ? (
          <div style={{ padding: 24, fontSize: 13, color: 'var(--muted)' }}>불러오는 중...</div>
        ) : cred ? (
          <>
            <TelegramCredentialCard cred={cred} primary={primary} />
            <UpdateCredentialForm primary={primary} onUpdated={loadCred} />
          </>
        ) : (
          <div style={{ padding: 24, fontSize: 13, color: 'var(--coral-ink)' }}>
            자격증명을 불러오지 못했어요.
          </div>
        )}
      </div>

      {/* 우측: 수신자 목록. 목록은 컴포넌트가 직접 API로 불러온다 */}
      <div>
        <TelegramRecipientList primary={primary} />
      </div>
    </div>
  );
}

export default AdminTelegramSettings;
