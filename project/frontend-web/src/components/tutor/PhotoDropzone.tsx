// 아바타 사진 업로드 드롭존.
// 드래그앤드롭이나 파일 선택으로 받은 이미지를 Data URL로 바꿔 상위에 넘긴다.
import React, { useRef, useState, useCallback } from 'react';
import type { Palette } from '@/lib';
import { TutorAvatar } from './TutorAvatar';
import Icon from '@/Icon';

interface PhotoDropzoneProps {
  photo?: string | null;
  palette: Palette;
  onUpload: (dataUrl: string) => void;
  onRemove: () => void;
  /** 저장된 튜터(ID 있음)에서 File 객체가 필요할 때 쓴다. 업로드는 부모가 한다 */
  onFileSelect?: (file: File) => void;
}

// 파일을 검사한 뒤 Data URL로 바꾼다.
// onFileSelect가 있으면 파일 객체도 함께 부모로 넘긴다.
function useFileHandler(
  onUpload: (dataUrl: string) => void,
  onFileSelect?: (file: File) => void,
) {
  const handleFile = useCallback((file: File | undefined) => {
    if (!file) return;
    if (!file.type.startsWith('image/')) { alert('이미지 파일만 올릴 수 있어요'); return; }
    if (file.size > 5 * 1024 * 1024) { alert('5MB 이하 이미지만 올릴 수 있어요'); return; }
    // 로컬 미리보기를 바로 보여 주려고 Data URL 변환은 항상 한다
    const reader = new FileReader();
    reader.onload = (e) => {
      const result = e.target?.result;
      if (typeof result === 'string') onUpload(result);
    };
    reader.readAsDataURL(file);
    // onFileSelect가 있으면 원본 파일을 부모에게 넘긴다. 업로드는 부모가 한다.
    if (onFileSelect) onFileSelect(file);
  }, [onUpload, onFileSelect]);
  return handleFile;
}

export function PhotoDropzone({ photo, palette, onUpload, onRemove, onFileSelect }: PhotoDropzoneProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [dragOver, setDragOver] = useState(false);
  // 전역 CSS 선택자를 쓰지 않으려고 오버레이 표시를 React 상태로 관리한다
  const [hovered, setHovered] = useState(false);
  const handleFile = useFileHandler(onUpload, onFileSelect);

  const onDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    handleFile(e.dataTransfer.files?.[0]);
  };

  // 키보드 접근성: Enter/Space로 파일 선택을 연다
  const onKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      inputRef.current?.click();
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, flexShrink: 0 }}>
      <div
        role="button"
        tabIndex={0}
        aria-label="사진 올리기 드롭존"
        onDragOver={e => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        onKeyDown={onKeyDown}
        style={{
          position: 'relative', width: 108, height: 108, borderRadius: '50%', cursor: 'pointer',
          border: dragOver ? `2.5px dashed ${palette.primary}` : photo ? '1px solid var(--line)' : '1.5px dashed var(--line-2)',
          background: photo ? 'transparent' : 'var(--paper)',
          overflow: 'visible', display: 'grid', placeItems: 'center', transition: 'all .2s',
        }}
      >
        {photo ? (
          <div style={{
            width: '100%', height: '100%', borderRadius: '50%',
            backgroundImage: `url(${photo})`, backgroundSize: 'cover', backgroundPosition: 'center',
          }} />
        ) : (
          <TutorAvatar size={104} />
        )}
        {/* 호버 오버레이. opacity를 React 상태로 제어한다 */}
        <div style={{
          position: 'absolute', inset: 0, borderRadius: '50%',
          background: 'var(--overlay-dim)', color: 'var(--paper)',
          display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 4,
          opacity: hovered ? 1 : 0, transition: 'opacity .15s',
          pointerEvents: 'none',
        }}>
          <Icon name="upload" size={18} color="var(--paper)" />
          <span style={{ fontSize: 12, fontWeight: 700 }}>{photo ? '사진 바꾸기' : '사진 올리기'}</span>
        </div>
        {photo && (
          <button
            onClick={e => { e.stopPropagation(); onRemove(); }}
            title="사진 제거"
            style={{
              position: 'absolute', top: -4, right: -4, width: 26, height: 26, borderRadius: '50%',
              background: 'var(--paper)', border: '1px solid var(--line)', boxShadow: 'var(--shadow-sm)',
              display: 'grid', placeItems: 'center', color: 'var(--danger)', cursor: 'pointer', zIndex: 2,
            }}
          >
            <Icon name="x" size={12} color="var(--danger)" />
          </button>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        onChange={e => handleFile(e.target.files?.[0])}
        style={{ display: 'none' }}
      />
      {/* 카메라 아이콘 버튼 */}
      <button onClick={() => inputRef.current?.click()} style={{
        /* photo가 있으면 var(--paper) 카드 버튼, 없으면 palette.primary 주 액션 버튼(#fff 텍스트) */
        padding: '6px 12px', borderRadius: 8, background: photo ? 'var(--paper)' : palette.primary,
        border: photo ? '1px solid var(--line)' : 'none',
        color: photo ? 'var(--ink-2)' : 'var(--paper)',
        fontSize: 12, fontWeight: 700, cursor: 'pointer', letterSpacing: '.02em',
        display: 'flex', alignItems: 'center', gap: 4,
      }}>
        <Icon name="camera" size={12} color={photo ? 'var(--ink-2)' : 'var(--paper)'}/>
        {photo ? '다른 사진' : '사진 선택'}
      </button>
      <div style={{ fontSize: 12, color: 'var(--muted)', textAlign: 'center', lineHeight: 1.5 }}>
        JPG · PNG · 5MB
      </div>
    </div>
  );
}
