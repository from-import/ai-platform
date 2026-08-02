import { useSyncExternalStore } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import {
  clearLatestApiError,
  getLatestApiError,
  subscribeToApiErrors,
} from "./api/client";
import { AppShell } from "./components/AppShell";
import { useModels } from "./hooks/useModels";
import { AnalyticsPage } from "./pages/AnalyticsPage";
import { PlaygroundPage } from "./pages/PlaygroundPage";
import { RequestLogsPage } from "./pages/RequestLogsPage";

export default function App() {
  const { models, loading, error } = useModels();
  const apiError = useSyncExternalStore(
    subscribeToApiErrors,
    getLatestApiError,
    getLatestApiError,
  );
  const status = loading ? "Loading models..." : error ? "Models unavailable" : "Ready";
  const statusState = loading ? "busy" : error ? "error" : undefined;

  return (
    <AppShell
      status={status}
      statusState={statusState}
      apiError={apiError}
      onDismissApiError={clearLatestApiError}
    >
      <Routes>
        <Route path="/" element={<Navigate to="/playground" replace />} />
        <Route
          path="/playground"
          element={<PlaygroundPage models={models} modelsLoading={loading} modelsError={error} />}
        />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route path="/requests" element={<RequestLogsPage models={models} />} />
        <Route path="*" element={<Navigate to="/playground" replace />} />
      </Routes>
    </AppShell>
  );
}
