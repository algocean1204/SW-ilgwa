// 튜터 꾸미기 페이지. 상태와 prop만 다루고 화면은 components/tutor가 그린다.
// 프로필, 스타일, 목소리 3열이며 기본 튜터는 수정할 수 없다.
import { useState, useEffect, useCallback } from 'react';
import { useTweaks, PALETTES } from '@/lib';
import type { Palette } from '@/lib';
import type { Tutor, RouteSetter, AppState } from '@/shared/types';
import Icon from '@/Icon';
import PageIntro from '@/components/ui/PageIntro';
import { TutorList } from '@/components/tutor/TutorList';
import { PersonaEditor } from '@/components/tutor/PersonaEditor';
import { VoiceCloneEditor } from '@/components/tutor/VoiceCloneEditor';
import { getMyTutors, createTutor, updateTutor as apiUpdateTutor, deleteTutor as apiDeleteTutor, deleteAllCustomTutors, uploadTutorProfileImage, deleteTutorProfileImage, uploadVoiceSample } from '@/apis';
import { ApiError } from '@/apis/types';
import { toTutor, toCreateRequest, toUpdateRequest } from '@/components/tutor/tutorAdapters';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import { isDemoMode } from '@/lib/demoMode';

interface TutorCustomizeProps {
  setRoute: RouteSetter;
  appState: AppState;
}

// 편집 패널 섹션 헤더
function SectionLabel({ label, palette }: { label: string; palette: Palette }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
      <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '.1em', color: palette.primary, textTransform: 'uppercase' }}>{label}</span>
      <div style={{ flex: 1, height: 1, background: 'var(--border-soft)' }} />
    </div>
  );
}

function TutorCustomize({ setRoute, appState }: TutorCustomizeProps) {
  const t = useTweaks();
  const palette: Palette = PALETTES[t.palette] ?? PALETTES.grass!;
  // 무료 플랜이면 커스텀 튜터 생성과 편집을 막는다
  const isFree = appState.plan === 'free';

  // 튜터 목록. 빈 배열로 시작해 마운트 시 getMyTutors()로 채운다.
  const [tutors, setTutors] = useState<Tutor[]>([]);
  const [activeId, setActiveId] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [proModalOpen, setProModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  // createTutor 응답 전 중복 클릭을 막는 플래그
  const [addingTutor, setAddingTutor] = useState(false);
  const [saved, setSaved] = useState(false);
  // 저장할 때 함께 올릴 사진
  const [pendingPhotoFile, setPendingPhotoFile] = useState<File | null>(null);
  const [pendingPhotoRemove, setPendingPhotoRemove] = useState(false);
  // 저장할 때 함께 올릴 보이스 샘플
  const [pendingVoiceFile, setPendingVoiceFile] = useState<File | null>(null);

  // 튜터 목록을 불러온다
  const loadTutors = useCallback(() => {
    void getMyTutors().then((res) => {
      if (res.data) {
        const list = res.data.tutors.map(toTutor);
        setTutors(list);
        setActiveId((prev) => list.find(t => t.id === prev)?.id ?? list[0]?.id ?? '');
      }
    }).catch(() => {}).finally(() => setLoading(false));
  }, []);

  useEffect(() => { loadTutors(); }, [loadTutors]);

  const active = tutors.find(x => x.id === activeId) ?? tutors[0];

  // 튜터를 바꾸면 대기 중인 사진과 보이스를 비운다
  useEffect(() => {
    void Promise.resolve().then(() => {
      setPendingPhotoFile(null);
      setPendingPhotoRemove(false);
      setPendingVoiceFile(null);
    });
  }, [activeId]);

  const updateActive = useCallback((patch: Partial<Tutor>) => {
    if (!active) return;
    // 기본 튜터(builtIn)는 수정하지 않는다
    if (active.builtIn) return;
    setTutors(list => list.map(x => x.id === active.id ? { ...x, ...patch } : x));
  }, [active]);

  // 사진은 로컬 미리보기만 하고 저장할 때 올린다
  const handlePhotoFile = useCallback((file: File) => {
    if (!active || active.builtIn) return;
    setPendingPhotoFile(file);
    setPendingPhotoRemove(false);
  }, [active]);

  // 사진 제거는 로컬 상태만 비우고 삭제 플래그를 둔다(저장할 때 서버에서 지운다)
  const handlePhotoRemove = useCallback(() => {
    if (!active || active.builtIn) return;
    updateActive({ photo: null });
    setPendingPhotoFile(null);
    setPendingPhotoRemove(true);
  }, [active, updateActive]);

  // 보이스 파일은 저장할 때 올린다
  const handleVoiceFile = useCallback((file: File) => {
    if (!active || active.builtIn) return;
    setPendingVoiceFile(file);
  }, [active]);

  // TUT_003(구독 필요)이면 구독 안내 모달을 띄운다
  const handleCreateError = (err: unknown) => {
    if (err instanceof ApiError && err.errorResponse?.code === 'TUT_003') {
      setProModalOpen(true);
    }
  };

  const addTutor = () => {
    // 5개에 도달했거나 응답을 기다리는 중이면 막는다. 로컬 목록이 아직 4개여도 재클릭을 막는다.
    if (tutors.length >= 5 || addingTutor) return;
    setAddingTutor(true);
    const newT: Tutor = {
      id: '', name: '새 튜터',
      desc: '내가 만든 튜터', tone: 60, pace: 50, depth: 65, socratic: 70,
      useEmoji: false, formal: true, builtIn: false,
    };
    void createTutor(toCreateRequest(newT)).then((res) => {
      if (res.data) {
        setActiveId(res.data.tutorId);
        loadTutors();
      }
    }).catch(handleCreateError).finally(() => setAddingTutor(false));
  };

  const handleDeleteTutor = (id: string) => {
    if (!confirm('이 튜터를 삭제할까요?')) return;
    void apiDeleteTutor(id).then(() => {
      setTutors(list => {
        const next = list.filter(x => x.id !== id);
        if (id === activeId) setActiveId(next[0]?.id ?? '');
        return next;
      });
    }).catch(() => {});
  };

  const duplicateTutor = (id: string) => {
    if (tutors.length >= 5) return;
    const src = tutors.find(x => x.id === id);
    if (!src) return;
    const copied: Tutor = { ...src, id: '', name: src.name + ' (복사)', builtIn: false };
    void createTutor(toCreateRequest(copied)).then((res) => {
      if (res.data) {
        setActiveId(res.data.tutorId);
        loadTutors();
      }
    }).catch(handleCreateError);
  };

  const resetToDefaults = () => {
    if (!confirm('기본 튜터로 리셋할까요? 커스텀 튜터는 모두 삭제됩니다.')) return;
    void deleteAllCustomTutors().then(() => {
      loadTutors();
    }).catch(() => {});
  };

  // 기본 튜터면 편집을 잠근다
  const isBuiltIn = active?.builtIn ?? false;

  return (
    <div style={{ maxWidth: 'var(--page-max-w)', margin: '0 auto', padding: 'var(--page-py) var(--page-px) 48px' }}>
      {/* 페이지 헤더 */}
      <div style={{ marginBottom: 'var(--section-gap)' }}>
        <PageIntro
          eyebrow="MY TUTORS"
          title={
            <>내 튜터를 <span className="script" style={{ fontSize: 32, color: palette.primary }}>내 스타일</span>로</>
          }
          description="튜터를 선택하고 스타일을 조정해 나만의 학습 파트너를 만드세요."
          actions={
            <button onClick={resetToDefaults} className="lift" style={{
              height: 36, padding: '0 12px', borderRadius: 9, background: 'var(--paper)', border: '1px solid var(--line)',
              fontSize: 12, fontWeight: 700, color: 'var(--ink-3)', display: 'flex', alignItems: 'center', gap: 5,
            }}><Icon name="arrowDown" size={11} color="var(--ink-3)" /> 기본값으로</button>
          }
        />
      </div>

      {/* 튜터 선택 목록 */}
      {loading && (
        <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--ink-3)', fontSize: 13 }}>
          튜터 목록을 불러오는 중...
        </div>
      )}
      {!loading && <div id="tutor-choose">
        <TutorList
          tutors={tutors}
          activeId={activeId}
          palette={palette}
          onSelect={setActiveId}
          onAdd={addTutor}
          onDelete={handleDeleteTutor}
          onDuplicate={duplicateTutor}
          isFree={isFree}
          maxReached={isDemoMode || tutors.length >= 5 || addingTutor}
        />
      </div>}

      {/* 기본 튜터를 고르면 안내 배너를 보인다 */}
      {isBuiltIn && (
        <div style={{
          padding: '10px 16px', borderRadius: 10, marginBottom: 16,
          background: 'var(--honey-pale)', border: '1px solid var(--honey)',
          fontSize: 12, fontWeight: 600, color: 'var(--honey-ink)',
          display: 'flex', alignItems: 'center', gap: 8,
        }}>
          <Icon name="lock" size={13} color="var(--honey-ink)" />
          기본 튜터는 수정할 수 없어요. 커스텀 튜터를 추가해 자유롭게 편집해보세요.
        </div>
      )}

      {/* 프로필과 스타일은 한 위젯에, 목소리는 오른쪽 별도 위젯에 둔다 */}
      {active && (
        <div id="persona-edit" style={{
          display: 'grid',
          gap: 16,
          alignItems: 'flex-start',
        }}>
          {/* 프로필, 스타일 위젯 */}
          <div style={{
            padding: '16px 18px', borderRadius: 14,
            background: 'var(--surface-card)', border: '1px solid var(--border-soft)',
          }}>
            <div className="tutor-profile-style-grid">
              <div>
                <SectionLabel label="프로필" palette={palette} />
                <PersonaEditor
                  tutor={active}
                  palette={palette}
                  onUpdate={updateActive}
                  locked={isBuiltIn || (isFree && !active?.builtIn)}
                  profileOnly
                  onPhotoFile={handlePhotoFile}
                  onPhotoRemove={handlePhotoRemove}
                />
              </div>
              <div>
                <SectionLabel label="스타일 설정" palette={palette} />
                <PersonaEditor
                  tutor={active}
                  palette={palette}
                  onUpdate={updateActive}
                  locked={isBuiltIn || (isFree && !active?.builtIn)}
                  styleOnly
                />
              </div>
            </div>
          </div>

          {/* 목소리 */}
          <div style={{
            padding: '16px 18px', borderRadius: 14,
            background: 'var(--surface-card)', border: '1px solid var(--border-soft)',
          }}>
            <SectionLabel label="목소리" palette={palette} />
            <VoiceCloneEditor tutor={active} palette={palette} onUpdate={updateActive} onFileSelect={handleVoiceFile} locked={isBuiltIn || (isFree && !active?.builtIn)} />
          </div>
        </div>
      )}

      {/* 저장 버튼. 기본 튜터와 무료 플랜 커스텀 튜터에는 없다 */}
      {active && !isBuiltIn && !(isFree && !active.builtIn) && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
          <button
            disabled={saving || saved || !active.id}
            onClick={() => {
              // id가 없으면 아직 생성 응답 전이라 저장하지 않는다
              if (!active || active.builtIn || saving || !active.id) return;
              setSaving(true);
              // 사진, 보이스를 차례로 올린 뒤 튜터 메타데이터를 저장한다
              const mediaWork = async () => {
                if (pendingPhotoRemove && active.id) {
                  await deleteTutorProfileImage(active.id).catch(() => {});
                  setPendingPhotoRemove(false);
                }
                if (pendingPhotoFile && active.id) {
                  const fd = new FormData();
                  fd.append('file', pendingPhotoFile);
                  const res = await uploadTutorProfileImage(active.id, fd).catch(() => null);
                  if (res?.data) updateActive({ photo: res.data.profileImageUrl });
                  setPendingPhotoFile(null);
                }
                if (pendingVoiceFile && active.id) {
                  const fd = new FormData();
                  fd.append('file', pendingVoiceFile);
                  const res = await uploadVoiceSample(active.id, fd).catch(() => null);
                  // 업로드가 성공하면 서버 URL로 voiceSample을 바꾸고 저장 완료로 표시한다
                  if (res?.data) updateActive({ voiceSample: res.data.voiceSampleUrl, voiceCloneSaved: true });
                  setPendingVoiceFile(null);
                }
              };
              void mediaWork()
                .then(() => apiUpdateTutor(active.id, toUpdateRequest(active)))
                .then(() => { setSaved(true); setTimeout(() => setSaved(false), 1500); })
                .catch(() => {})
                .finally(() => setSaving(false));
            }}
            className="lift"
            style={{
              padding: '10px 20px', borderRadius: 10,
              background: saved ? 'var(--grass-primary)' : palette.primary,
              color: 'var(--paper)',
              fontSize: 13, fontWeight: 700,
              boxShadow: 'var(--shadow-sm)',
              display: 'flex', alignItems: 'center', gap: 6,
              opacity: saving ? 0.7 : 1,
              transition: 'background .2s, opacity .2s',
            }}
          >
            {saving ? '저장 중...' : saved ? '저장됨!' : '저장하기'}
            {!saving && !saved && <Icon name="check" size={13} color="var(--paper)" />}
          </button>
        </div>
      )}

      {/* Pro 구독 안내 모달 */}
      <ConfirmDialog
        open={proModalOpen}
        onClose={() => setProModalOpen(false)}
        icon={{ name: 'lock', color: palette.primary, bgColor: palette.wash }}
        title="Pro 구독이 필요해요"
        description={<>커스텀 튜터를 만들고 편집하려면<br />Pro 이상 구독이 필요합니다.</>}
        confirmLabel="구독 플랜 보기"
        onConfirm={() => { setProModalOpen(false); setRoute('pricing'); }}
        cancelLabel="나중에 할게요"
        confirmColorOverride={palette.primary}
      >
        {/* Pro 혜택 목록 */}
        <div style={{
          padding: '12px 16px', borderRadius: 10,
          background: 'var(--surface-subtle)', border: '1px solid var(--border-soft)',
          display: 'flex', flexDirection: 'column', gap: 8,
        }}>
          {['나만의 커스텀 튜터 최대 5명', '말투·질문 스타일 자유 조정', '보이스 클론으로 목소리 설정'].map((text) => (
            <div key={text} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Icon name="check" size={14} color={palette.primary} />
              <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-primary)' }}>{text}</span>
            </div>
          ))}
        </div>
      </ConfirmDialog>
    </div>
  );
}

export default TutorCustomize;
