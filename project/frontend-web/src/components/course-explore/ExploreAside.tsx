// 우측 안내 카드. 4단계 생성 과정, 팁, 개인정보 안내를 보여 준다.
import type { Palette } from '@/lib';
import Icon from '@/Icon';
import InspectorPanel from '@/components/ui/InspectorPanel';

// 4단계 커리큘럼 생성 안내 데이터
const STEPS = [
  { n: '1', t: '커리큘럼 초안 생성', d: '주제/PDF 분석 후 챕터·슬라이드 구조 설계' },
  { n: '2', t: '직접 확인하고 조정', d: '제안된 목차를 보고 추가·삭제·순서 변경' },
  { n: '3', t: '음성 강의 생성', d: '확정하면 슬라이드+TTS 자동 제작 (롤포워드 병렬)' },
  { n: '4', t: '학습 시작', d: '음성과 함께 공부하고, 궁금한 건 바로 질문' },
];

interface ExploreAsideProps {
  palette: Palette;
}

export function ExploreAside({ palette }: ExploreAsideProps) {
  return (
    <div style={{
      position: 'sticky', top: 20, alignSelf: 'flex-start',
      display: 'flex', flexDirection: 'column', gap: 14,
    }}>
      {/* 커리큘럼 생성 단계 안내 */}
      <InspectorPanel title="이렇게 만들어져요">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {STEPS.map(s => (
            <div key={s.n} style={{ display: 'flex', gap: 12 }}>
              {/* 단계 번호 원 */}
              <div style={{
                width: 24, height: 24, borderRadius: '50%', background: 'var(--paper)',
                border: `1.5px solid ${palette.primary}`, color: palette.primary,
                fontSize: 12, fontWeight: 800, display: 'grid', placeItems: 'center', flexShrink: 0,
              }}>
                {s.n}
              </div>
              <div>
                <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink)' }}>{s.t}</div>
                <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2, lineHeight: 1.5 }}>{s.d}</div>
              </div>
            </div>
          ))}
        </div>
      </InspectorPanel>

      {/* 팁 카드 */}
      <InspectorPanel title="팁">
        <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.7 }}>
          <b style={{ color: 'var(--ink)' }}>주제 입력이 처음</b>이라면 이렇게 써보세요 —{' '}
          <br />
          "<i>누구</i>를 위한 <i>무엇</i>을 <i>어떤 목표</i>로". 예) 고1을 위한 영어 독해, 모의고사 3등급 목표.
        </div>
      </InspectorPanel>

      {/* 개인정보 보안 안내 */}
      <InspectorPanel>
        <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
          <span style={{ fontSize: 16 }}><Icon name="lock" size={16} color="var(--ink-3)" /></span>
          <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.55 }}>
            업로드한 PDF와 학습 기록은 <b>내 계정 전용 SHM</b>에 암호화 저장됩니다. 제3자에게 제공되지 않아요.
          </div>
        </div>
      </InspectorPanel>
    </div>
  );
}
