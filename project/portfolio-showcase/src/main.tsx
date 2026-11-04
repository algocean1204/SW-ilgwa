// 앱 진입점. VITE_MOCK=1이면 MSW 워커를 먼저 띄우고 App을 마운트한다.
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';
import ErrorBoundary from './components/ErrorBoundary';

async function enableMocking() {
  if (import.meta.env.VITE_MOCK !== '1') return;
  const { worker } = await import('./mocks/browser');
  await worker.start({
    quiet: true,
    onUnhandledRequest: (req) => {
      const url = new URL(req.url);
      if (url.pathname.startsWith('/api/')) {
        console.warn('[MSW] Unhandled:', req.method, url.pathname);
      }
    },
    serviceWorker: { url: import.meta.env.BASE_URL + 'mockServiceWorker.js' },
  });
}

void enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <ErrorBoundary scope="global">
        <App />
      </ErrorBoundary>
    </StrictMode>,
  );
});