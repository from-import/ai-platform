import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { login, register } from "../api/client";
import type { LoginResponse } from "../api/types";
import { errorMessage } from "../utils/format";

interface LoginPageProps {
  onAuthenticated: (session: LoginResponse) => void;
}

type AuthMode = "login" | "register";

export function LoginPage({ onAuthenticated }: LoginPageProps) {
  const navigate = useNavigate();
  const [mode, setMode] = useState<AuthMode>("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      if (mode === "register") {
        await register({ username, password, displayName });
      }
      const session = await login({ username, password });
      onAuthenticated(session);
      navigate("/playground", { replace: true });
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  function switchMode(nextMode: AuthMode): void {
    setMode(nextMode);
    setError(null);
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <div className="login-brand">
          <div className="app-icon">AI</div>
          <div>
            <strong>AI Platform</strong>
            <span>Secure gateway access</span>
          </div>
        </div>

        <div className="auth-mode-switch" role="tablist" aria-label="Authentication mode">
          <button
            className={mode === "login" ? "active" : ""}
            type="button"
            role="tab"
            aria-selected={mode === "login"}
            onClick={() => switchMode("login")}
          >
            Sign in
          </button>
          <button
            className={mode === "register" ? "active" : ""}
            type="button"
            role="tab"
            aria-selected={mode === "register"}
            onClick={() => switchMode("register")}
          >
            Create account
          </button>
        </div>

        <div className="login-heading">
          <h1>{mode === "login" ? "Welcome back" : "Create your account"}</h1>
          <p>
            {mode === "login"
              ? "Sign in to use configured models and view request analytics."
              : "Registration is available in local development by default."}
          </p>
        </div>

        <form className="login-form" onSubmit={(event) => void handleSubmit(event)}>
          {mode === "register" ? (
            <label>
              Display name
              <input
                type="text"
                value={displayName}
                maxLength={64}
                autoComplete="name"
                onChange={(event) => setDisplayName(event.target.value)}
              />
            </label>
          ) : null}

          <label>
            Username
            <input
              type="text"
              value={username}
              minLength={3}
              maxLength={64}
              pattern="[A-Za-z0-9._-]+"
              autoComplete="username"
              required
              onChange={(event) => setUsername(event.target.value)}
            />
          </label>

          <label>
            Password
            <input
              type="password"
              value={password}
              minLength={mode === "register" ? 8 : undefined}
              maxLength={72}
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              required
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>

          {error ? <div className="login-error" role="alert">{error}</div> : null}

          <button className="login-submit" type="submit" disabled={submitting}>
            {submitting
              ? "Please wait..."
              : mode === "login"
                ? "Sign in"
                : "Create account and sign in"}
          </button>
        </form>
      </section>
    </main>
  );
}
