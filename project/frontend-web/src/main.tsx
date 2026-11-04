// 앱 진입점. 한 컴포넌트의 throw가 전체 화이트스크린이 되지 않게 전역 ErrorBoundary로 감싼다.
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App";
import ErrorBoundary from "./components/ErrorBoundary";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ErrorBoundary scope="global">
      <App />
    </ErrorBoundary>
  </StrictMode>,
);
