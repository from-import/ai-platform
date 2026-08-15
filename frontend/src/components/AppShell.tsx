import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import type { ApiErrorNotice } from "../api/client";
import type { UserInfo } from "../api/types";
import { ConversationHistory } from "./ConversationHistory";

interface AppShellProps {
  children: ReactNode;
  status: string;
  statusState?: "busy" | "error";
  apiError: ApiErrorNotice | null;
  onDismissApiError: () => void;
  currentUser: UserInfo;
  onLogout: () => void;
  conversationRevision: number;
}

function PlaygroundIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M8 9h8M8 13h5" />
      <path d="M5 19l1.4-3.1A7 7 0 1 1 19 12a7 7 0 0 1-7 7H5Z" />
    </svg>
  );
}

function AnalyticsIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 19V9M12 19V5M19 19v-7" />
    </svg>
  );
}

function RequestLogsIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 6h10M7 12h10M7 18h10" />
      <path d="M4 6h.01M4 12h.01M4 18h.01" />
    </svg>
  );
}

export function AppShell({
  children,
  status,
  statusState,
  apiError,
  onDismissApiError,
  currentUser,
  onLogout,
  conversationRevision,
}: AppShellProps) {
  const navClassName = ({ isActive }: { isActive: boolean }) =>
    `nav-link${isActive ? " active" : ""}`;
  const isAdministrator = currentUser.role === "ADMIN";
  const roleLabel = isAdministrator ? "Administrator" : "Standard user";

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-header">
          <div className="app-icon">AI</div>
          <strong>AI Platform</strong>
        </div>

        <nav className="navigation" aria-label="Primary navigation">
          <NavLink className={navClassName} to="/playground">
            <PlaygroundIcon />
            <span>Playground</span>
          </NavLink>
          <NavLink className={navClassName} to="/analytics">
            <AnalyticsIcon />
            <span>Analytics</span>
          </NavLink>
          <NavLink className={navClassName} to="/requests">
            <RequestLogsIcon />
            <span>Request Logs</span>
          </NavLink>
        </nav>

        <ConversationHistory revision={conversationRevision} />

        <div className="sidebar-footer">
          <div className="account-panel">
            <span
              className={`account-avatar${isAdministrator ? " administrator" : ""}`}
              title={`${currentUser.displayName || currentUser.username} · ${roleLabel}`}
            >
              {(currentUser.displayName || currentUser.username).slice(0, 1).toUpperCase()}
            </span>
            <span className="account-copy">
              <strong>{currentUser.displayName || currentUser.username}</strong>
              <small>
                @{currentUser.username}
                <span className={`account-role${isAdministrator ? " administrator" : ""}`}>
                  {roleLabel}
                </span>
              </small>
            </span>
            <span
              className={`account-role account-role-compact${isAdministrator ? " administrator" : ""}`}
            >
              {isAdministrator ? "Admin" : "User"}
            </span>
            <button className="logout-button" type="button" onClick={onLogout}>
              Log out
            </button>
          </div>
          <div className={`status${statusState ? ` ${statusState}` : ""}`}>{status}</div>
        </div>
      </aside>

      <main className="app-main">
        {apiError ? (
          <div className="api-error-notice" role="alert" aria-live="assertive">
            <div>
              <strong>{apiError.code}</strong>
              <span>{apiError.message}</span>
            </div>
            <button type="button" aria-label="Dismiss error" onClick={onDismissApiError}>
              ×
            </button>
          </div>
        ) : null}
        {children}
      </main>
    </div>
  );
}
