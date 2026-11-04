// mockExam 라우트. aiMockExam과 같은 MockExamView를 렌더한다.
import MockExamView from '@/components/mock-exam/MockExamView';
import type { ViewProps } from '@/routes/config';

function MockExam({ setRoute, appState }: ViewProps) {
  return <MockExamView setRoute={setRoute} appState={appState} />;
}

export default MockExam;
