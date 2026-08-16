import { useEffect, useState, useSyncExternalStore } from "react";
import { Navigate, Route, Routes, useNavigate } from "react-router-dom";
import {
  clearLatestApiError,
  getCurrentUser,
  getLatestApiError,
  logout,
  subscribeToApiErrors,
} from "./api/client";
import {
  clearAuthSession,
  getAuthSession,
  saveAuthSession,
  subscribeToAuthSession,
  updateAuthenticatedUser,
} from "./auth/session";
import { AppShell } from "./components/AppShell";
import { useModels } from "./hooks/useModels";
import { AnalyticsPage } from "./pages/AnalyticsPage";
import { LoginPage } from "./pages/LoginPage";
import { PlaygroundPage } from "./pages/PlaygroundPage";
import { RequestLogsPage } from "./pages/RequestLogsPage";

export default function App() {
  const session = useSyncExternalStore(
    subscribeToAuthSession,
    getAuthSession,
    getAuthSession,
  );

  if (!session) {
    return (
      <Routes>
        <Route
          path="/login"
          element={<LoginPage onAuthenticated={saveAuthSession} />}
        />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return <AuthenticatedApp />;
}

function AuthenticatedApp() {
  const navigate = useNavigate();
  const session = getAuthSession()!;
  const { models, loading, error } = useModels();
  const [conversationRevision, setConversationRevision] = useState(0);
  const apiError = useSyncExternalStore(
    subscribeToApiErrors,
    getLatestApiError,
    getLatestApiError,
  );
  const status = loading ? "Loading models..." : error ? "Models unavailable" : "Ready";
  const statusState = loading ? "busy" : error ? "error" : undefined;

  useEffect(() => {
    const controller = new AbortController();
    void getCurrentUser(controller.signal)
      .then(updateAuthenticatedUser)
      .catch(() => undefined);
    return () => controller.abort();
  }, [session.token]);

  async function handleLogout(): Promise<void> {
    try {
      await logout();
    } finally {
      clearAuthSession();
      clearLatestApiError();
      navigate("/login", { replace: true });
    }
  }

  return (
    <AppShell
      status={status}
      statusState={statusState}
      apiError={apiError}
      onDismissApiError={clearLatestApiError}
      currentUser={session.user}
      onLogout={() => void handleLogout()}
      conversationRevision={conversationRevision}
    >
      <Routes>
        <Route path="/" element={<Navigate to="/playground" replace />} />
        <Route
          path="/playground"
          element={(
            <PlaygroundPage
              models={models}
              modelsLoading={loading}
              modelsError={error}
              onConversationUpdated={() => setConversationRevision((value) => value + 1)}
            />
          )}
        />
        <Route
          path="/playground/:conversationId"
          element={(
            <PlaygroundPage
              models={models}
              modelsLoading={loading}
              modelsError={error}
              onConversationUpdated={() => setConversationRevision((value) => value + 1)}
            />
          )}
        />
        <Route
          path="/projects/:projectId"
          element={(
            <PlaygroundPage
              models={models}
              modelsLoading={loading}
              modelsError={error}
              onConversationUpdated={() => setConversationRevision((value) => value + 1)}
            />
          )}
        />
        <Route
          path="/projects/:projectId/conversations/:conversationId"
          element={(
            <PlaygroundPage
              models={models}
              modelsLoading={loading}
              modelsError={error}
              onConversationUpdated={() => setConversationRevision((value) => value + 1)}
            />
          )}
        />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route path="/requests" element={<RequestLogsPage models={models} />} />
        <Route path="/login" element={<Navigate to="/playground" replace />} />
        <Route path="*" element={<Navigate to="/playground" replace />} />
      </Routes>
    </AppShell>
  );
}
