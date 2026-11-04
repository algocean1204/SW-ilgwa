// PDF 업로드 패널. 드래그 대기 상태와 업로드 완료(파일 정보) 상태로 나뉜다.
import { useRef, useState } from 'react';
import Icon from '@/Icon';
import type { Palette } from '@/lib';
import { MAX_PDF_UPLOAD_SIZE_BYTES, MAX_PDF_UPLOAD_SIZE_LABEL } from '@/shared/constants/upload';

interface PdfUploadPanelProps {
  file: File | null;
  palette: Palette;
  disabled?: boolean;
  onFileSelect: (file: File) => void;
  onFileClear: () => void;
}

function formatFileSize(size: number): string {
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(size / 1024))} KB`;
}

function isPdf(file: File): boolean {
  return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
}

export function PdfUploadPanel({ file, palette, disabled = false, onFileSelect, onFileClear }: PdfUploadPanelProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  const handleFile = (nextFile: File | undefined) => {
    if (disabled) return;
    if (!nextFile) return;
    if (!isPdf(nextFile)) {
      setError('PDF 파일만 업로드할 수 있어요.');
      return;
    }
    if (nextFile.size > MAX_PDF_UPLOAD_SIZE_BYTES) {
      setError(`${MAX_PDF_UPLOAD_SIZE_LABEL} 이하의 PDF를 선택해 주세요.`);
      return;
    }
    setError(null);
    onFileSelect(nextFile);
  };

  return (
    /* 표면 배경이라 var(--paper) 토큰을 쓴다 */
    <div style={{
      padding: '32px 24px', borderRadius: 16, background: 'var(--paper)',
      border: '1px solid var(--line)',
    }}>
      {!file ? (
        /* 업로드 대기 */
        <button
          type="button"
          disabled={disabled}
          onClick={() => inputRef.current?.click()}
          onDragOver={e => e.preventDefault()}
          onDrop={e => {
            e.preventDefault();
            handleFile(e.dataTransfer.files?.[0]);
          }}
          className="lift"
          style={{
            width: '100%', padding: '36px 20px', borderRadius: 14,
            background: palette.wash, border: `2px dashed ${palette.soft}`,
            textAlign: 'center',
            opacity: disabled ? 0.6 : 1,
            cursor: disabled ? 'not-allowed' : 'pointer',
          }}
        >
          <div style={{ marginBottom: 10, display: 'flex', justifyContent: 'center' }}>
            <Icon name="upload" size={32} color={palette.primary} strokeWidth={1.8} />
          </div>
          <div style={{ fontSize: 14, fontWeight: 800, color: palette.primary }}>
            PDF를 여기에 놓거나 클릭해서 업로드
          </div>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 6 }}>
            최대 {MAX_PDF_UPLOAD_SIZE_LABEL} · 한글 교재 OCR 지원 · 수식 자동 LaTeX 변환
          </div>
        </button>
      ) : (
        /* 업로드 완료: 파일 정보와 변경 버튼 */
        <div style={{
          padding: '16px 18px', borderRadius: 12, background: palette.wash,
          border: `1px solid ${palette.soft}`,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {/* PDF 아이콘 뱃지 */}
            <div style={{
              width: 40, height: 48, borderRadius: 6, background: 'var(--paper)',
              border: `1px solid ${palette.soft}`, display: 'grid', placeItems: 'center',
              fontSize: 14, fontWeight: 800, color: palette.primary,
            }}>
              PDF
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink)' }}>{file.name}</div>
              <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>
                {formatFileSize(file.size)} · 업로드 대기
              </div>
            </div>
            <button type="button" disabled={disabled} onClick={() => { setError(null); onFileClear(); if (inputRef.current) inputRef.current.value = ''; }} style={{ fontSize: 12, color: 'var(--ink-3)' }}>
              변경
            </button>
          </div>
          {/* 문서 분석 중 표시 */}
          <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: palette.primary }}>
            <span className="pulse" style={{ width: 6, height: 6, borderRadius: 3, background: palette.primary }} />
            과외 생성 시 PDF를 업로드합니다
          </div>
        </div>
      )}
      <input
        ref={inputRef}
        type="file"
        accept="application/pdf,.pdf"
        disabled={disabled}
        onChange={e => {
          handleFile(e.target.files?.[0]);
          e.currentTarget.value = '';
        }}
        style={{ display: 'none' }}
      />
      {error && (
        <div role="alert" style={{ marginTop: 10, fontSize: 12, fontWeight: 700, color: 'var(--danger)' }}>
          {error}
        </div>
      )}
    </div>
  );
}
