// 랜딩의 PDF 업로드 진입 패널
import type { Palette } from '@/lib';
import type { RouteSetter } from '@/shared/types';
import { MAX_PDF_UPLOAD_SIZE_LABEL } from '@/shared/constants/upload';
import Icon from '@/Icon';

// PdfEntry와 TopicEntry가 같이 쓰는 prop 타입
interface EntryProps {
  palette: Palette;
  setRoute: RouteSetter;
}

function PdfEntry({ palette, setRoute }: EntryProps) {
  return (
    <>
      {/* PDF 드롭존. 누르면 로그인 화면으로 보낸다 */}
      <button onClick={() => setRoute("auth")} className="lift" style={{
        width:"100%", padding:"28px 20px", borderRadius:14,
        background:palette.wash, border:`2px dashed ${palette.soft}`,
        textAlign:"center",
      }}>
        <div style={{ fontSize:28, marginBottom:8 }}><Icon name="pdf" size={28} /></div>
        <div style={{ fontSize:13, fontWeight:800, color:palette.primary }}>
          PDF를 여기에 놓거나 클릭해서 선택
        </div>
        <div style={{ fontSize:12, color:"var(--ink-3)", marginTop:4 }}>
          최대 {MAX_PDF_UPLOAD_SIZE_LABEL} · 교재·강의자료·참고도서 · 목차와 수업 흐름 구성
        </div>
      </button>

      {/* 주제 탭으로 유도하는 보조 안내 */}
      <div style={{ fontSize:12, color:"var(--ink-3)", marginTop:10, textAlign:"center" }}>
        업로드하지 않아도 돼요 — 주제 입력이 더 편하면 오른쪽 탭을 눌러주세요
      </div>
    </>
  );
}

export { PdfEntry };
export type { EntryProps };
