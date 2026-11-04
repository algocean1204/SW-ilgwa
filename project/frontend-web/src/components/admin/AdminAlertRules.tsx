// 에러 유형별 알림 규칙 관리.
// 토글이나 severity를 바꾸면 바로 API를 호출하고, 성공하면 로컬 상태에 반영한다.
import { useState, useEffect } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import { getAlertRules, updateAlertRule } from '@/apis/admin';
import type { AlertRuleResponse, AlertSeverity } from '@/apis/admin';

interface AdminAlertRulesProps {
  primary: string;
}

// severity별 색상. 팔레트에서 가장 가까운 토큰을 쓴다.
const SEVERITY_COLOR: Record<AlertSeverity, { bg: string; ink: string }> = {
  CRITICAL: { bg: 'var(--coral-wash)', ink: 'var(--coral-ink)' },
  WARN:     { bg: 'var(--honey-wash, var(--bg))', ink: 'var(--honey-ink, var(--ink-2))' },
  INFO:     { bg: 'var(--grass-wash)', ink: 'var(--grass-ink)' },
};

const SEVERITY_OPTIONS: AlertSeverity[] = ['INFO', 'WARN', 'CRITICAL'];
const SEVERITY_LABEL: Record<AlertSeverity, string> = { INFO: 'INFO', WARN: 'WARN', CRITICAL: 'CRITICAL' };

function AdminAlertRules(_props: AdminAlertRulesProps) {
  const t = useTweaks();
  const palette = PALETTES[t.palette] ?? PALETTES.grass!;
  const [rules, setRules] = useState<AlertRuleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    setLoading(true);
    getAlertRules()
      .then(res => { if (res.data) setRules(res.data.rules); })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  async function handleToggle(rule: AlertRuleResponse) {
    const next = !rule.enabled;
    try {
      await updateAlertRule(rule.id, { enabled: next });
      setRules(prev => prev.map(r => r.id === rule.id ? { ...r, enabled: next } : r));
    } catch {
      // 실패 시 로컬 상태 유지
    }
  }

  async function handleSeverity(rule: AlertRuleResponse, sev: AlertSeverity) {
    try {
      await updateAlertRule(rule.id, { severity: sev });
      setRules(prev => prev.map(r => r.id === rule.id ? { ...r, severity: sev } : r));
    } catch {
      // 실패 시 로컬 상태 유지
    }
  }

  return (
    <div style={{
      background: 'var(--paper)', borderRadius: 14,
      border: '1px solid var(--line)', boxShadow: 'var(--shadow-md)', overflow: 'hidden',
    }}>
      <div style={{
        padding: '14px 20px', borderBottom: '1px solid var(--line-2)',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-3)' }}>알림 규칙</div>
        <div style={{ fontSize: 11, color: 'var(--muted)' }}>{rules.length}개 규칙</div>
      </div>

      {loading && (
        <div style={{ padding: 24, textAlign: 'center', fontSize: 13, color: 'var(--muted)' }}>
          불러오는 중...
        </div>
      )}
      {error && !loading && (
        <div style={{ padding: 24, textAlign: 'center', fontSize: 13, color: 'var(--coral-ink)' }}>
          알림 규칙을 불러오지 못했어요.
        </div>
      )}

      {/* 규칙 행 목록 */}
      {!loading && !error && rules.map((rule, i) => {
        const sc = SEVERITY_COLOR[rule.severity];
        return (
          <div key={rule.id} style={{
            display: 'flex', alignItems: 'center', gap: 12,
            padding: '12px 20px', borderBottom: i < rules.length - 1 ? '1px solid var(--line-2)' : 'none',
            background: i % 2 === 0 ? 'var(--paper)' : 'var(--bg)',
          }}>
            <div style={{ flex: 1, fontSize: 13, fontWeight: 700, color: 'var(--ink)' }}>
              {rule.displayName}
            </div>

            <select
              value={rule.severity}
              onChange={e => handleSeverity(rule, e.target.value as AlertSeverity)}
              style={{
                padding: '4px 8px', borderRadius: 7,
                border: `1px solid ${sc.ink}`,
                background: sc.bg, color: sc.ink,
                fontSize: 11, fontWeight: 700,
                cursor: 'pointer', fontFamily: 'inherit', outline: 'none',
              }}
            >
              {SEVERITY_OPTIONS.map(s => (
                <option key={s} value={s}>{SEVERITY_LABEL[s]}</option>
              ))}
            </select>

            <button
              onClick={() => handleToggle(rule)}
              style={{
                width: 40, height: 22, borderRadius: 11, border: 'none',
                background: rule.enabled ? palette.primary : 'var(--line)',
                cursor: 'pointer', position: 'relative', transition: 'background .15s',
                flexShrink: 0,
              }}
            >
              <span style={{
                position: 'absolute', top: 3,
                left: rule.enabled ? 21 : 3,
                width: 16, height: 16, borderRadius: '50%',
                background: 'var(--paper)',
                transition: 'left .15s',
              }} />
            </button>
          </div>
        );
      })}
    </div>
  );
}

export default AdminAlertRules;
