// 커리큘럼 초안 페이지 상단 경로. sourceType에 따라 PDF/주제 입력 문구를 고른다.
import type { RouteSetter } from '@/shared/types';

interface BreadcrumbProps {
  setRoute: RouteSetter;
  /** 과외 소스 유형: 'pdf' | 'topic' | '' */
  sourceType?: string;
}

export function Breadcrumb({ setRoute, sourceType }: BreadcrumbProps) {
  // pdf가 아니면(topic 또는 알 수 없는 값) 주제 입력 문구를 쓴다
  const uploadLabel = sourceType === 'pdf' ? 'PDF 업로드 완료' : '주제 입력 완료';

  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 8,
      fontSize: 12, color: "var(--ink-3)", marginBottom: 14,
    }}>
      <button
        onClick={() => setRoute("explore")}
        style={{ color: "var(--ink-3)", fontWeight: 600 }}
      >
        새 과외
      </button>
      <span style={{ opacity: .4 }}>/</span>
      <span>{uploadLabel}</span>
      <span style={{ opacity: .4 }}>/</span>
      <span style={{ color: "var(--ink-2)", fontWeight: 600 }}>커리큘럼 확인</span>
    </div>
  );
}
