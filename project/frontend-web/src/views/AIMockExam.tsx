// 실전 모의고사 페이지. 모든 내비게이션이 이 라우트를 가리키고, 응시 흐름은 MockExamView에 있다.
// 상태 전환(개요→생성→응시→결과), API, OMR, 타이머, 결과지는 components/mock-exam 아래에 있다.
import MockExamView from '@/components/mock-exam/MockExamView';
import type { ViewProps } from '@/routes/config';

function AIMockExam({ setRoute, appState }: ViewProps) {
  return <MockExamView setRoute={setRoute} appState={appState} />;
}

export default AIMockExam;
