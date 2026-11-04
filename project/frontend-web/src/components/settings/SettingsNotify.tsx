// 알림·접근성 설정 탭.
// getProfile()로 초기값을 읽고, 토글하면 updateSettings()를 부른다.
import { useCallback, useEffect, useRef, useState } from 'react';
import type { Palette } from '@/lib';
import {
  ApiError,
  getProfile,
  updateSettings,
} from '@/apis';
import type {
  SettingsResponse,
  UpdateSettingsRequest,
} from '@/apis';
import { Card, ToggleRow } from './SettingsPrimitives';

interface SettingsNotifyProps {
  palette: Palette;
}

export function SettingsNotify({ palette }: SettingsNotifyProps) {
  const [settings, setSettings] = useState<SettingsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // 요청 중 여부. 토글을 동시에 눌러도 요청이 겹치지 않게 한다
  const pendingRef = useRef(false);

  const loadSettings = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await getProfile();
      if (!res.data) throw new Error('프로필 응답이 비어 있습니다.');
      setSettings(res.data.settings);
    } catch (e) {
      setError(
        e instanceof ApiError
          ? (e.errorResponse?.message ?? `설정을 불러오지 못했습니다. (${e.status})`)
          : '설정을 불러오지 못했습니다.',
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSettings();
  }, [loadSettings]);

  const handleToggle = useCallback(async (req: UpdateSettingsRequest) => {
    if (!settings) return;
    // 요청 중이면 무시한다. 토글을 연타해도 카운터가 어긋나지 않는다
    if (pendingRef.current) return;
    pendingRef.current = true;

    // 토글이 바로 반응하게 API 응답 전에 UI부터 바꾼다
    const prev = { ...settings, notifications: { ...settings.notifications } };
    setSettings({
      ...settings,
      ...('highContrast' in req && req.highContrast !== undefined
        ? { highContrast: req.highContrast }
        : {}),
      ...('keyboardNavigation' in req && req.keyboardNavigation !== undefined
        ? { keyboardNavigation: req.keyboardNavigation }
        : {}),
      ...(req.notifications
        ? { notifications: { ...settings.notifications, ...req.notifications } }
        : {}),
    });

    try {
      const res = await updateSettings(req);
      // 서버가 돌려준 값으로 최종 상태를 맞춘다
      if (res.data) setSettings(res.data);
    } catch (e) {
      // 실패하면 미리 바꾼 값을 되돌린다. 카운터도 원래대로 돌아간다
      setSettings(prev);
      setError(
        e instanceof ApiError
          ? (e.errorResponse?.message ?? `설정 저장에 실패했습니다. (${e.status})`)
          : '설정 저장에 실패했습니다.',
      );
    } finally {
      pendingRef.current = false;
    }
  }, [settings]);

  const n = settings?.notifications;
  // 카운터도 토글과 같은 필드별 기본값(??의 오른쪽)으로 센다. 기준이 다르면
  // "3개 ON·카운터 0"처럼 어긋나므로 ?? true / ?? false를 토글 val과 똑같이 쓴다.
  const activeCount = [
    n?.lesson ?? true,
    n?.assignment ?? true,
    n?.community ?? true,
    n?.marketing ?? false,
  ].filter(Boolean).length;

  if (isLoading) {
    return (
      <div style={{ padding: 40, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
        설정을 불러오는 중...
      </div>
    );
  }

  return (
    <>
      {error && (
        <div
          role="alert"
          style={{
            marginBottom: 14, padding: '12px 14px', borderRadius: 'var(--radius-md)',
            background: 'var(--state-danger-bg)', border: '1px solid var(--coral)',
            color: 'var(--danger)', fontSize: 12.5, fontWeight: 700,
            display: 'flex', alignItems: 'center', gap: 10,
          }}
        >
          <span style={{ flex: 1 }}>{error}</span>
          <button
            onClick={() => void loadSettings()}
            style={{ fontSize: 11.5, fontWeight: 800, color: 'var(--danger)' }}
          >
            다시 시도
          </button>
        </div>
      )}

      {/* 알림 현황 요약 */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 14,
        padding: '12px 16px', marginBottom: 14, borderRadius: 'var(--radius-lg)',
        background: 'var(--surface-subtle)', border: '1px solid var(--border-soft)',
      }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)', marginBottom: 3 }}>
            {activeCount}개 알림 활성 중
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
            알림 카테고리별로 수신 여부를 설정할 수 있습니다
          </div>
        </div>
      </div>

      <Card title="알림 설정" subtitle="카테고리별 알림 수신 여부">
        <ToggleRow
          palette={palette}
          val={n?.lesson ?? true}
          title="강의 알림"
          desc="새 슬라이드, 챕터 완료 등 학습 진행 관련 알림"
          onToggle={(v) => void handleToggle({ notifications: { lesson: v } })}
        />
        <ToggleRow
          palette={palette}
          val={n?.assignment ?? true}
          title="과제 알림"
          desc="과제 출제, 채점 완료 등 과제 관련 알림"
          onToggle={(v) => void handleToggle({ notifications: { assignment: v } })}
        />
        <ToggleRow
          palette={palette}
          val={n?.community ?? true}
          title="커뮤니티 알림"
          desc="커뮤니티 활동 관련 알림"
          onToggle={(v) => void handleToggle({ notifications: { community: v } })}
        />
        <ToggleRow
          palette={palette}
          val={n?.marketing ?? false}
          title="마케팅 알림"
          desc="프로모션, 이벤트, 할인 등 마케팅 정보 수신"
          onToggle={(v) => void handleToggle({ notifications: { marketing: v } })}
        />
      </Card>
    </>
  );
}
