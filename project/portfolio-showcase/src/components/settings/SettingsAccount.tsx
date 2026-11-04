// 계정 탭. 프로필 API 조회·수정과 OAuth 연결 계정 목록을 그린다.
import { useCallback, useEffect, useRef, useState, type ChangeEvent } from 'react';
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import { ApiError, clearAccessToken, deleteUserProfileImage, getProfile, logout, updateProfile, updateSettings, uploadUserProfileImage, withdraw } from '@/apis';
import type { UpdateProfileRequest, UserProfileResponse, SettingsResponse } from '@/apis';
import StatusBadge from '@/components/ui/StatusBadge';
import Icon from '@/Icon';
import { Card, Field, Input } from './SettingsPrimitives';
import WithdrawConfirmDialog from './WithdrawConfirmDialog';

interface SettingsAccountProps {
  palette: Palette;
  setRoute: RouteSetter;
}

interface AccountForm {
  nickname: string;
  email: string;
  profileImageUrl: string;
  grade: string;
  learningGoal: string;
  preferredTone: string;
  interestsText: string;
}


const EMPTY_FORM: AccountForm = {
  nickname: '',
  email: '',
  profileImageUrl: '',
  grade: '',
  learningGoal: '',
  preferredTone: '',
  interestsText: '',
};

const trimOrUndefined = (value: string): string | undefined => {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
};

const parseInterests = (value: string): string[] =>
  value
    .split(',')
    .map(item => item.trim())
    .filter(Boolean);

const toAccountForm = (profile: UserProfileResponse): AccountForm => ({
  nickname: profile.nickname ?? '',
  email: profile.email ?? '',
  profileImageUrl: profile.profileImageUrl ?? '',
  grade: profile.grade ?? '',
  learningGoal: profile.learningGoal ?? '',
  preferredTone: profile.preferredTone ?? '',
  // interests가 null/undefined면 .join이 throw하므로 ?? []로 빈 배열을 쓴다.
  interestsText: (profile.interests ?? []).join(', '),
});

const formatError = (error: unknown): string => {
  if (error instanceof ApiError) {
    if (error.status === 401) return '로그인 세션이 만료됐어요. 다시 로그인한 뒤 확인해주세요.';
    return error.errorResponse?.message ?? `내 정보 요청에 실패했어요. (${error.status})`;
  }
  if (error instanceof Error) return error.message;
  return '내 정보 요청에 실패했어요.';
};

const subscriptionBadge = (
  status: UserProfileResponse['subscriptionStatus'] | undefined,
): { label: string; tone: 'success' | 'warning' | 'danger' | 'neutral' | 'info' } => {
  switch (status) {
    case 'PRO':
      return { label: 'Pro', tone: 'success' };
    case 'PREMIUM':
      return { label: 'Premium', tone: 'info' };
    case 'EXPIRED':
      return { label: 'Expired', tone: 'danger' };
    case 'FREE':
      return { label: 'Free', tone: 'neutral' };
    default:
      return { label: '확인 중', tone: 'neutral' };
  }
};

function Avatar({
  imageUrl,
  name,
  palette,
  size,
}: {
  imageUrl: string;
  name: string;
  palette: Palette;
  size: number;
}) {
  const initial = name.trim().charAt(0).toUpperCase() || '?';

  return (
    <div style={{
      width: size,
      height: size,
      borderRadius: "50%",
      flexShrink: 0,
      background: `linear-gradient(135deg, ${palette.primary}, ${palette.primary2})`,
      display: "grid",
      placeItems: "center",
      overflow: "hidden",
      color: "var(--paper)",
      fontSize: size >= 60 ? 24 : 18,
      fontWeight: 800,
    }}>
      {imageUrl ? (
        <img
          src={imageUrl}
          alt=""
          style={{ width: "100%", height: "100%", objectFit: "cover" }}
        />
      ) : initial}
    </div>
  );
}

export function SettingsAccount({ palette, setRoute }: SettingsAccountProps) {
  const [profile, setProfile] = useState<UserProfileResponse | null>(null);
  const [form, setForm] = useState<AccountForm>(EMPTY_FORM);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [photoHovered, setPhotoHovered] = useState(false);
  // S3 업로드/삭제 진행 중 여부. 중복 요청을 막고 로딩 UI를 띄우는 데 쓴다
  const [isUploading, setIsUploading] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [withdrawDialogOpen, setWithdrawDialogOpen] = useState(false);
  const [isWithdrawing, setIsWithdrawing] = useState(false);
  const photoInputRef = useRef<HTMLInputElement>(null);

  const handleLogout = useCallback(async () => {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    try {
      await logout();
    } catch {
      // 로그아웃은 실패해도 로컬 토큰을 지우고 로그인 화면으로 이동한다
    } finally {
      clearAccessToken();
      setRoute('auth');
    }
  }, [isLoggingOut, setRoute]);

  const handleWithdraw = useCallback(async () => {
    if (isWithdrawing) return;
    setIsWithdrawing(true);
    setErrorMessage(null);
    try {
      await withdraw();
      // 탈퇴가 성공하면 로그아웃처럼 토큰을 지우고 로그인 화면으로 간다
      clearAccessToken();
      setRoute('auth');
    } catch {
      // 탈퇴가 실패하면 계정이 남아 있으므로 토큰을 지우지 않고 에러를 보여준다
      setErrorMessage('회원 탈퇴에 실패했어요. 잠시 후 다시 시도해주세요.');
      setWithdrawDialogOpen(false);
    } finally {
      setIsWithdrawing(false);
    }
  }, [isWithdrawing, setRoute]);

  // 일일 학습 목표 슬라이더 드래그 중 로컬 값
  const [goalDraft, setGoalDraft] = useState<number | null>(null);
  const goalDraftRef = useRef<number | null>(null);
  const [settingsData, setSettingsData] = useState<SettingsResponse | null>(null);

  const displayGoal = goalDraft ?? settingsData?.dailyStudyGoalMinutes ?? 30;

  const handleGoalChange = (e: ChangeEvent<HTMLInputElement>) => {
    const v = Math.max(5, Number(e.target.value));
    setGoalDraft(v);
    goalDraftRef.current = v;
  };

  const commitGoal = async () => {
    const v = goalDraftRef.current;
    if (v !== null && v !== settingsData?.dailyStudyGoalMinutes) {
      const prev = settingsData;
      if (settingsData) setSettingsData({ ...settingsData, dailyStudyGoalMinutes: v });
      try {
        const res = await updateSettings({ dailyStudyGoalMinutes: v });
        if (res.data) setSettingsData(res.data);
      } catch {
        if (prev) setSettingsData(prev);
      }
    }
    setGoalDraft(null);
    goalDraftRef.current = null;
  };

  const loadProfile = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const response = await getProfile();
      if (!response.data) {
        throw new Error('내 정보 응답이 비어 있어요.');
      }
      setProfile(response.data);
      setForm(toAccountForm(response.data));
      setSettingsData(response.data.settings);
    } catch (error) {
      setProfile(null);
      setErrorMessage(formatError(error));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  const handlePhotoChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    // 백엔드 허용 타입과 같은 기준으로 미리 검사한다
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      alert('JPG, PNG, WebP 이미지만 올릴 수 있어요');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      alert('5MB 이하 이미지만 올릴 수 있어요');
      return;
    }
    setIsUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      const res = await uploadUserProfileImage(fd);
      if (res.data?.profileImageUrl) {
        setForm(prev => ({ ...prev, profileImageUrl: res.data!.profileImageUrl }));
        if (profile) {
          setProfile({ ...profile, profileImageUrl: res.data.profileImageUrl });
        }
        setSuccessMessage('프로필 사진이 업로드됐어요.');
      }
    } catch {
      setErrorMessage('프로필 사진 업로드에 실패했어요.');
    } finally {
      setIsUploading(false);
    }
  };

  const handleRemovePhoto = async () => {
    setIsUploading(true);
    try {
      await deleteUserProfileImage();
      setForm(prev => ({ ...prev, profileImageUrl: '' }));
      if (profile) {
        setProfile({ ...profile, profileImageUrl: null });
      }
      setSuccessMessage('프로필 사진이 삭제됐어요.');
    } catch {
      setErrorMessage('프로필 사진 삭제에 실패했어요.');
    } finally {
      setIsUploading(false);
    }
  };

  const setField = (key: keyof AccountForm) => (event: ChangeEvent<HTMLInputElement>) => {
    const { value } = event.target;
    setForm(prev => ({ ...prev, [key]: value }));
    setSuccessMessage(null);
  };

  const resetForm = () => {
    if (!profile) return;
    setForm(toAccountForm(profile));
    setErrorMessage(null);
    setSuccessMessage(null);
  };

  const handleSave = async () => {
    if (isSaving) return;
    const nickname = form.nickname.trim();
    if (!nickname) {
      setErrorMessage('이름을 입력해주세요.');
      return;
    }

    // 프로필 이미지는 전용 엔드포인트(POST/DELETE /users/me/profile-image)로 관리하므로 여기서 제외
    const body: UpdateProfileRequest = {
      nickname,
      grade: trimOrUndefined(form.grade),
      learningGoal: trimOrUndefined(form.learningGoal),
      preferredTone: trimOrUndefined(form.preferredTone),
      interests: parseInterests(form.interestsText),
    };

    setIsSaving(true);
    setErrorMessage(null);
    setSuccessMessage(null);
    try {
      const response = await updateProfile(body);
      if (!response.data) {
        throw new Error('저장 응답이 비어 있어요.');
      }
      setProfile(response.data);
      setForm(toAccountForm(response.data));
      setSuccessMessage('내 정보가 저장됐어요.');
    } catch (error) {
      setErrorMessage(formatError(error));
    } finally {
      setIsSaving(false);
    }
  };

  const badge = subscriptionBadge(profile?.subscriptionStatus);
  const displayName = form.nickname || profile?.nickname || '사용자';
  const profileImageUrl = form.profileImageUrl.trim();
  const metaText = profile
    ? profile.email
    : '내 정보를 불러오는 중';

  return (
    <>
      {/* 계정 현황 요약 카드 */}
      <div style={{
        display: "flex", alignItems: "center", gap: 14,
        padding: "12px 16px", marginBottom: 14, borderRadius: "var(--radius-lg)",
        background: "var(--surface-subtle)", border: "1px solid var(--border-soft)",
      }}>
        <Avatar imageUrl={profileImageUrl} name={displayName} palette={palette} size={44} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <span style={{ fontSize: 13, fontWeight: 800, color: "var(--text-primary)" }}>
              {isLoading ? '불러오는 중...' : displayName}
            </span>
            <StatusBadge tone={badge.tone} label={badge.label} size="sm" />
          </div>
          <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 2 }}>
            {metaText}
          </div>
        </div>
      </div>

      {errorMessage && (
        <div
          role="alert"
          style={{
            marginBottom: 14,
            padding: "12px 14px",
            borderRadius: "var(--radius-md)",
            background: "var(--state-danger-bg)",
            border: "1px solid var(--coral)",
            color: "var(--danger)",
            fontSize: 12.5,
            fontWeight: 700,
            display: "flex",
            alignItems: "center",
            gap: 10,
          }}
        >
          <span style={{ flex: 1 }}>{errorMessage}</span>
          <button
            onClick={() => errorMessage.includes('로그인') ? setRoute('auth') : void loadProfile()}
            style={{ fontSize: 11.5, fontWeight: 800, color: "var(--danger)" }}
          >
            {errorMessage.includes('로그인') ? '로그인' : '다시 시도'}
          </button>
        </div>
      )}

      {successMessage && (
        <div
          role="status"
          style={{
            marginBottom: 14,
            padding: "11px 14px",
            borderRadius: "var(--radius-md)",
            background: "var(--state-success-bg)",
            border: "1px solid var(--accent-soft)",
            color: "var(--accent-primary)",
            fontSize: 12.5,
            fontWeight: 700,
          }}
        >
          {successMessage}
        </div>
      )}

      <Card title="프로필" subtitle="튜터와 다른 학습자에게 표시되는 정보">
        <div style={{ display: "flex", alignItems: "center", gap: 16, padding: "6px 0 12px" }}>
          <div style={{ position: 'relative', flexShrink: 0 }}>
            <div
              role="button"
              tabIndex={0}
              aria-label="프로필 사진 변경"
              onClick={() => { if (!isUploading) photoInputRef.current?.click(); }}
              onKeyDown={e => { if (!isUploading && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); photoInputRef.current?.click(); } }}
              onMouseEnter={() => setPhotoHovered(true)}
              onMouseLeave={() => setPhotoHovered(false)}
              style={{ cursor: isUploading ? 'not-allowed' : 'pointer', borderRadius: '50%', position: 'relative' }}
            >
              <Avatar imageUrl={profileImageUrl} name={displayName} palette={palette} size={80} />
              <div style={{
                position: 'absolute', inset: 0, borderRadius: '50%',
                background: 'rgba(0,0,0,.45)', color: 'var(--paper)',
                display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2,
                opacity: (photoHovered || isUploading) ? 1 : 0, transition: 'opacity .15s',
                pointerEvents: 'none',
              }}>
                {isUploading ? (
                  <span style={{ fontSize: 10, fontWeight: 700 }}>업로드 중...</span>
                ) : (
                  <>
                    <Icon name="camera" size={16} color="var(--paper)" />
                    <span style={{ fontSize: 10, fontWeight: 700 }}>{profileImageUrl ? '변경' : '업로드'}</span>
                  </>
                )}
              </div>
            </div>
            {profileImageUrl && (
              <button
                onClick={() => { void handleRemovePhoto(); }}
                disabled={isUploading}
                title="사진 제거"
                style={{
                  position: 'absolute', top: -2, right: -2, width: 22, height: 22, borderRadius: '50%',
                  background: 'var(--paper)', border: '1px solid var(--line)', boxShadow: 'var(--shadow-sm)',
                  display: 'grid', placeItems: 'center', color: 'var(--danger)',
                  cursor: isUploading ? 'not-allowed' : 'pointer', zIndex: 2,
                  opacity: isUploading ? 0.5 : 1,
                }}
              >
                <Icon name="x" size={10} color="var(--danger)" />
              </button>
            )}
            <input
              ref={photoInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={e => { void handlePhotoChange(e); }}
              style={{ display: 'none' }}
            />
          </div>
          <div>
            <div style={{ fontSize: 14, fontWeight: 800, color: "var(--ink)" }}>
              {isLoading ? '내 정보 불러오는 중' : displayName}
            </div>
            <div style={{ fontSize: 11, color: "var(--ink-3)", marginTop: 2 }}>
              {profile ? badge.label : '프로필 API 응답을 기다리고 있어요'}
            </div>
            <div style={{ fontSize: 10, color: 'var(--muted)', marginTop: 4 }}>
              JPG · PNG · 5MB 이하
            </div>
          </div>
        </div>

        <Field label="이름" hint="튜터가 당신을 부를 이름">
          <Input value={form.nickname} disabled={isLoading || isSaving} onChange={setField('nickname')} />
        </Field>
        <Field label="이메일" hint="소셜 로그인 계정 기준으로 표시됩니다">
          <Input value={form.email} disabled />
        </Field>
        <Field label="학년/단계" hint="온보딩에서 저장한 학습 단계">
          <Input value={form.grade} disabled={isLoading || isSaving} onChange={setField('grade')} />
        </Field>
        <Field label="학습 목표" hint="홈 화면과 튜터 응답에 활용됩니다">
          <Input value={form.learningGoal} disabled={isLoading || isSaving} onChange={setField('learningGoal')} />
        </Field>
        <Field label="선호 말투" hint="예: 존댓말, 격려형, 도전형">
          <Input value={form.preferredTone} disabled={isLoading || isSaving} onChange={setField('preferredTone')} />
        </Field>
        <Field label="관심사" hint="쉼표로 구분해서 저장합니다">
          <Input value={form.interestsText} disabled={isLoading || isSaving} onChange={setField('interestsText')} />
        </Field>
        <Field label="비밀번호">
          <button className="lift" disabled style={{
            padding: "9px 14px", borderRadius: "var(--radius-md)", fontSize: 12, fontWeight: 700,
            background: "var(--bg)", color: "var(--ink-3)", border: "1px solid var(--line)",
            cursor: "not-allowed",
          }}>소셜 로그인 계정</button>
        </Field>

        <div style={{
          display: "flex", gap: 10, marginTop: 18,
          paddingTop: 16, borderTop: "1px solid var(--line-2)",
        }}>
          <button
            onClick={handleSave}
            disabled={isLoading || isSaving}
            className="lift"
            style={{
              padding: "11px 18px", borderRadius: "var(--radius-md)", fontSize: 12.5, fontWeight: 800,
              background: palette.primary,
              color: "var(--paper)",
              opacity: isLoading || isSaving ? .68 : 1,
              cursor: isLoading || isSaving ? "not-allowed" : "pointer",
            }}
          >
            {isSaving ? '저장 중...' : '변경사항 저장'}
          </button>
          <button
            onClick={resetForm}
            disabled={isLoading || isSaving || !profile}
            style={{
              padding: "11px 18px", borderRadius: "var(--radius-md)", fontSize: 12.5, fontWeight: 700,
              color: "var(--ink-3)",
              cursor: isLoading || isSaving || !profile ? "not-allowed" : "pointer",
            }}
          >
            취소
          </button>
        </div>
      </Card>

      <Card title="일일 학습 목표" subtitle="하루에 목표하는 학습 시간을 설정합니다">
        <div style={{
          padding: "18px 22px", borderRadius: 14,
          background: palette.wash, border: `1px solid ${palette.soft}`,
        }}>
          <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
            <span style={{ fontSize: 42, fontWeight: 800, color: palette.primary, letterSpacing: 0 }}>
              {displayGoal}
            </span>
            <span style={{ fontSize: 14, color: palette.primary, fontWeight: 600 }}>분 / 일</span>
            <div style={{ flex: 1 }} />
            <div style={{ fontSize: 12, color: palette.primary, opacity: .8 }}>
              주 {(displayGoal * 7 / 60).toFixed(1)}시간
            </div>
          </div>
          <input
            type="range"
            min="0"
            max="60"
            step="5"
            value={displayGoal}
            onChange={handleGoalChange}
            onMouseUp={() => void commitGoal()}
            onTouchEnd={() => void commitGoal()}
            className="onboarding-slider"
            style={{ width: "100%", marginTop: 10, accentColor: palette.primary2 }}
          />
          <div style={{
            display: "flex", justifyContent: "space-between",
            fontSize: 12, color: palette.primary, opacity: .6, marginTop: 4,
          }}>
            <span>0분</span>
            <span>20분</span>
            <span>40분</span>
            <span>60분</span>
          </div>
        </div>
      </Card>

      <Card title="계정 관리">
        <div style={{ padding: '6px 0', display: 'flex', flexWrap: 'wrap', gap: 10 }}>
          <button
            className="lift"
            onClick={() => void handleLogout()}
            disabled={isLoggingOut}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 8,
              padding: '10px 14px', borderRadius: 'var(--radius-md)',
              background: 'var(--bg)', border: '1px solid var(--line-2)',
              fontSize: 13, fontWeight: 600, color: 'var(--ink-2)',
              opacity: isLoggingOut ? 0.5 : 1,
            }}
          >
            <Icon name="signout" size={15} />
            {isLoggingOut ? '로그아웃 중...' : '로그아웃'}
          </button>
          <button
            className="lift"
            onClick={() => setWithdrawDialogOpen(true)}
            disabled={isWithdrawing}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 8,
              padding: '10px 14px', borderRadius: 'var(--radius-md)',
              background: 'var(--warn-bg)', border: '1px solid var(--warn-border)',
              fontSize: 13, fontWeight: 600, color: 'var(--danger)',
              opacity: isWithdrawing ? 0.5 : 1,
            }}
          >
            <Icon name="trash" size={15} />
            회원 탈퇴
          </button>
        </div>
      </Card>

      <WithdrawConfirmDialog
        open={withdrawDialogOpen}
        submitting={isWithdrawing}
        onConfirm={() => void handleWithdraw()}
        onClose={() => setWithdrawDialogOpen(false)}
      />
    </>
  );
}
