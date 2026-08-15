import type { LoginResponse, UserInfo } from "../api/types";

const STORAGE_KEY = "ai-platform.auth-session";

let currentSession = readStoredSession();
const listeners = new Set<() => void>();

export function subscribeToAuthSession(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getAuthSession(): LoginResponse | null {
  return currentSession;
}

export function getAuthToken(): string | null {
  return currentSession?.token ?? null;
}

export function saveAuthSession(session: LoginResponse): void {
  currentSession = session;
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  notify();
}

export function updateAuthenticatedUser(user: UserInfo): void {
  if (!currentSession) {
    return;
  }
  currentSession = { ...currentSession, user };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(currentSession));
  notify();
}

export function clearAuthSession(): void {
  if (!currentSession && localStorage.getItem(STORAGE_KEY) === null) {
    return;
  }
  currentSession = null;
  localStorage.removeItem(STORAGE_KEY);
  notify();
}

function readStoredSession(): LoginResponse | null {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (!stored) {
    return null;
  }
  try {
    const session = JSON.parse(stored) as Partial<LoginResponse>;
    if (
      !session.token
      || !session.expiresAt
      || !session.user?.username
      || (session.user.role !== "USER" && session.user.role !== "ADMIN")
    ) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return session as LoginResponse;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

function notify(): void {
  listeners.forEach((listener) => listener());
}
