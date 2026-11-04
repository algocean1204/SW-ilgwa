// 온보딩 4단계: 텔레그램 연동과 메시지 미리보기.
// 마운트 시 linkTelegram()으로 토큰과 봇 URL을 받고, 봇을 연 뒤 연결 상태를 확인한다.
import { useState, useEffect } from 'react';
import type { Palette } from '@/lib';
import Icon from '@/Icon';
import { linkTelegram, getTelegramStatus } from '@/apis';
import type { TelegramLinkResponse } from '@/apis';

export interface StepPaletteProps {
  palette: Palette;
}

// 텔레그램 미리보기 색. index.css 토큰을 쓴다.
const TG_BG = "var(--telegram-ui-bg)";
const TG_BORDER = "var(--telegram-ui-border)";

// 버튼 단계: open(텔레그램 열기) → verify(연결 확인) → done(연결 완료)
type ButtonPhase = 'open' | 'verify' | 'done';

export function OnboardingStep4({ palette }: StepPaletteProps) {
  // linkTelegram() 호출 중이면 true
  const [loading, setLoading] = useState(true);
  const [linkError, setLinkError] = useState<string | null>(null);
  // linkTelegram() 응답(토큰과 봇 URL)
  const [linkData, setLinkData] = useState<TelegramLinkResponse | null>(null);
  const [buttonPhase, setButtonPhase] = useState<ButtonPhase>('open');
  // getTelegramStatus() 호출 중일 때 true
  const [verifying, setVerifying] = useState(false);
  // getTelegramStatus() 응답의 linked 값
  const [connected, setConnected] = useState(false);

  // 토큰과 봇 URL을 바로 보여 주려고 마운트 시 linkTelegram()을 부른다
  useEffect(() => {
    let cancelled = false;

    const fetchLinkToken = async () => {
      setLoading(true);
      setLinkError(null);
      try {
        const res = await linkTelegram();
        if (!cancelled) {
          setLinkData(res.data);
        }
      } catch {
        if (!cancelled) {
          // 실패하면 버튼을 비활성화하고 오류 힌트를 보여 준다
          setLinkError('링크 생성에 실패했어요. 잠시 후 다시 시도해주세요.');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    fetchLinkToken();

    // 언마운트 뒤 도착한 응답은 무시한다
    return () => {
      cancelled = true;
    };
  }, []);

  // 봇 URL을 새 탭으로 열고 '연결 확인' 단계로 넘어간다
  const handleOpenTelegram = () => {
    if (!linkData) return;
    window.open(linkData.telegramBotUrl, '_blank');
    setButtonPhase('verify');
  };

  // getTelegramStatus()의 linked 값으로 연결 상태를 갱신한다
  const handleVerify = async () => {
    setVerifying(true);
    try {
      const res = await getTelegramStatus();
      if (res.data?.linked) {
        setConnected(true);
        setButtonPhase('done');
      }
      // linked가 false면 아직 봇에 메시지를 안 보낸 것이라 단계를 유지한다
    } catch {
      // 확인에 실패하면 단계를 유지해 다시 시도할 수 있게 한다
    } finally {
      setVerifying(false);
    }
  };

  const buttonDisabled = loading || !!linkError || buttonPhase === 'done';

  const buttonStyle: React.CSSProperties = {
    marginTop: 16,
    padding: "10px 18px",
    borderRadius: 10,
    fontSize: 13,
    fontWeight: 700,
    display: "inline-flex",
    alignItems: "center",
    gap: 6,
    background: (connected || buttonDisabled) ? palette.wash : "var(--telegram-blue)",
    color: (connected || buttonDisabled) ? palette.primary : "var(--paper)",
    border: (connected || buttonDisabled) ? `1px solid ${palette.primary2}` : "none",
    opacity: buttonDisabled && !connected ? 0.5 : 1,
    cursor: buttonDisabled ? 'not-allowed' : 'pointer',
  };

  return (
    <div>
      <h1 style={{ margin: 0, fontSize: 32, fontWeight: 800, letterSpacing: 0, color: "var(--ink)" }}>
        텔레그램으로 매일 보낼까요?
      </h1>
      <p style={{ marginTop: 10, fontSize: 14, color: "var(--ink-3)" }}>
        앱을 따로 열지 않아도 암기노트와 퀴즈가 텔레그램에 도착해요. 나중에 연결해도 돼요.
      </p>

      <div style={{
        marginTop: 26, padding: "26px 28px", borderRadius: 18,
        background: TG_BG,
        border: `1px solid ${TG_BORDER}`,
        display: "grid", gridTemplateColumns: "1.1fr 1fr", gap: 24, alignItems: "center",
      }}>
        {/* 봇 연결 안내 + 버튼 */}
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
            <div style={{
              width: 40, height: 40, borderRadius: "50%", background: "var(--telegram-blue)",
              display: "grid", placeItems: "center", color: "var(--paper)",
            }}><Icon name="telegram" size={20} color="var(--paper)" /></div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 800, color: "var(--ink)", letterSpacing: 0 }}>@ilgwa_bot</div>
              <div style={{ fontSize: 12, color: "var(--ink-3)" }}>텔레그램 학습 봇</div>
            </div>
          </div>

          {/* 토큰 안내 문구. 로딩, 오류, 정상 상태마다 다르다 */}
          <p style={{ margin: "6px 0 0", fontSize: 13, color: "var(--ink-2)", lineHeight: 1.6 }}>
            봇에게{" "}
            <span className="mono" style={{ padding: "1px 6px", background: "var(--sand)", borderRadius: 4, fontSize: 11 }}>
              {loading
                ? "/start ···"
                : linkError
                  ? "/start ???"
                  : `/start ${linkData?.linkToken ?? "···"}`}
            </span>
            {" "}을 보내면 연결돼요.
          </p>

          {/* API 오류 힌트 */}
          {linkError && (
            <p style={{ marginTop: 6, fontSize: 12, color: "var(--error, #e53e3e)" }}>
              {linkError}
            </p>
          )}

          {/* 단계별 버튼 */}
          {buttonPhase === 'done' || connected ? (
            <button
              disabled
              className="lift"
              style={buttonStyle}
            >
              <Icon name="check" size={11} style={{ marginRight: 4 }} />
              연결 완료
            </button>
          ) : buttonPhase === 'verify' ? (
            // 텔레그램을 연 뒤에는 연결 확인 버튼을 보여 준다
            <button
              onClick={handleVerify}
              disabled={verifying}
              className="lift"
              style={{
                ...buttonStyle,
                opacity: verifying ? 0.6 : 1,
                cursor: verifying ? 'not-allowed' : 'pointer',
              }}
            >
              {verifying ? "확인 중…" : "연결 확인"}
            </button>
          ) : (
            // 처음에는 텔레그램 열기 버튼을 보여 준다
            <button
              onClick={handleOpenTelegram}
              disabled={buttonDisabled}
              className="lift"
              style={buttonStyle}
            >
              {loading ? "불러오는 중…" : "텔레그램 열기"}
            </button>
          )}
        </div>

        {/* 텔레그램 메시지 미리보기 카드 */}
        <div style={{
          padding: "12px 14px", borderRadius: 14, background: "var(--paper)",
          border: "1px solid var(--line)", boxShadow: "var(--shadow-sm)",
        }}>
          <div style={{
            display: "flex", alignItems: "center", gap: 8,
            marginBottom: 10, paddingBottom: 8, borderBottom: "1px solid var(--line)",
          }}>
            <div style={{
              width: 26, height: 26, borderRadius: "50%", background: "var(--grass-wash)",
              display: "grid", placeItems: "center",
            }}>
              <Icon name="owl" size={15} color="var(--brand-deep)" />
            </div>
            <div style={{ fontSize: 12, fontWeight: 700, color: "var(--ink)" }}>일과 / 오전 7:30</div>
          </div>
          <div style={{
            padding: "8px 10px", borderRadius: "10px 10px 10px 2px",
            background: TG_BG, fontSize: 12, color: "var(--ink)", lineHeight: 1.5,
          }}>
            오늘의 암기노트 🦉<br />
            <b>Q.</b> SRS는 어느 단계?<br />
            <span style={{ color: "var(--ink-3)" }}>a/b/c 중 선택</span>
          </div>
        </div>
      </div>
    </div>
  );
}
