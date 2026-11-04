// 봇 자격증명 현황 카드. 토큰 fingerprint는 마스킹해서 보여준다.
import type { TelegramCredentialResponse } from '@/apis/admin';

interface TelegramCredentialCardProps {
  cred: TelegramCredentialResponse;
  primary: string;
}

// 봇 토큰 fingerprint는 앞 4자만 보이고 나머지는 최대 8개의 *로 가린다
export function maskFingerprint(fp: string | null | undefined): string {
  if (!fp) return '—';
  if (fp.length <= 4) return fp;
  return `${fp.slice(0, 4)}${'*'.repeat(Math.min(fp.length - 4, 8))}`;
}

function formatDateTime(iso: string | null): string {
  if (!iso) return '없음';
  const d = new Date(iso);
  return d.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--line-2)' }}>
      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', letterSpacing: '.05em' }}>{label}</span>
      <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink)', fontFamily: 'monospace' }}>{value}</span>
    </div>
  );
}

function TelegramCredentialCard({ cred, primary }: TelegramCredentialCardProps) {
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
        봇 자격증명 현황
      </div>
      <Row label="토큰 핑거프린트" value={maskFingerprint(cred.botTokenFingerprint)} />
      <Row label="관리자 Chat ID" value={cred.adminChatId != null ? String(cred.adminChatId) : '—'} />
      <Row label="마지막 갱신" value={formatDateTime(cred.lastRotatedAt)} />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 8 }}>
        <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', letterSpacing: '.05em' }}>헬스체크</span>
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink)', fontFamily: 'monospace' }}>
          {formatDateTime(cred.lastHealthCheckAt)}
        </span>
      </div>
    </div>
  );
}

export default TelegramCredentialCard;
