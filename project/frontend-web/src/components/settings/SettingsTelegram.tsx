// 텔레그램 탭 최상위 컴포넌트. 상태, 데이터 로드, API 호출, 낙관 업데이트, 폴링,
// 에러/성공 메시지를 다루고 화면은 하위 컴포넌트가 그린다.
// TelegramStatusBanner: 연결 현황 요약 | TelegramNotices: 에러/성공 알림
// TelegramLinkCard: 연동 카드 | TelegramAutoSendOptions: 옵션 토글
// TelegramMessagePreview: 메시지 미리보기
import { useCallback, useEffect, useRef, useState } from 'react';
import type { Palette } from '@/lib';
import {
  getTelegramPreferences,
  getTelegramStatus,
  linkTelegram,
  updateTelegramPreferences,
} from '@/apis';
import type {
  TelegramLinkResponse,
  TelegramPreferencesResponse,
  TelegramStatusResponse,
  UpdateTelegramPreferencesRequest,
} from '@/apis';
import { TelegramStatusBanner } from './TelegramStatusBanner';
import { TelegramNotices } from './TelegramNotices';
import { TelegramLinkCard } from './TelegramLinkCard';
import { TelegramAutoSendOptions } from './TelegramAutoSendOptions';
import { TelegramMessagePreview } from './TelegramMessagePreview';
import { formatError } from './telegramHelpers';

interface SettingsTelegramProps {
  palette: Palette;
}

export function SettingsTelegram({ palette }: SettingsTelegramProps) {
  const [status, setStatus] = useState<TelegramStatusResponse | null>(null);
  const [linkInfo, setLinkInfo] = useState<TelegramLinkResponse | null>(null);
  const [prefs, setPrefs] = useState<TelegramPreferencesResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isLinking, setIsLinking] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  // 옵션 토글 요청 중 여부. 연타로 서버와 UI가 어긋나지 않게 한다
  const prefsPendingRef = useRef(false);

  const loadStatus = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      // 연동 상태와 자동 전송 옵션을 함께 로드한다.
      // 옵션 조회 실패는 화면 표시를 막지 않도록 개별 처리하고,
      // 상태 조회 실패만 치명적 오류로 다룬다.
      const [statusResult, prefsResult] = await Promise.allSettled([
        getTelegramStatus(),
        getTelegramPreferences(),
      ]);

      if (statusResult.status === 'rejected') throw statusResult.reason;
      if (!statusResult.value.data) throw new Error('텔레그램 상태 응답이 비어 있어요.');
      setStatus(statusResult.value.data);
      if (statusResult.value.data.linked) setLinkInfo(null);

      // 옵션은 보조 정보라 조회가 실패해도 연동 카드와 미리보기는 보여준다
      if (prefsResult.status === 'fulfilled' && prefsResult.value.data) {
        setPrefs(prefsResult.value.data);
      }
    } catch (error) {
      setStatus(null);
      setErrorMessage(formatError(error));
    } finally {
      setIsLoading(false);
    }
  }, []);

  // 자동 전송 옵션 토글. UI를 먼저 바꾸고 서버 응답으로 맞추며, 실패하면 되돌린다
  const handleTogglePref = useCallback(
    async (req: UpdateTelegramPreferencesRequest) => {
      if (!prefs || !status?.linked || prefsPendingRef.current) return;
      prefsPendingRef.current = true;

      const prev = { ...prefs };
      setPrefs({ ...prefs, ...req });
      setErrorMessage(null);

      try {
        const response = await updateTelegramPreferences(req);
        if (response.data) setPrefs(response.data);
      } catch (error) {
        // 실패하면 미리 바꾼 값을 되돌린다
        setPrefs(prev);
        setErrorMessage(formatError(error));
      } finally {
        prefsPendingRef.current = false;
      }
    },
    [prefs, status?.linked],
  );

  useEffect(() => { void loadStatus(); }, [loadStatus]);

  // linkInfo가 있는 동안 3초 간격으로 연동 완료 여부를 폴링한다
  useEffect(() => {
    if (!linkInfo || status?.linked) return;

    // 타임존 없는 문자열도 UTC로 읽는다.
    // 백엔드가 offset/Z 포함 ISO 문자열을 내려주면 그대로 파싱되고,
    // zone 없는 값에는 'Z'를 붙여 UTC로 해석한다.
    const rawExpiry = linkInfo.expiresAt ?? '';
    const hasZoneInfo = /[Zz]$/.test(rawExpiry) || /[+-]\d{2}:\d{2}$/.test(rawExpiry);
    const expiresAt = new Date(hasZoneInfo ? rawExpiry : `${rawExpiry}Z`).getTime();

    const pollStatus = async () => {
      if (Number.isFinite(expiresAt) && Date.now() > expiresAt) {
        setSuccessMessage(null);
        setErrorMessage('텔레그램 연동 링크가 만료됐어요. 새 링크를 생성해주세요.');
        setLinkInfo(null);
        return;
      }
      try {
        const response = await getTelegramStatus();
        if (!response.data) return;
        setStatus(response.data);
        if (response.data.linked) {
          setLinkInfo(null);
          setErrorMessage(null);
          setSuccessMessage('텔레그램 연동이 완료됐어요.');
        }
      } catch {
        // 자동 확인 중 발생한 일시 오류는 수동 새로고침/전역 세션 처리에 맡긴다
      }
    };

    const timerId = window.setInterval(() => { void pollStatus(); }, 3000);
    void pollStatus();
    return () => window.clearInterval(timerId);
  }, [linkInfo, status?.linked]);

  const handleCreateLink = async () => {
    if (isLinking || status?.linked) return;
    setIsLinking(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const response = await linkTelegram();
      if (!response.data) throw new Error('텔레그램 연동 링크 응답이 비어 있어요.');
      setLinkInfo(response.data);
      setSuccessMessage('텔레그램 연동 링크가 생성됐어요. 5분 동안 연결 상태를 자동으로 확인합니다.');
    } catch (error) {
      setLinkInfo(null);
      setErrorMessage(formatError(error));
    } finally {
      setIsLinking(false);
    }
  };

  const isLinked = status?.linked === true;

  return (
    <>
      <TelegramStatusBanner status={status} isLoading={isLoading} isLinked={isLinked} />
      <TelegramNotices
        errorMessage={errorMessage}
        successMessage={successMessage}
        onRetry={() => void loadStatus()}
      />
      <TelegramLinkCard
        palette={palette}
        status={status}
        linkInfo={linkInfo}
        isLoading={isLoading}
        isLinking={isLinking}
        isLinked={isLinked}
        onCreateLink={() => void handleCreateLink()}
        onRefresh={() => void loadStatus()}
      />
      <TelegramAutoSendOptions
        palette={palette}
        isLinked={isLinked}
        prefs={prefs}
        onToggleNote={(v) => void handleTogglePref({ noteEnabled: v })}
        onToggleQuiz={(v) => void handleTogglePref({ quizEnabled: v })}
        onToggleFriend={(v) => void handleTogglePref({ friendEnabled: v })}
      />
      <TelegramMessagePreview />
    </>
  );
}
