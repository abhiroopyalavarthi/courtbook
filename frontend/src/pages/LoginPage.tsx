import { Shuttle } from "../components/Shuttle.tsx";
import { useState, type FormEvent } from "react";
import { useSession } from "../session.tsx";
import { useAction } from "../useAsync.ts";

export function LoginPage() {
  const { login, register } = useSession();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const action = useAction();

  async function submit(e: FormEvent) {
    e.preventDefault();
    await action.run(() => (mode === "login" ? login(email, password) : register(email, name, password)));
  }

  return (
    <div className="login">
      <div className="login-card">
        <h1>
          <span className="brand-mark">
            <Shuttle />
          </span>{" "}
          CourtBook
        </h1>
        <p className="muted">Court bookings and tournaments for the SBU Badminton Club.</p>

        <form onSubmit={submit} className="stack">
          <label>
            Email
            <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          {mode === "register" && (
            <label>
              Name
              <input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
            </label>
          )}
          <label>
            Password
            <input
              type="password"
              required
              minLength={mode === "register" ? 8 : undefined}
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          {action.error && <p className="error">{action.error}</p>}
          <button className="primary" disabled={action.busy}>
            {mode === "login" ? "Log in" : "Create account"}
          </button>
        </form>

        <p className="muted small">
          {mode === "login" ? "New to the club? " : "Already have an account? "}
          <button
            className="link"
            onClick={() => {
              setMode(mode === "login" ? "register" : "login");
              action.clearError();
            }}
          >
            {mode === "login" ? "Create an account" : "Log in"}
          </button>
        </p>
      </div>
    </div>
  );
}
